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

import com.google.common.util.concurrent.ListenableFuture;
import com.google.iot.cbor.CborRuntimeException;
import java.io.IOException;
import java.net.SocketAddress;
import java.net.URI;
import java.util.concurrent.*;
import java.util.logging.Logger;
import org.checkerframework.checker.nullness.qual.Nullable;

final class TransactionImpl implements Transaction, OutboundMessageHandler {
    private static final boolean DEBUG = false;
    private static final Logger LOGGER = Logger.getLogger(Transaction.class.getCanonicalName());

    private static final int MAX_OBSERVATION_REFRESH_TIMEOUT = 120; // in seconds
    private static final int MIN_OBSERVATION_REFRESH_TIMEOUT = 10; // in seconds
    static final int DEFAULT_OBSERVATION_REFRESH_TIMEOUT = 20; // in seconds

    private final LocalEndpointManager mLocalEndpointManager;
    private LocalEndpoint mLocalEndpoint;

    private ListenableFuture<SocketAddress> mFutureSocketAddress = null;

    private final ConcurrentMap<Callback, Executor> mCallbacks = new ConcurrentHashMap<>();

    private volatile boolean mIsAcknowledged = false;
    private volatile boolean mIsActive = true;
    private volatile boolean mIsCancelled = false;
    private volatile boolean mIsMulticast;
    private final boolean mIsObserving;
    private int mObservationRetryTimeout = DEFAULT_OBSERVATION_REFRESH_TIMEOUT;
    private Future<?> mObservationRetryTimer = null;

    private BlockReconstructor mBlockReconstructor = null;

    private final URI mDestUri;

    private final MutableMessage mRequest;
    private volatile Message mResponse = null;
    private volatile Exception mException = null;

    private final BehaviorContext mBehaviorContext;
    private boolean mOmitUriHostPortOptions;

    TransactionImpl(
            LocalEndpointManager localEndpointManager,
            LocalEndpoint localEndpoint,
            MutableMessage request,
            @Nullable URI destUri,
            boolean omitUriHostPortOptions) {
        mLocalEndpointManager = localEndpointManager;
        mLocalEndpoint = localEndpoint;
        mRequest = request;
        mDestUri = destUri;
        mBehaviorContext = mLocalEndpoint.getBehaviorContext();
        mOmitUriHostPortOptions = omitUriHostPortOptions;

        mIsObserving = mRequest.getOptionSet().hasObserve();
        mIsMulticast = mRequest.isMulticast();
    }

    private void firedObservationRetryTimer() {
        if (isActive()) {
            if (DEBUG) {
                LOGGER.info("Keep-alive retransmit for " + mRequest.toShortString());
            }
            restart();
        }
    }

    private synchronized void resetObservationRetryTimer() {
        if (mObservationRetryTimer != null) {
            mObservationRetryTimer.cancel(false);
            mObservationRetryTimer = null;
        }
        if (isActive()) {
            long timeout = TimeUnit.SECONDS.toMillis(mObservationRetryTimeout);
            timeout -= (long) (timeout * mBehaviorContext.getRandom().nextFloat() * 0.1);
            mObservationRetryTimer =
                    mLocalEndpoint
                            .getExecutor()
                            .schedule(
                                    this::firedObservationRetryTimer,
                                    timeout,
                                    TimeUnit.MILLISECONDS);
        }
    }

    private void updateObservationRetryTimeout(int timeoutInSeconds) {
        if (!isCancelled()) {
            mObservationRetryTimeout =
                    Math.max(
                            MIN_OBSERVATION_REFRESH_TIMEOUT,
                            Math.min(MAX_OBSERVATION_REFRESH_TIMEOUT, timeoutInSeconds));
            resetObservationRetryTimer();
        }
    }

