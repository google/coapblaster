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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

class DeduplicationLayer extends AbstractLayer {
    private static final boolean DEBUG = false;
    private static final Logger LOGGER =
            Logger.getLogger(DeduplicationLayer.class.getCanonicalName());
    private final Map<KeyMid, Entry> mLookupTable = new ConcurrentHashMap<>();

    class Entry {
        final KeyMid mKey;
        final long mExpiresAfter;
        final boolean mIsRequest;
        Message mResponse;

        Entry(BehaviorContext behaviorContext, KeyMid key, boolean isRequest) {
            mExpiresAfter =
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime())
                            + behaviorContext.getCoapExchangeLifetimeMs();
            mKey = key;
            mIsRequest = isRequest;
        }
    }

    public DeduplicationLayer(LocalEndpointManager ignored) {}

    @Override
    public void close() {
        mLookupTable.clear();
        super.close();
    }

    @Override
    public int getSortOrder() {
        return 0;
    }

    @Override
    public void cleanup() {
        long now = TimeUnit.NANOSECONDS.toMillis(System.nanoTime());
        mLookupTable.forEach(
                (k, entry) -> {
                    if (now > entry.mExpiresAfter) {
                        if (DEBUG) LOGGER.info("Removing deduplication entry " + k);
                        mLookupTable.remove(k);
                    }
                });
    }

    @Override
    public void handleOutboundResponse(LocalEndpoint localEndpoint, Message msg) {
        final KeyMid key = new KeyMid(msg);
        final Entry entry = mLookupTable.get(key);

        if (entry != null && entry.mResponse == null) {
            entry.mResponse = msg.copy();
        }

        super.handleOutboundResponse(localEndpoint, msg);
    }

    @Override
    public void handleInboundRequest(InboundRequest inboundRequest) {
        final KeyMid key = new KeyMid(inboundRequest.getMessage());
        Entry entry;

        synchronized (mLookupTable) {
            entry = mLookupTable.get(key);

            if (entry == null) {
                mLookupTable.put(
                        key,
                        new Entry(
                                inboundRequest.getLocalEndpoint().getBehaviorContext(), key, true));
                super.handleInboundRequest(inboundRequest);
                return;
            }
        }

        if (entry.mIsRequest) {
            super.handleInboundRequest(inboundRequest);
            return;
        }

        if (entry.mResponse != null) {
            if (DEBUG)
                LOGGER.info(
                        "Inbound request " + key + " is a duplicate, sending previous response");
            super.handleOutboundResponse(inboundRequest.getLocalEndpoint(), entry.mResponse);
            inboundRequest.drop();
        } else {
            if (DEBUG) LOGGER.info("Inbound request " + key + " is a duplicate, sending ack");
            inboundRequest.acknowledge();
        }
    }

    @Override
    public synchronized void handleInboundResponse(
            LocalEndpoint localEndpoint,
            Message msg,
            OutboundMessageHandler outboundMessageHandler) {
        final KeyMid key = new KeyMid(msg);
        Entry entry;
        boolean entryIsNew = false;

        synchronized (mLookupTable) {
            entry = mLookupTable.get(key);

            if (entry != null && entry.mIsRequest) {
                if (DEBUG)
                    LOGGER.info(
                            "Inbound response "
                                    + key
                                    + " will replace duplicate tracking for similar entry");
                mLookupTable.remove(entry.mKey);
                entry = null;
            }

            if (entry == null) {
                entry = new Entry(localEndpoint.getBehaviorContext(), key, false);
                mLookupTable.put(key, entry);
                entryIsNew = true;
            }
        }

        if (entryIsNew) {
            super.handleInboundResponse(localEndpoint, msg, outboundMessageHandler);

        } else {
            if (DEBUG) LOGGER.info("Inbound response " + key + " is a duplicate");
        }

        if (msg.getType() == Type.CON) {
            Message response = entry.mResponse;
            if (response == null) {
                if (DEBUG) LOGGER.info("Sending ack for " + key);
                super.handleOutboundResponse(localEndpoint, msg.createAckResponse());
            } else if (!entryIsNew) {
                super.handleOutboundResponse(localEndpoint, response);
            }
        }
    }
}
