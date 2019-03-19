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
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import org.checkerframework.checker.nullness.qual.Nullable;

abstract class AbstractLocalEndpoint implements LocalEndpoint {
    private static final boolean DEBUG = false;
    private static final Logger LOGGER =
            Logger.getLogger(AbstractLocalEndpoint.class.getCanonicalName());

    private ListeningScheduledExecutorService mExecutor = null;
    private InboundRequestHandler mInboundRequestHandler = null;
    private final LocalEndpointManager mManager;

    private final Stack mStack;
    private final ReadWriteLock mStopRunLock = new ReentrantReadWriteLock();
    private Future<?> mCleanupTimer = null;
    private final Stack.Outbox mOutbox;
    private BehaviorContext mBehaviorContext;
    private Interceptor mInterceptor = null;
    private final Set<ListenableFuture<?>> mCancelAtClose = new HashSet<>();

    /** @hide */
    protected Lock getRunLock() {
        return mStopRunLock.readLock();
    }

    /** @hide */
    protected Lock getStopLock() {
        return mStopRunLock.writeLock();
    }

    AbstractLocalEndpoint(LocalEndpointManager manager) {
        mManager = Objects.requireNonNull(manager);
        mBehaviorContext = mManager.getDefaultBehaviorContext();

        mOutbox =
                (msg) -> {
                    if (DEBUG) LOGGER.info("Delivering Message: " + msg);

                    if (mInterceptor != null && mInterceptor.onInterceptOutbound(msg)) {
                        // Message was intercepted.
                        return;
                    }

                    deliverOutboundMessage(msg);
                };

        mStack = new Stack(manager, mOutbox);

        setInterceptor(manager.getDefaultInterceptor());
    }

    /** @hide Called periodically to clean up old state. */
    protected void cleanup() {
        mStack.cleanup();
    }

    @Override
    public BehaviorContext getBehaviorContext() {
        return mBehaviorContext;
    }

    @Override
    public void setBehaviorContext(BehaviorContext behaviorContext) {
        mBehaviorContext = behaviorContext;
    }

    @Override
    public void setInterceptor(Interceptor interceptor) {
        mInterceptor = interceptor;
    }

    @Override
    public boolean attemptToJoinDefaultCoapGroups(@Nullable NetworkInterface netIf) {
        boolean ret = false;
        if (supportsMulticast()) {
            final String[] groups =
                    new String[] {
                        Coap.ALL_NODES_MCAST_IP4,
                        Coap.ALL_NODES_MCAST_IP6_LINK_LOCAL,
                        Coap.ALL_NODES_MCAST_IP6_REALM_LOCAL,
                        Coap.ALL_NODES_MCAST_IP6_ADMIN_LOCAL,
                        Coap.ALL_NODES_MCAST_IP6_SITE_LOCAL
                    };

            for (String group : groups) {
                try {
                    joinGroup(new InetSocketAddress(group, 0), netIf);
                    ret = true;
                } catch (IOException x) {
                    if (DEBUG)
                        LOGGER.warning(
                                "Unable to join multicast group \""
                                        + group
                                        + "\" on "
                                        + netIf
                                        + ": "
                                        + x);
                }
            }
        }
        return ret;
    }

    @Override
    public void start() {
        if (!isRunning()) {
            if (mCleanupTimer != null) {
                mCleanupTimer.cancel(false);
            }
            mCleanupTimer =
                    getExecutor().scheduleAtFixedRate(this::cleanup, 2, 2, TimeUnit.MINUTES);
        }
    }

    @Override
    public void stop() {
        try {
            getStopLock().lock();
            if (mCleanupTimer != null) {
                mCleanupTimer.cancel(false);
                mCleanupTimer = null;
            }
        } finally {
            getStopLock().unlock();
        }
    }

    Stack getStack() {
        return mStack;
    }

    /** {@hide} Returns the {@link LocalEndpointManager} that this endpoint is associated with. */
    public LocalEndpointManager getManager() {
        return mManager;
    }

    @Override
    public void cancelAtClose(ListenableFuture<?> futureToCancelAtClose) {
        mExecutor.execute(()->{
            if (!futureToCancelAtClose.isDone()) {
                synchronized (mCancelAtClose) {
                    mCancelAtClose.add(futureToCancelAtClose);
                }

                futureToCancelAtClose.addListener(()-> {
                    synchronized (mCancelAtClose) {
                        mCancelAtClose.remove(futureToCancelAtClose);
                    }
                }, Runnable::run);
            }
        });
    }

    @Override
    public void close() throws IOException {
        if (DEBUG) LOGGER.info("close");
        try {
            getStopLock().lock();
            setRequestHandler(null);
            stop();

            Set<ListenableFuture<?>> cancelAtCloseCopy;

            synchronized (mCancelAtClose) {
                cancelAtCloseCopy = new HashSet<>(mCancelAtClose);
                mCancelAtClose.clear();
            }

            for (ListenableFuture<?> future : cancelAtCloseCopy) {
                future.cancel(true);
            }

            mStack.close();
        } finally {
            getStopLock().unlock();
        }
    }