    @Override
    public synchronized void restart() {
        if (mIsCancelled) {
            throw new CancellationException();
        }

        // Reset everything.
        mIsActive = true;
        mException = null;
        if (isFinishedAfterFirstResponse()) {
            mResponse = null;
        }

        // Reset the MID for the message this will
        // be reassigned later in the stack.
        mRequest.setMid(Message.MID_NONE);

        if (isObserving()) {
            resetObservationRetryTimer();
        }

        try {
            if (mRequest.getRemoteSocketAddress() == null) {
                // Did a previous lookup finish?
                if (mFutureSocketAddress != null) {

                    if (mFutureSocketAddress.isDone()) {
                        if (DEBUG) LOGGER.info("Socket address lookup has finished");

                        mRequest.setRemoteSocketAddress(mFutureSocketAddress.get());

                        if (DEBUG) {
                            LOGGER.info(
                                    "RemoteSocketAddress = " + mRequest.getRemoteSocketAddress());
                        }

                    } else {
                        // Lookup still in progress.
                        return;
                    }

                } else {
                    // We must perform a lookup first.
                    String scheme = mLocalEndpoint.getScheme();
                    String host = mRequest.getOptionSet().getUriHost();
                    int port = -1;

                    if (mDestUri == null) {
                        if (mRequest.getOptionSet().hasUriPort()) {
                            port = mRequest.getOptionSet().getUriPort();
                        }
                    } else {
                        scheme = mDestUri.getScheme();
                        host = mDestUri.getHost();
                        port = mDestUri.getPort();
                    }

                    if (port < 0) {
                        port = mLocalEndpoint.getDefaultPort();
                    }

                    if (host == null) {
                        handleException(new HostLookupException("Host was not specified"));
                        return;
                    }

                    if (DEBUG)
                        LOGGER.info(
                                "Socket address lookup required: scheme:"
                                        + scheme
                                        + " host:"
                                        + host
                                        + " port:"
                                        + port);

                    mFutureSocketAddress =
                            mLocalEndpointManager.lookupSocketAddress(scheme, host, port);

                    mFutureSocketAddress.addListener(
                            () -> {
                                synchronized (TransactionImpl.this) {
                                    if (!mIsCancelled) {
                                        restart();
                                    }
                                }
                            },
                            mLocalEndpoint.getExecutor());

                    return;
                }
            }

            if (mRequest.getLocalSocketAddress() == null) {
                mRequest.setLocalSocketAddress(mLocalEndpoint.getLocalSocketAddress());
            }

            if (mRequest.hasEmptyToken()) {
                mRequest.setToken(mLocalEndpoint.newToken(mRequest.getRemoteSocketAddress(), this));
            }

            // Update multicast flag
            mIsMulticast = mRequest.isMulticast();

            // Remove URI-Host and URI-Port, if requested
            if (mOmitUriHostPortOptions) {
                mRequest.getOptionSet().setUriHost(null);
                mRequest.getOptionSet().setUriPort(null);
            }

            if (DEBUG) LOGGER.info("Transaction is sending " + mRequest.toShortString());
            mLocalEndpoint.sendRequest(mRequest, this);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

        } catch (ExecutionException e) {
            if (e.getCause() instanceof HostLookupException) {
                handleException(new HostLookupException(e.getCause()));

            } else if (e.getCause() instanceof IOException) {
                handleException((IOException) e.getCause());

            } else {
                handleException(e);
            }
        }
        return;
    }

    @Override
    public boolean isCancelled() {
        return mIsCancelled;
    }

    @Override
    public boolean isAcknowledged() {
        return mIsAcknowledged;
    }

    @Override
    public boolean isActive() {
        return mIsActive;
    }

    @Override
    public boolean isMulticast() {
        return mIsMulticast;
    }

    @Override
    public boolean isObserving() {
        return mIsObserving;
    }

    @Override
    public boolean isFinishedAfterFirstResponse() {
        return !mIsMulticast && !mIsObserving;
    }

