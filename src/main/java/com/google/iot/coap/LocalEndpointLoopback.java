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

import java.net.NetworkInterface;
import java.net.SocketAddress;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Loopback {@link LocalEndpoint} instance, for testing. Messages sent out on this endpoint are
 * directly fed back into itself. Useful for testing purposes. Uses the non-standard {@code loop://}
 * URI scheme. Authority component is ignored.
 */
public final class LocalEndpointLoopback extends AbstractLocalEndpoint {

    private boolean mIsRunning = true;
    private final TransactionLookupSimple mTransactionLookup = new TransactionLookupSimple();
    private final SocketAddress mLocalSocketAddress =
            new SocketAddress() {
                @Override
                public String toString() {
                    return "localhost";
                }
            };

    public LocalEndpointLoopback(LocalEndpointManager context) {
        super(context);
        getStack().addStandardLayers();
        getStack().addReliabilityLayers();
    }

    @Override
    public String getScheme() {
        return "loop";
    }

    @Override
    protected void cleanup() {
        super.cleanup();
        mTransactionLookup.cleanup();
    }

    /** @hide */
    @Override
    protected void registerTransaction(
            MutableMessage msg, @Nullable OutboundMessageHandler transaction) {
        mTransactionLookup.registerTransaction(msg, transaction);
    }

    /** @hide */
    @Override
    protected OutboundMessageHandler lookupTransaction(Message msg) {
        return mTransactionLookup.lookupTransaction(msg);
    }

    /** @hide */
    protected void prepOutboundRequest(MutableMessage msg) {
        if (msg.getMid() == Message.MID_NONE) {
            msg.setMid(mTransactionLookup.getNextMidForSocketAddress(msg.getRemoteSocketAddress()));
        }
    }

    /** @hide */
    protected void prepOutboundResponse(MutableMessage msg) {
        if (msg.getMid() == Message.MID_NONE) {
            msg.setMid(mTransactionLookup.getNextMidForSocketAddress(msg.getRemoteSocketAddress()));
        }
    }

    /** @hide */
    @Override
    public Token newToken(
            SocketAddress socketAddress, @Nullable OutboundMessageHandler outboundMessageHandler) {
        return mTransactionLookup.newToken(socketAddress, outboundMessageHandler);
    }

    /** @hide */
    @Override
    public int newMid(SocketAddress socketAddress) {
        return mTransactionLookup.getNextMidForSocketAddress(socketAddress);
    }

    /** @hide */
    @Override
    protected void deliverOutboundMessage(Message msg) {
        if (!msg.hasMid()) {
            throw new IllegalArgumentException("Message doesn't have MID");
        }

        if (mIsRunning) {
            MutableMessage reflect = msg.mutableCopy();

            if (msg.getLocalSocketAddress() != null) {
                reflect.setRemoteSocketAddress(msg.getLocalSocketAddress());
            } else {
                reflect.setRemoteSocketAddress(getLocalSocketAddress());
            }
            reflect.setLocalSocketAddress(msg.getRemoteSocketAddress());

            reflect.setInbound(true);

            getExecutor().execute(() -> handleInboundMessage(reflect));
        }
    }

    @Override
    public void start() {
        super.start();
        mIsRunning = true;
    }

    @Override
    public void stop() {
        try {
            getStopLock().lock();
            super.stop();
            mIsRunning = false;
        } finally {
            getStopLock().unlock();
        }
    }

    @Override
    public boolean supportsMulticast() {
        return false;
    }

    @Override
    public void joinGroup(SocketAddress address, @Nullable NetworkInterface netIf) {}

    @Override
    public void leaveGroup(SocketAddress address, @Nullable NetworkInterface netIf) {}

    @Override
    public boolean isRunning() {
        return mIsRunning;
    }

    @Override
    public SocketAddress getLocalSocketAddress() {
        return mLocalSocketAddress;
    }
}