    @Override
    public ListeningScheduledExecutorService getExecutor() {
        if (mExecutor == null) {
            mExecutor = mManager.getExecutor();
        }
        return mExecutor;
    }

    @Override
    public void setExecutor(ListeningScheduledExecutorService executor) {
        mExecutor = executor;
    }

    /** {@hide} */
    protected abstract void registerTransaction(
            MutableMessage msg, @Nullable OutboundMessageHandler transaction);

    /** {@hide} */
    protected abstract OutboundMessageHandler lookupTransaction(Message msg);

    /** {@hide} */
    protected abstract void prepOutboundRequest(MutableMessage msg);

    /** {@hide} */
    protected abstract void prepOutboundResponse(MutableMessage msg);

    /** {@hide} */
    @Override
    public void sendRequest(
            Message message, @Nullable OutboundMessageHandler outboundMessageHandler) {
        if (!isRunning()) {
            return;
        }

        MutableMessage mutableMessage = message.mutableCopy();

        try {
            getRunLock().lock();

            prepOutboundRequest(mutableMessage);
            registerTransaction(mutableMessage, outboundMessageHandler);
            if (DEBUG) LOGGER.info("sendRequest: " + mutableMessage);
            mStack.handleOutboundRequest(this, mutableMessage, outboundMessageHandler);

        } finally {
            getRunLock().unlock();
        }
    }

    /** {@hide} */
    @Override
    public void sendResponse(Message message) {
        if (!isRunning()) {
            return;
        }

        MutableMessage mutableMessage = message.mutableCopy();

        try {
            getRunLock().lock();

            prepOutboundResponse(mutableMessage);

            if (DEBUG) LOGGER.info("sendResponse: " + mutableMessage);

            mStack.handleOutboundResponse(this, mutableMessage);
        } finally {
            getRunLock().unlock();
        }
    }

    /** @hide */
    protected abstract void deliverOutboundMessage(Message msg);

    /** @hide */
    protected void handleInboundMessage(Message msg) {
        if (!msg.isInbound()) {
            throw new IllegalArgumentException("Message not marked as inbound");
        }

        try {
            getRunLock().lock();

            if (!isRunning()) {
                return;
            }

            if (DEBUG) LOGGER.info("handleInboundMessage: " + msg);

            if (mInterceptor != null && mInterceptor.onInterceptInbound(msg)) {
                // Message was intercepted.
                return;
            }

            if (msg.isRequest()) {
                InboundRequestInstance inboundRequest =
                        new InboundRequestInstance(getExecutor(), this, msg);
                mStack.handleInboundRequest(inboundRequest);

            } else if (msg.getCode() == Code.EMPTY && msg.getType() == Type.CON) {
                // Handle pings immediately.
                deliverOutboundMessage(msg.createRstResponse());
            } else {
                OutboundMessageHandler transaction = lookupTransaction(msg);

                if (transaction != null) {
                    mStack.handleInboundResponse(this, msg, transaction);

                } else if (msg.getType() == Type.CON || msg.getType() == Type.NON) {
                    // Transaction is invalid or inactive, send reset response.
                    mOutbox.onDeliverOutboundMessage(msg.createRstResponse());
                }
            }
        } finally {
            getRunLock().unlock();
        }
    }

    /**
     * Handle transport-specific exceptions and errors. This would be called when the transport
     * indicates some sort of error which prevented a given message from being delivered. This could
     * be something as simple as an ICMP "host unreachable" error or something as complicated as a
     * DTLS session failure.
     *
     * <p>{@hide}
     *
     * @param msg the message that was trying to be sent when the exception was generated
     * @param x the exception to handle
     */
    protected void handleTransportException(Message msg, Exception x) {
        if (x instanceof IOException) {
            LOGGER.log(Level.INFO, "Failed to deliver " + msg.toShortString() + "; " + x);
        } else {
            LOGGER.log(Level.WARNING, "Failed to deliver " + msg.toShortString(), x);
        }

        if (DEBUG) x.printStackTrace();

        OutboundMessageHandler transaction = lookupTransaction(msg);

        if (transaction != null) {
            if (x instanceof IOException) {
                transaction.onOutboundMessageGotIOException((IOException) x);

            } else if (x instanceof CoapRuntimeException) {
                transaction.onOutboundMessageGotRuntimeException((CoapRuntimeException) x);

            } else {
                x = new CoapRuntimeException(x);
                transaction.onOutboundMessageGotRuntimeException((CoapRuntimeException) x);
            }

        } else {
            LOGGER.warning("Got " + x + " without related transaction.");
            x.printStackTrace();
        }
    }

    @Override
    public int getDefaultPort() {
        return mManager.getDefaultPortForScheme(getScheme());
    }

    @Override
    public InboundRequestHandler getRequestHandler() {
        return mInboundRequestHandler;
    }

    @Override
    public void setRequestHandler(@Nullable InboundRequestHandler rh) {
        mStack.setRequestHandler(rh);
        mInboundRequestHandler = rh;
    }

    @Override
    public URI createUriFromSocketAddress(SocketAddress socketAddress) {
        return Utils.createUriFromSocketAddress(socketAddress, getScheme());
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "<" + getLocalSocketAddress() + ">";
    }
}
