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

import java.lang.ref.WeakReference;
import java.net.SocketAddress;
import java.util.*;
import java.util.logging.Logger;
import org.checkerframework.checker.nullness.qual.Nullable;

final class TransactionLookupSimple implements TransactionLookup {
    private static final boolean DEBUG = false;
    private static final Logger LOGGER =
            Logger.getLogger(TransactionLookup.class.getCanonicalName());

    private final Map<KeyMid, WeakReference<OutboundMessageHandler>> mMapMid = new HashMap<>();
    private final Map<KeyToken, WeakReference<OutboundMessageHandler>> mMapToken = new HashMap<>();
    private final WeakHashMap<OutboundMessageHandler, KeyMid> mReverseMapMid = new WeakHashMap<>();
    private static final Random mRandom = new Random();

    TransactionLookupSimple() {}

    @Override
    public synchronized int getNextMidForSocketAddress(@Nullable SocketAddress socketAddress) {
        int ret = (mRandom.nextInt() & 0xFFFF);
        int retries = 0;
        KeyMid key = new KeyMid(ret, socketAddress);

        while (mMapMid.containsKey(key)) {
            if (retries++ >= 0xFFFF) {
                String message =
                        "Unable to allocate unused MID, "
                                + mMapMid.size()
                                + " MIDs are currently in use";
                LOGGER.warning(message);
                throw new CoapRuntimeException(message);
            }
            ret = ((ret + 1) & 0xFFFF);
            key.setMid(ret);
        }

        return ret;
    }

    private synchronized Token getNextTokenForSocketAddress(@Nullable SocketAddress socketAddress) {
        int ret = (mRandom.nextInt() & 0xFFFF);
        int retries = 0;
        KeyToken key = new KeyToken(Token.tokenFromInteger(ret), socketAddress);

        while (mMapToken.containsKey(key) || ret == 0) {
            if (retries++ >= 0xFFFF) {
                String message =
                        "Unable to allocate unused token, "
                                + mMapToken.size()
                                + " tokens are currently in use";
                LOGGER.warning(message);
                throw new CoapRuntimeException(message);
            }
            ret = ((ret + 1) & 0xFFFF);

            key.setToken(Token.tokenFromInteger(ret));
        }

        return key.getToken();
    }

    @Override
    public synchronized Token newToken(
            @Nullable SocketAddress socketAddress, @Nullable OutboundMessageHandler handler) {
        Token ret = getNextTokenForSocketAddress(socketAddress);
        WeakReference<OutboundMessageHandler> reference = new WeakReference<>(handler);
        mMapToken.put(new KeyToken(ret, socketAddress), reference);
        return ret;
    }

    @Override
    public synchronized void registerTransaction(
            MutableMessage message, @Nullable OutboundMessageHandler handler) {
        WeakReference<OutboundMessageHandler> reference = new WeakReference<>(handler);

        if (mReverseMapMid.containsKey(handler)) {
            mMapMid.put(mReverseMapMid.get(handler), new WeakReference<>(null));
            mReverseMapMid.remove(handler);
            if (DEBUG) LOGGER.info("Changing MID associated with transaction");
        }

        SocketAddress addr = message.getRemoteSocketAddress();

        if (message.getMid() == Message.MID_NONE) {
            message.setMid(getNextMidForSocketAddress(addr));
        }
        KeyMid keyMid = new KeyMid(message);
        mMapMid.put(keyMid, reference);
        mReverseMapMid.put(handler, keyMid);

        if (message.getCode() != Code.EMPTY) {
            if (message.hasEmptyToken()) {
                message.setToken(newToken(addr, handler));
            } else {
                mMapToken.put(new KeyToken(message), reference);
            }
        }
    }

    @Override
    public synchronized OutboundMessageHandler lookupTransaction(Message message) {
        OutboundMessageHandler ret;
        final KeyMid keyMid = new KeyMid(message);

        WeakReference<OutboundMessageHandler> reference = mMapMid.get(keyMid);
        // `reference` now holds MID-based OutboundMessageHandler

        if (reference != null) {
            ret = reference.get();
            if (DEBUG && ret == null) LOGGER.info("Transaction was collected");
        } else {
            ret = null;
        }


        if (!message.hasEmptyToken()) {
            final KeyToken keyToken = new KeyToken(message);
            reference = mMapToken.get(keyToken);
            // `reference` now holds token-based OutboundMessageHandler
        }

        if (ret == null) {
            if (reference != null) {

                ret = reference.get();
                if (DEBUG && ret == null) LOGGER.info("Transaction was collected");
            }
        } else if (reference != null) {
            OutboundMessageHandler trans_token = reference.get();
            if ((trans_token != null) && !trans_token.equals(ret)) {
                // The transaction for the token and mid don't match.
                // We return the token holder in this case.
                LOGGER.warning("BUG: MID/Token mismatch, (Token:"  + trans_token
                        + "; MID:" + ret + ")");
                ret = trans_token;
            }
        }
        return ret;
    }

    @Override
    public synchronized void reset() {
        mMapMid.clear();
        mMapToken.clear();
        mReverseMapMid.clear();
    }

    @Override
    public synchronized void cleanup() {
        for (Map.Entry<KeyMid, WeakReference<OutboundMessageHandler>> entry :
                new ArrayList<>(mMapMid.entrySet())) {
            if (entry.getValue().get() == null) {
                if (DEBUG) LOGGER.warning("Cleaning up MID entry: " + entry.getKey());
                mMapMid.remove(entry.getKey());
            }
        }
        for (Map.Entry<KeyToken, WeakReference<OutboundMessageHandler>> entry :
                new ArrayList<>(mMapToken.entrySet())) {
            if (entry.getValue().get() == null) {
                if (DEBUG) LOGGER.warning("Cleaning up token entry: " + entry.getKey());
                mMapToken.remove(entry.getKey());
            }
        }
    }
}
