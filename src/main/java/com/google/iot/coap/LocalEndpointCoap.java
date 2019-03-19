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

import java.io.IOException;
import java.net.*;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.util.concurrent.RejectedExecutionException;
import java.util.logging.Logger;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * {@link LocalEndpoint} for standard IPv4/IPv6 CoAP traffic. Uses the standard {@code coap://} URI
 * scheme. Most common scenarios, do not involve directly interacting with this class but instead
 * use the {@link Client}, {@link Server}, and {@link Transaction} objects.
 */
public final class LocalEndpointCoap extends AbstractLocalEndpoint {
    private static final boolean DEBUG = false;
    private static final Logger LOGGER =
            Logger.getLogger(LocalEndpointCoap.class.getCanonicalName());

    private final DatagramSocket mSocket;
    private final TransactionLookupSimple mTransactionLookup = new TransactionLookupSimple();
    private final MessageCodec mCodec = new CoapMessageCodec();

    private final ByteBuffer mOutboundByteBuffer =
            ByteBuffer.wrap(new byte[getBehaviorContext().getMaxOutboundPacketLength()]);
    private final DatagramPacket mOutboundPacket =
            new DatagramPacket(mOutboundByteBuffer.array(), 0);

    private volatile boolean mThreadShouldStop = false;

    private final Thread mListeningThread =
            new Thread() {
                @Override
                public void run() {
                    byte[] buffer = new byte[getBehaviorContext().getMaxInboundPacketLength()];
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    ByteBuffer byteBuffer = ByteBuffer.wrap(packet.getData());

                    while (!mThreadShouldStop && !interrupted()) {
                        try {
                            mSocket.receive(packet);

                            byteBuffer.clear();
                            byteBuffer.limit(packet.getOffset() + packet.getLength());
                            byteBuffer.position(packet.getOffset());

                            final MutableMessage msg =
                                    mCodec.decodeMessage(byteBuffer)
                                            .setInbound(true)
                                            .setLocalSocketAddress(getLocalSocketAddress())
                                            .setRemoteSocketAddress(packet.getSocketAddress());

                            getExecutor().execute(() -> handleInboundMessage(msg));

                        } catch (IOException | RejectedExecutionException e) {
                            if (!mThreadShouldStop && !interrupted()) {
                                LOGGER.warning("Exception: " + e);
                                mThreadShouldStop = true;
                            }

                        } catch (CoapParseException e) {
                            if (DEBUG) {
                                LOGGER.warning("Inbound parse exception: " + e);
                            }
                        }
                    }
                }
            };

    public LocalEndpointCoap(LocalEndpointManager context, DatagramSocket socket) {
        super(context);

        mSocket = socket;

        getStack().addStandardLayers();
        getStack().addReliabilityLayers();
    }

    /** @hide */
    @Override
    protected void cleanup() {
        super.cleanup();
        mTransactionLookup.cleanup();
    }

    @Override
    public void close() throws IOException {
        try {
            try {
                getStopLock().lock();
                mThreadShouldStop = true;
                mListeningThread.interrupt();
                mSocket.close();
            } finally {
                super.close();
            }
        } finally {
            getStopLock().unlock();
        }

        try {
            // Wait a maximum of one second to join the IO thread.
            // It should actually join almost immediately.
            mListeningThread.join(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        if (mListeningThread.isAlive()) {
            throw new CoapRuntimeException("Unable to shutdown internal thread");
        }
    }

    @Override
    public String getScheme() {
        return Coap.SCHEME_UDP;
    }

    @Override
    public void start() {
        if (!isRunning()) {
            mThreadShouldStop = false;
            super.start();
            mListeningThread.start();
        }
    }

    @Override
    public void stop() {
        try {
            getStopLock().lock();
            super.stop();
            if (isRunning()) {
                mThreadShouldStop = true;
                mListeningThread.interrupt();

                try {
                    mListeningThread.join(100);
                } catch (InterruptedException e) {
                    if (DEBUG) LOGGER.info("Interrupted: " + e);
                    Thread.currentThread().interrupt();
                }

                if (isRunning()) {
                    throw new CoapRuntimeException("Unable to stop UDP thread");
                }
            }
        } finally {
            getStopLock().unlock();
        }
    }

    @Override
    public boolean isRunning() {
        return mListeningThread.isAlive();
    }

    @Override
    public SocketAddress getLocalSocketAddress() {
        return mSocket.getLocalSocketAddress();
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
        SocketAddress rsockaddr = msg.getRemoteSocketAddress();
        if (msg.getMid() == Message.MID_NONE && rsockaddr != null) {
            msg.setMid(mTransactionLookup.getNextMidForSocketAddress(rsockaddr));
        }
    }

    /** @hide */
    protected void prepOutboundResponse(MutableMessage msg) {
        SocketAddress rsockaddr = msg.getRemoteSocketAddress();
        if (msg.getMid() == Message.MID_NONE && rsockaddr != null) {
            msg.setMid(mTransactionLookup.getNextMidForSocketAddress(rsockaddr));
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
    protected synchronized void deliverOutboundMessage(Message msg) {
        SocketAddress remoteSocketAddress = msg.getRemoteSocketAddress();

        if (remoteSocketAddress == null) {
            handleTransportException(
                    msg, new CoapRuntimeException("Missing remote socket address"));
            return;
        }

        getRunLock().lock();

        try {
            mOutboundByteBuffer.clear();
            mCodec.encodeMessage(msg, mOutboundByteBuffer);
            mOutboundPacket.setData(mOutboundByteBuffer.array(), 0, mOutboundByteBuffer.position());
            mOutboundPacket.setSocketAddress(remoteSocketAddress);
            mSocket.send(mOutboundPacket);

        } catch (BufferOverflowException x) {
            try {
                // Indicate that the response is too big. This should only happen if block
                // transfer support isn't being used.
                MutableMessage newMsg =
                        msg.mutableCopy()
                                .setCode(Code.RESPONSE_INTERNAL_SERVER_ERROR)
                                .setPayload("Response too large to fit in single packet")
                                .clearOptions();

                mOutboundByteBuffer.clear();
                mCodec.encodeMessage(newMsg, mOutboundByteBuffer);
                mOutboundPacket.setData(
                        mOutboundByteBuffer.array(), 0, mOutboundByteBuffer.position());
                mOutboundPacket.setSocketAddress(remoteSocketAddress);
                mSocket.send(mOutboundPacket);

            } catch (IOException | CoapRuntimeException secondaryException) {
                handleTransportException(msg, secondaryException);
            }

            handleTransportException(msg, x);

        } catch (IOException | CoapRuntimeException x) {
            handleTransportException(msg, x);

        } finally {
            getRunLock().unlock();
        }
    }

    @Override
    public boolean supportsMulticast() {
        return (mSocket instanceof MulticastSocket) && !mSocket.isConnected();
    }

    @Override
    public void joinGroup(SocketAddress address, @Nullable NetworkInterface netIf)
            throws IOException {
        if (!supportsMulticast()) {
            return;
        }

        MulticastSocket socket = (MulticastSocket) mSocket;

        socket.joinGroup(address, netIf);
    }

    @Override
    public void leaveGroup(SocketAddress address, @Nullable NetworkInterface netIf)
            throws IOException {
        if (!supportsMulticast()) {
            return;
        }

        MulticastSocket socket = (MulticastSocket) mSocket;

        socket.leaveGroup(address, netIf);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "<" + getLocalSocketAddress() + "|" + mSocket + ">";
    }
}
