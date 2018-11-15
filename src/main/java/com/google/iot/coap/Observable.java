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

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/** Utility class for easily making resources observable. */
public final class Observable {
    private static final boolean DEBUG = false;
    private static final Logger LOGGER = Logger.getLogger(Observable.class.getCanonicalName());

    private final Map<KeyToken, RemoteObserver> mObservers = new ConcurrentHashMap<>();
    private final HashSet<Observable> mDependencies = new HashSet<>();
    private final Map<Callback, Executor> mCallbacks = new ConcurrentHashMap<>();

    /**
     * Interface that {@link InboundRequestHandler} objects can optionally implement in order to
     * communicate their {@link Observable} instance to other objects which could use that
     * information, such as {@link Resource} objects.
     */
    public interface Provider {
        /** Returns the {@link Observable} instance associated with this object. */
        Observable onGetObservable();
    }

    /** Callback class for getting notifications for when observers are registered or not. */
    @SuppressWarnings("unused")
    public abstract static class Callback {
        /** Called when remote observers have been added to this observable. */
        public abstract void onHasRemoteObservers(Observable observable);

        /** Called when there are no more active observers of this observable. */
        public abstract void onNoRemoteObservers(Observable observable);
    }

    // Note: RemoteObserver is an internal class.
    private class RemoteObserver implements InboundRequest.Callback {
        private InboundRequest mInboundRequest = null;
        private int mCount = 0;
        private volatile MutableMessage mLastResponse = null;
        private volatile boolean mIsClosed = false;
        private volatile boolean mForceSendNextUpdate = false;
        private final KeyToken mKey;
        private int mMaxAge = TransactionImpl.DEFAULT_OBSERVATION_REFRESH_TIMEOUT;
        private ScheduledExecutorService mExecutor;
        private Future<?> mKeepaliveTimer = null;

        RemoteObserver(InboundRequest inboundRequest) {
            mKey = new KeyToken(inboundRequest.getMessage());
            mObservers.put(mKey, this);

            updateInboundRequest(inboundRequest);
        }

        @CanIgnoreReturnValue
        synchronized boolean updateInboundRequest(InboundRequest inboundRequest) {
            // Sanity check.
            if (mIsClosed) {
                // Something has gone terribly wrong.
                inboundRequest.sendSimpleResponse(Code.RESPONSE_INTERNAL_SERVER_ERROR);
                silentlyClose();
                return true;
            }

            if (DEBUG) {
                if (mInboundRequest == null) {
                    LOGGER.warning("Observer " + mKey + " opened");
                } else {
                    LOGGER.warning("Observer " + mKey + " updated");
                }
            }

            boolean ret = false;

            if (mInboundRequest == null) {
                mInboundRequest = inboundRequest;

                // Update the executor, in case it changed for some reason.
                mExecutor = mInboundRequest.getLocalEndpoint().getExecutor();

                // Add our hook so that we can modify the packet on the way out.
                mInboundRequest.registerCallback(this);

                // Since we will be sending back asynchronous responses,
                // we need to make sure that this inboundRequest doesn't
                // get re-used.
                mInboundRequest.responsePending();

                mForceSendNextUpdate = true;
            } else {
                inboundRequest.acknowledge();
                inboundRequest.drop();
                ret = true;
            }

            updateKeepalive();

            return ret;
        }

        InboundRequest getInboundRequest() {
            return mInboundRequest;
        }

        private synchronized void updateKeepalive() {
            stopKeepalive();
            if (!mIsClosed) {
                long timeout = TimeUnit.SECONDS.toMillis(mMaxAge - 1);
                if (timeout < 100) {
                    timeout = 100;
                }
                mKeepaliveTimer =
                        mExecutor.schedule(this::sendKeepalive, timeout, TimeUnit.MILLISECONDS);
            }
        }

        private synchronized void stopKeepalive() {
            if (mKeepaliveTimer != null) {
                mKeepaliveTimer.cancel(false);
                mKeepaliveTimer = null;
            }
        }

        private synchronized void sendKeepalive() {
            if (DEBUG) LOGGER.warning("Observer " + mKey + " sending keepalive");
            mForceSendNextUpdate = true;
            trigger(mLastResponse);
        }