    @Override
    public synchronized void cancelWithoutUnobserve() {
        if (!mIsActive) {
            // You can't cancel something that isn't active.
            return;
        }

        mIsCancelled = true;
        mIsActive = false;

        mCallbacks.forEach(
                (cb, exec) ->
                        exec.execute(
                                () -> {
                                    cb.onTransactionCancelled();
                                    cb.onTransactionFinished();
                                }));
        mCallbacks.clear();

        if (mFutureSocketAddress != null) {
            mFutureSocketAddress.cancel(false);
        }

        if (mObservationRetryTimer != null) {
            mObservationRetryTimer.cancel(false);
            mObservationRetryTimer = null;
        }
    }

    @Override
    public void cancel() {
        cancelWithoutUnobserve();
        if (mRequest.getOptionSet().hasObserve()) {
            // We need to send this one last time without the observe option.
            MutableMessage observeCancelRequest = mRequest.mutableCopy();
            observeCancelRequest.getOptionSet().setObserve(null);
            observeCancelRequest.setMid(Message.MID_NONE);
            mLocalEndpoint.sendRequest(observeCancelRequest, this);
        }
    }

    @Override
    public LocalEndpoint getLocalEndpoint() {
        return mLocalEndpoint;
    }

    @Override
    public synchronized void registerCallback(Executor executor, Callback cb) {
        if (isActive()) {
            mCallbacks.put(cb, executor);
            if (mResponse != null) {
                executor.execute(() -> cb.onTransactionResponse(mLocalEndpoint, mResponse));
            } else if (isAcknowledged()) {
                executor.execute(cb::onTransactionAcknowledged);
            }
        } else if (isCancelled()) {
            executor.execute(
                    () -> {
                        cb.onTransactionCancelled();
                        cb.onTransactionFinished();
                    });
        } else if (mException != null) {
            executor.execute(
                    () -> {
                        cb.onTransactionException(mException);
                        cb.onTransactionFinished();
                    });
        } else if (mResponse != null) {
            executor.execute(
                    () -> {
                        cb.onTransactionResponse(mLocalEndpoint, mResponse);
                        cb.onTransactionFinished();
                    });
        } else {
            executor.execute(cb::onTransactionFinished);
        }
    }

    @Override
    public void registerCallback(Callback cb) {
        registerCallback(mLocalEndpointManager.getExecutor(), cb);
    }

    @Override
    public void unregisterCallback(@Nullable Callback cb) {
        mCallbacks.remove(cb);
    }

    @Override
    public Message getRequest() {
        return mRequest;
    }

    @Override
    public synchronized Message getResponse(long timeout)
            throws InterruptedException, HostLookupException, IOException, TimeoutException {
        final long expires = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeout);

        while (mResponse == null && mException == null && mIsActive) {
            long remaining = TimeUnit.NANOSECONDS.toMillis(expires - System.nanoTime());

            if (remaining > 0) {
                wait(remaining);
            } else if (mIsMulticast) {
                // We don't throw a timeout exception when we are a multicast transaction
                break;
            } else {
                throw new TimeoutException();
            }
        }

