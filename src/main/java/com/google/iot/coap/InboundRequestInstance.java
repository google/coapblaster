/*
 * Copyright (C) 2018 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.iot.coap;

import com.google.common.util.concurrent.ListeningScheduledExecutorService;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

class InboundRequestInstance implements InboundRequest, OutboundMessageHandler {
    private static final boolean DEBUG = false;
    private static final Logger LOGGER =
            Logger.getLogger(InboundRequestInstance.class.getCanonicalName());

    private final LocalEndpoint mLocalEndpoint;
    private final Message mMessage;
    private volatile boolean mIsDone = false;
    private volatile boolean mDidSendAck = false;
    private volatile boolean mResponseIsPending = false;
    private volatile boolean mDidDrop = false;
    private final Thread mScopeThread;
    private List<Option> mSortedOptions;
    private int mOptionIndex = 0;

    private List<Callback> mCallbacks = null;

    private final ListeningScheduledExecutorService mExecutor;

    InboundRequestInstance(
            ListeningScheduledExecutorService executor, LocalEndpoint localEndpoint, Message msg) {
        mExecutor = executor;
        mLocalEndpoint = localEndpoint;
        mMessage = msg;
        mScopeThread = Thread.currentThread();
        if (mMessage.hasOptions()) {
            mSortedOptions = mMessage.getOptionSet().asSortedList();
        }
    }

    /**
     * This private method makes sure that the methods of this class aren't being called from an
     * unsupported scope. We need this because in the future this object will be re-used
     * aggressively. By making things fail hard and early we can avoid otherwise difficult to
     * discover bugs.
     *
     * @see InboundRequest#responsePending()
     */
    private void assertProperScope() {
        if (!mResponseIsPending && !mScopeThread.equals(Thread.currentThread())) {
            InboundRequestOutOfScopeException x;
            x = new InboundRequestOutOfScopeException("Out-of-scope call to InboundRequest object");
            LOGGER.log(Level.SEVERE, "Out-of-scope call to InboundRequest object", x);
            x.printStackTrace();
            throw x;
        }
    }

    @Override
    public LocalEndpoint getLocalEndpoint() {
        assertProperScope();
        return mLocalEndpoint;
    }

    @Override
    public Message getMessage() {
        assertProperScope();
        return mMessage;
    }

    @Override
    public boolean isDone() {
        return mIsDone;
    }

    @Override
    public boolean didAcknowledge() {
        return mDidSendAck || (mIsDone && !mDidDrop);
    }

    @Override
    public boolean isResponsePending() {
        return mResponseIsPending;
    }

    @Override
    public synchronized void drop() {
        assertProperScope();
        if (!mIsDone) {
            mIsDone = true;
            mDidDrop = true;
            if (DEBUG) LOGGER.info("Dropped " + mMessage);
        }
    }

    @Override
    public synchronized void acknowledge() {
        assertProperScope();

        if (mIsDone) {
            return;
        }

        mDidSendAck = true;

        getLocalEndpoint().sendResponse(mMessage.createAckResponse());
    }

    @Override
    public synchronized void sendResponse(MutableMessage response) {
        // TODO: Handle responseFailureCallback
        assertProperScope();

        if (mIsDone) {
            return;
        }

        response.setRemoteSocketAddress(mMessage.getRemoteSocketAddress());

        response.setInbound(false);

        if (response.getType() == Type.ACK) {
            if (mDidSendAck) {
                // TODO: Properly handle async stuff
                response.setType(Type.CON);
            } else {
                mDidSendAck = true;
            }
        }

        if (response.getType() == Type.ACK) {
            response.setMid(mMessage.getMid());
        } else {
            response.setMid(Message.MID_NONE);
        }

        if (response.getCode() != Code.EMPTY) {
            response.setToken(mMessage.getToken());
        }

        final LocalEndpoint localEndpoint = getLocalEndpoint();

        if (mCallbacks != null) {
            boolean shouldDrop = false;

            for (Callback x : mCallbacks) {
                shouldDrop |= !x.onInboundRequestSendResponseHook(response);
            }

            if (shouldDrop) {
                return;
            }

            if (mMessage.isMulticast()) {
                // Add some jitter to the response for multicast responses.
                localEndpoint.cancelAtClose(mExecutor.schedule(
                        () -> localEndpoint.sendRequest(response, this),
                        localEndpoint.getBehaviorContext().calcMulticastResponseDelay(),
                        TimeUnit.MILLISECONDS));
            } else {
                localEndpoint.sendRequest(response, this);
            }

        } else {
            if (mMessage.isMulticast()) {
                // Add some jitter to the response for multicast responses.
                localEndpoint.cancelAtClose(mExecutor.schedule(
                        () -> localEndpoint.sendResponse(response),
                        localEndpoint.getBehaviorContext().calcMulticastResponseDelay(),
                        TimeUnit.MILLISECONDS));
            } else {
                localEndpoint.sendResponse(response);
            }
        }

        mIsDone = !response.getOptionSet().hasObserve();
    }

    @Override
    public void registerCallback(Callback callback) {
        assertProperScope();
        if (mCallbacks == null) {
            mCallbacks = new LinkedList<>();
        }
        mCallbacks.add(callback);
    }

    private void handleOutboundException(Exception x) {
        // We know mCallbacks isn't null because this method
        // would not have been called otherwise.
        mCallbacks.forEach(cb -> cb.onInboundRequestResponseFailure(x));
    }

    @Override
    public void onOutboundMessageGotReply(LocalEndpoint endpoint, Message msg) {
        if (msg.isReset()) {
            handleOutboundException(
                    new ResetException("Got reset message: " + msg.toShortString()));
        }
    }

    @Override
    public void onOutboundMessageGotIOException(IOException x) {
        handleOutboundException(x);
    }

    @Override
    public void onOutboundMessageGotRuntimeException(CoapRuntimeException x) {
        handleOutboundException(x);
    }

    @Override
    public void onOutboundMessageRetransmitTimeout() {
        handleOutboundException(new TimeoutException("Retransmit timeout"));
    }

    @Override
    public void responsePending() {
        assertProperScope();
        mResponseIsPending = true;
    }

    @Override
    public int getCurrentOptionIndex() {
        return mOptionIndex;
    }

    @Override
    public void setCurrentOptionIndex(int optionIndex) {
        assertProperScope();

        if (optionIndex < 0 || mSortedOptions.size() < optionIndex) {
            throw new IndexOutOfBoundsException();
        }

        mOptionIndex = optionIndex;
    }

    @Override
    public void resetNextOption() {
        setCurrentOptionIndex(0);
    }

    @Override
    public void rewindOneOption() {
        assertProperScope();
        if (mOptionIndex > 0) {
            --mOptionIndex;
        }
    }

    @Override
    public Option nextOption() {
        assertProperScope();
        if (mSortedOptions != null && mSortedOptions.size() > mOptionIndex) {
            return mSortedOptions.get(mOptionIndex++);
        }
        return null;
    }

    @Override
    public Option nextOptionWithNumber(int number) {
        assertProperScope();
        while (mSortedOptions != null && mSortedOptions.size() > mOptionIndex) {
            Option option = mSortedOptions.get(mOptionIndex);
            if (option == null || option.getNumber() > number) {
                return null;
            }
            mOptionIndex++;
            if (option.getNumber() == number) {
                return option;
            }
        }
        return null;
    }

    @Override
    public void success() {
        assertProperScope();

        // TODO: Handle non-confirmable cases

        final int code = mMessage.getCode();

        if (code == Code.METHOD_GET) {
            sendSimpleResponse(Code.RESPONSE_CONTENT);
        } else if (code == Code.METHOD_PUT) {
            sendSimpleResponse(Code.RESPONSE_CHANGED);
        } else if (code == Code.METHOD_POST) {
            sendSimpleResponse(Code.RESPONSE_CHANGED);
        } else if (code == Code.METHOD_DELETE) {
            sendSimpleResponse(Code.RESPONSE_DELETED);
        } else {
            sendSimpleResponse(Code.of(Code.Class.SUCCESS, 0));
        }
    }
}
