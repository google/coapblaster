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

import java.util.Map;
import java.util.concurrent.*;
import java.util.logging.Logger;
import org.checkerframework.checker.nullness.qual.Nullable;

final class RetransmissionLayer extends AbstractLayer {
    private static final boolean DEBUG = false;
    private static final Logger LOGGER =
            Logger.getLogger(RetransmissionLayer.class.getCanonicalName());

    private final Map<KeyMid, Entry> mMidLookupTable = new ConcurrentHashMap<>();

    private class Entry extends Transaction.Callback {
        private final KeyMid mKey;
        private final Message mMessage;
        private final LocalEndpoint mLocalEndpoint;
        private OutboundMessageHandler mHandler;
        private final BehaviorContext mBehaviorContext;
        private Transaction mTransaction = null;
        private int mRetransmitCount = 1;
        private volatile boolean mIsClosed = false;

        private ScheduledFuture<?> mScheduledRetransmit = null;
        private ScheduledFuture<?> mScheduledTimeout;

        Entry(
                KeyMid key,
                LocalEndpoint localEndpoint,
                Message msg,
                @Nullable OutboundMessageHandler handler) {
            mKey = key;
            mMessage = msg;
            mLocalEndpoint = localEndpoint;
            mHandler = handler;
            mBehaviorContext = mLocalEndpoint.getBehaviorContext();

            if (mHandler instanceof Transaction) {
                mTransaction = (Transaction) mHandler;
            }

            scheduleRetransmit();

            mScheduledTimeout =
                    getScheduledExecutorService()
                            .schedule(
                                    this::timeout,
                                    mBehaviorContext.getCoapMaxTransmitWaitMs(),
                                    TimeUnit.MILLISECONDS);

            if (mTransaction != null) {
                mTransaction.registerCallback(getScheduledExecutorService(), this);
            }
        }

        void timeout() {
            if (!mIsClosed) {
                if (DEBUG) LOGGER.info("Giving up on " + mMessage.toShortString());

                if (mHandler != null) {
                    mHandler.onOutboundMessageRetransmitTimeout();
                }

                close();
            }
        }

        @Override
        public void onTransactionFinished() {
            close();
        }

        @Override
        public void onTransactionException(Exception exception) {
            close();
        }

        @Override
        public void onTransactionResponse(LocalEndpoint endpoint, Message response) {
            close();
        }

        synchronized void close() {
            mIsClosed = true;
            mMidLookupTable.remove(mKey);

            if (mTransaction != null) {
                mTransaction.unregisterCallback(this);
                mTransaction = null;
            }

            mHandler = null;

            stopRetransmits();

            if (mScheduledTimeout != null) {
                mScheduledTimeout.cancel(false);
                mScheduledTimeout = null;
            }
        }

        synchronized void stopRetransmits() {
            if (mScheduledRetransmit != null) {
                mScheduledRetransmit.cancel(false);
                mScheduledRetransmit = null;
            }
        }

        synchronized void scheduleRetransmit() {
            stopRetransmits();

            if (!mIsClosed && mRetransmitCount < mBehaviorContext.getCoapMaxRetransmit()) {
                final int timeoutMs =
                        mBehaviorContext.calcRetransmitTimeoutAtAttempt(mRetransmitCount);
                mScheduledRetransmit =
                        getScheduledExecutorService()
                                .schedule(this::retransmit, timeoutMs, TimeUnit.MILLISECONDS);

                if (DEBUG) LOGGER.info("retransmit scheduled for " + timeoutMs + "ms");
            }
        }

        void retransmit() {
            if (!mIsClosed) {
                if (DEBUG) LOGGER.info("retransmit @ " + mRetransmitCount);
                mRetransmitCount++;
                if (mHandler == null) {
                    handleOutboundResponse(mLocalEndpoint, mMessage);
                } else {
                    handleOutboundRequest(mLocalEndpoint, mMessage, mHandler);
                }
                if (mRetransmitCount < mBehaviorContext.getCoapMaxRetransmit()) {
                    scheduleRetransmit();
                }
            }
        }

        @Override
        public String toString() {
            return mMessage.toShortString();
        }
    }

    RetransmissionLayer(LocalEndpointManager context) {}

    @Override
    public int getSortOrder() {
        return 10;
    }

    @Override
    public void close() {
        mMidLookupTable.forEach((k, entry) -> entry.close());
    }

    @Override
    public void handleInboundRequest(InboundRequest inboundRequest) {
        super.handleInboundRequest(inboundRequest);
    }

    @Override
    public void handleInboundResponse(
            LocalEndpoint localEndpoint,
            Message msg,
            OutboundMessageHandler outboundMessageHandler) {
        final KeyMid key = new KeyMid(msg);
        Entry entry = mMidLookupTable.get(key);

        if (entry != null) {
            if (DEBUG) LOGGER.info("Got response for " + entry + ", closing.");
            entry.close();
        }

        if (msg.isEmptyAck() || msg.isReset()) {
            // We stop processing here in the stack if the message is ultimately empty.
            outboundMessageHandler.onOutboundMessageGotReply(localEndpoint, msg);
        } else {
            super.handleInboundResponse(localEndpoint, msg, outboundMessageHandler);
        }
    }

    @Override
    public void handleOutboundRequest(
            LocalEndpoint localEndpoint,
            Message msg,
            @Nullable OutboundMessageHandler outboundMessageHandler) {
        if (msg.getType() == Type.CON) {
            final KeyMid key = new KeyMid(msg);

            if (!mMidLookupTable.containsKey(key)) {
                Entry entry = new Entry(key, localEndpoint, msg, outboundMessageHandler);
                mMidLookupTable.put(key, entry);
            }
        }
        super.handleOutboundRequest(localEndpoint, msg, outboundMessageHandler);
    }

    @Override
    public void handleOutboundResponse(LocalEndpoint localEndpoint, Message msg) {
        if (msg.getType() == Type.CON) {
            final KeyMid key = new KeyMid(msg);

            if (!mMidLookupTable.containsKey(key)) {
                Entry entry = new Entry(key, localEndpoint, msg, null);
                mMidLookupTable.put(key, entry);
            }
        }
        super.handleOutboundResponse(localEndpoint, msg);
    }
}