        return getResponse();
    }

    @Override
    public synchronized Message getResponse()
            throws InterruptedException, HostLookupException, IOException, TimeoutException {
        while (mResponse == null && mException == null && mIsActive) {
            wait();
        }

        if (mIsCancelled) {
            throw new CancellationException(
                    "Transaction for " + mRequest.toShortString() + "was canceled");
        }

        if (mException != null) {
            if (mException instanceof TimeoutException) {
                throw (TimeoutException) mException;
            }
            if (mException instanceof HostLookupException) {
                throw (HostLookupException) mException;
            }
            if (mException instanceof IOException) {
                throw (IOException) mException;
            }
            if (mException instanceof CborRuntimeException) {
                throw (CborRuntimeException) mException;
            }
            throw new CborRuntimeException(mException);
        }

        return mResponse;
    }

    @Override
    public synchronized void onOutboundMessageGotReply(LocalEndpoint endpoint, Message msg) {
        if (!isActive()) {
            if (!mRequest.isMulticast() && !msg.isReset() && msg.getType() != Type.ACK) {
                if (DEBUG)
                    LOGGER.info(
                            "Got "
                                    + msg.toShortString()
                                    + " on inactive transaction, sending reset");
                endpoint.sendResponse(msg.createRstResponse());
            } else {
                if (DEBUG)
                    LOGGER.info(
                            "Got " + msg.toShortString() + " on inactive transaction, ignoring");
            }
            return;
        }

        if (!isMulticast()) {
            mLocalEndpoint = endpoint;
        }

        if (msg.isEmptyAck()) {
            mIsAcknowledged = true;
            mCallbacks.forEach((cb, exec) -> exec.execute(cb::onTransactionAcknowledged));

        } else {

            if (mIsObserving) {
                int prevObserve = 0;
                int nextObserve = 0;

                if (mResponse != null && mResponse.getOptionSet().hasObserve()) {
                    //noinspection ConstantConditions
                    prevObserve = mResponse.getOptionSet().getObserve();
                }

                if (msg.getOptionSet().hasObserve()) {
                    //noinspection ConstantConditions
                    nextObserve = msg.getOptionSet().getObserve();
                }

                // restart observation timer
                if (msg.getOptionSet().hasMaxAge()) {
                    //noinspection ConstantConditions
                    updateObservationRetryTimeout(msg.getOptionSet().getMaxAge());
                } else {
                    resetObservationRetryTimer();
                }

                // Verify that the messages are arriving in-order.
                if ((nextObserve > 0) && (prevObserve >= nextObserve)) {
                    // The observer count isn't sequentially increasing,
                    // so we ignore this message.
                    if (DEBUG && (prevObserve > nextObserve)) {
                        LOGGER.info(
                                "Dropping "
                                        + msg.toShortString()
                                        + " because observe count is < that of "
                                        + mResponse.toShortString());
                    }
                    resetObservationRetryTimer();
                    return;
                }
            }

            mResponse = msg;

            if (isFinishedAfterFirstResponse()) {
                mIsActive = false;
                mCallbacks.forEach(
                        (cb, exec) ->
                                exec.execute(
                                        () -> {
                                            cb.onTransactionResponse(endpoint, msg);
                                            cb.onTransactionFinished();
                                        }));

            } else {
                mCallbacks.forEach(
                        (cb, exec) -> exec.execute(() -> cb.onTransactionResponse(endpoint, msg)));
            }

            // Wake up any threads waiting on getResponse()
            notifyAll();
        }
    }

    @Override
    public synchronized void onOutboundMessageRetransmitTimeout() {
        if (isActive()) {
            if (!isMulticast()) {
                handleException(new TimeoutException());
            } else {
                mIsActive = false;

                mCallbacks.forEach((cb, exec) -> exec.execute(cb::onTransactionFinished));

                // Wake up any threads waiting on getResponse()
                notifyAll();
            }
        }
    }

    @Override
    public void onOutboundMessageGotIOException(IOException exception) {
        handleException(exception);
    }

    @Override
    public void onOutboundMessageGotRuntimeException(CoapRuntimeException exception) {
        handleException(exception);
    }

    public synchronized void handleException(Exception exception) {
        if (isActive()) {
            if (DEBUG) {
                LOGGER.info("Error while sending " + mRequest.toShortString() + ": " + exception);
            }

            mException = exception;

            if (mIsObserving) {
                mCallbacks.forEach(
                        (cb, exec) ->
                                exec.execute(
                                        () -> {
                                            cb.onTransactionException(exception);
                                        }));
            } else {
                mIsActive = false;
                mCallbacks.forEach(
                        (cb, exec) ->
                                exec.execute(
                                        () -> {
                                            cb.onTransactionException(exception);
                                            cb.onTransactionFinished();
                                        }));
            }
            notifyAll();
        } else {
            LOGGER.warning("Can't handle " + exception + " because transaction is inactive");

            if (exception instanceof RuntimeException) {
                throw new CoapRuntimeException(
                        "Transaction inactive, can't handle exception", exception);
            }
        }
    }
}