        void silentlyClose() {
            if (!mIsClosed) {
                if (DEBUG) LOGGER.warning("Observer " + mKey + " closed");
                stopKeepalive();
                mIsClosed = true;
                synchronized (mObservers) {
                    if ((mObservers.remove(mKey) != null) && mObservers.isEmpty()) {
                        signalNoRemoteObservers();
                    }
                }
            }
        }

        void close() {
            if (!mIsClosed) {
                silentlyClose();

                final MutableMessage lastResponse = mLastResponse;

                if (lastResponse != null) {
                    // Force the MID to be reallocated.
                    lastResponse.setMid(Message.MID_NONE);

                    mInboundRequest.sendResponse(lastResponse);
                }
            }
        }

        synchronized void trigger() {
            if (!mIsClosed) {
                mCount++;

                final LocalEndpoint localEndpoint = mInboundRequest.getLocalEndpoint();
                final InboundRequestHandler inboundRequestHandler =
                        localEndpoint.getRequestHandler();

                if (inboundRequestHandler != null) {
                    try {
                        mInboundRequest.resetNextOption();
                        inboundRequestHandler.onInboundRequest(mInboundRequest);

                    } catch (CoapRuntimeException x) {
                        LOGGER.warning("Exception thrown while triggering observable: " + x);
                        x.printStackTrace();
                        silentlyClose();

                    } catch (RuntimeException x) {
                        LOGGER.warning("Exception thrown while triggering observable: " + x);
                        x.printStackTrace();
                        throw x;
                    }
                } else {
                    LOGGER.warning(
                            String.format(
                                    "%s no longer has a request handler, closing this observer",
                                    localEndpoint));
                    silentlyClose();
                }
                updateKeepalive();
            }
        }

        synchronized void trigger(Message msg) {
            if (!mIsClosed) {
                if (msg != mLastResponse) {
                    mCount++;
                }
                mInboundRequest.sendResponse(msg.mutableCopy());
                updateKeepalive();
            }
        }

        @Override
        public boolean onInboundRequestSendResponseHook(MutableMessage msg) {
            if (msg.isEmptyAck()) {
                // Nothing to process on an empty ack.
                return true;
            }

            if (Code.classValue(msg.getCode()) != Code.Class.SUCCESS) {
                // We go ahead and close the observer if an
                // error was generated from the higher level.
                silentlyClose();
            }

            if (mLastResponse != null
                    && Arrays.equals(msg.getPayload(), mLastResponse.getPayload())
                    && !mForceSendNextUpdate) {
                // Value didn't actually change. Skip sending updates.
                if (DEBUG) LOGGER.warning("Observer " + mKey + " value did not change");
                return false;
            }

            OptionSet optionSet = msg.getOptionSet();

            optionSet.setObserve(mIsClosed ? null : mCount);

            if (optionSet.hasMaxAge()) {
                //noinspection ConstantConditions
                mMaxAge = optionSet.getMaxAge();
            }

            mLastResponse = msg;
            return true;
        }

        @Override
        public void onInboundRequestResponseFailure(Exception exception) {
            if (!mIsClosed) {
                if (DEBUG) LOGGER.warning("onInboundRequestResponseFailure: " + exception);
                silentlyClose();
            }
        }
    }

    public Observable() {}

    /**
     * Method for giving this object a chance to examine an inbound request. This method should be
     * called just before you would start constructing your response message to the request.
     *
     * @param inboundRequest the {@link InboundRequest} object
     * @return true if this method ended up responding to the {@code inboundRequest} on your behalf,
     *     false if you should continue building a response yourself.
     */
    public synchronized boolean handleInboundRequest(InboundRequest inboundRequest) {
        final Message msg = inboundRequest.getMessage();
        final KeyToken key = new KeyToken(msg);
        final RemoteObserver existingObserver = mObservers.get(key);
        final int beforeCount = getObserverCount();
        final boolean messageIsObserving =
                msg.getCode() == Code.METHOD_GET
                        && msg.hasOptions()
                        && msg.getOptionSet().hasObserve();

        if (existingObserver != null) {
            if (inboundRequest.equals(existingObserver.getInboundRequest())) {
                // This is a "fake" request to generate
                // a response for this observer. Ignore it.
                return false;
            } else if (messageIsObserving) {
                // This is effectively a keep-alive request.
                // We simply update the observer object and return;
                return existingObserver.updateInboundRequest(inboundRequest);
            } else {
                // This is something else entirely. Close the existing observer.
                existingObserver.silentlyClose();
            }
        }

        if (messageIsObserving) {
            // The inbound request has an observe option, so we
            // go ahead and add it to our list of observers.
            new RemoteObserver(inboundRequest);
        }

        final int afterCount = getObserverCount();

        if (beforeCount > 0 && afterCount == 0) {
            signalNoRemoteObservers();
        } else if (beforeCount == 0 && afterCount > 0) {
            signalHasRemoteObservers();
        }
        return false;
    }

    /**
     * Triggers this observable, causing an update to be sent out to all subscribed observers. This
     * is accomplished by generating "fake" requests from the observers to tickle the handler to
     * send the appropriate response. This approach automatically handles things like having
     * observers with different values for {@link Option#ACCEPT}, since the "fake" request is based
     * on the original observation request.
     *
     * @see #triggerWithMessage(Message)
     */
    public synchronized void trigger() {
        mObservers.values().forEach(RemoteObserver::trigger);
        mDependencies.forEach(Observable::trigger);
    }

    /**
     * Triggers this observable to emit the given message to all of the subscribed observers. This
     * is generally more efficient than calling {@link #trigger()}, but it is also less flexible: it
     * the response will be invariant of things like what options were present in the original
     * observation request.
     *
     * <p>Note that the given message will be copied and modified as appropriate on a per-observer
     * basis.
     *
     * @param message the message to send out to all of the registered observers
     */
    public synchronized void triggerWithMessage(Message message) {
        mObservers.values().forEach(obs -> obs.trigger(message));
        mDependencies.forEach(Observable::trigger);
    }

    /** Returns the current number of observers in good standing. */
    public int getObserverCount() {
        return mObservers.size();
    }

    /** Ejects all active observers from this object. */
    public synchronized void ejectObservers() {
        mObservers.values().forEach(RemoteObserver::close);
        mObservers.clear();
        signalNoRemoteObservers();
    }

    /**
     * Add a separate {@link Observable} object as a dependency to this {@link Observable}. If this
     * observable is triggered after calling this method, it will cause {@link #trigger()} to be
     * invoked on this {@code dependentObservable}.
     *
     * @param dependentObservable the {@link Observable} to also trigger when this observable
     *     triggers
     * @see #removeDependency(Observable)
     */
    public synchronized void addDependency(Observable dependentObservable) {
        mDependencies.add(dependentObservable);
    }

    /**
     * Removes an {@link Observable} that was previously registered as a dependent from the
     * dependency list, no longer calling {@link #trigger()} whenever this observable is triggered.
     *
     * @param observable the observable to no longer trigger as a dependent
     * @see #addDependency(Observable)
     */
    public synchronized void removeDependency(Observable observable) {
        mDependencies.remove(observable);
    }

    /**
     * Registers the given {@link Callback} instance to receive notifications about this {@link
     * Observable}.
     *
     * @param executor the {@link Executor} instance to use to execute the callback methods
     * @param callback the {@link Callback} instance to notify
     * @see #unregisterCallback(Callback)
     */
    public void registerCallback(Executor executor, Callback callback) {
        mCallbacks.put(callback, executor);
    }

    /** @hide */
    public void registerCallback(Callback callback) {
        registerCallback(Runnable::run, callback);
    }

    /**
     * Unregisters a {@link Callback} instance that was previously registered with {@link
     * #registerCallback(Executor, Callback)}.
     *
     * @param callback the {@link Callback} instance to unregister
     * @see #registerCallback(Executor, Callback)
     */
    public void unregisterCallback(Callback callback) {
        mCallbacks.remove(callback);
    }

    private void signalHasRemoteObservers() {
        if (DEBUG) LOGGER.info("signalHasRemoteObservers() " + getObserverCount());
        mCallbacks.forEach(
                (cb, exec) ->
                        exec.execute(
                                () -> {
                                    cb.onHasRemoteObservers(this);
                                }));
    }

    private void signalNoRemoteObservers() {
        if (DEBUG) LOGGER.info("signalNoRemoteObservers()");
        mCallbacks.forEach(
                (cb, exec) ->
                        exec.execute(
                                () -> {
                                    cb.onNoRemoteObservers(this);
                                }));
    }
}
