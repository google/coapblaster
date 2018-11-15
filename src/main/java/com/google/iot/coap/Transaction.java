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
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeoutException;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Interface representing an outbound transaction and keeping track of its pending response.
 *
 * <p>Transaction objects keep track of an outbound request and the associated inbound response.
 * Usually this is a one-to-one relationship, but there are a few cases where transactions can have
 * more than one inbound response, such as multicast requests or observing requests.
 *
 * <p>Transactions could be thought of as similar to a special {@link
 * com.google.common.util.concurrent.ListenableFuture} returning a response {@link Message}, with a
 * fair amount of additional behaviors for good measure.
 *
 * @see Client
 * @see RequestBuilder
 * @see RequestBuilder#prepare()
 * @see RequestBuilder#send()
 */
public interface Transaction {
    /**
     * Callback class for handling asynchronous notifications about the status of the transaction.
     */
    @SuppressWarnings({"EmptyMethod", "unused"})
    abstract class Callback {
        /**
         * Called when the transaction has received a fully constructed response. This may be called
         * by the transaction multiple times if {@link #isFinishedAfterFirstResponse()} returns
         * <code>false</code>.
         */
        public void onTransactionResponse(LocalEndpoint endpoint, Message response) {}

        /**
         * Called incrementally for each received block. This is only called when a block transfer
         * is taking place. This can be useful for displaying partial results as they are received.
         * After all blocks of the response have been received, {@link
         * #onTransactionResponse(LocalEndpoint, Message)} will be called with the full
         * reconstructed message.
         */
        public void onTransactionResponseBlock(LocalEndpoint endpoint, Message response) {}

        /**
         * Called if there is a checked exception that has occurred which has prevented either the
         * request from being sent or the response from being received. Such a notification will
         * terminate the transaction, so the subsequent notification from this transaction will
         * always be {@link #onTransactionFinished()}.
         */
        public void onTransactionException(Exception exception) {}

        /** Called if the transaction was canceled by calling {@link #cancel()}. */
        public void onTransactionCancelled() {}

        /** Called if the request was acknowledged, but a response is still pending. */
        public void onTransactionAcknowledged() {}

        /** Called once the transaction has finished and become inactive. */
        public void onTransactionFinished() {}
    }

    /**
     * Sends the request, making the transaction active. This method will restart an inactive
     * transaction, unless it was cancelled by calling {@link #cancel()}.
     *
     * <p>This method isn't usually called because the method {@link RequestBuilder#send()} will
     * call it automatically. However, if you finish building the request using {@link
     * RequestBuilder#prepare()} instead of {@link RequestBuilder#send()}, then you will need to
     * call this method in order to actually send the request.
     *
     * @throws CancellationException if this transaction has been cancelled.
     */
    void restart();

    /**
     * Indicates if this transaction has been invalidated.
     *
     * @return true if the transaction has been cancelled, false otherwise.
     * @see #cancel()
     */
    boolean isCancelled();

    /**
     * Indicates if the request has been acknowledged by the destination.
     *
     * @return true if an acknowledgement has been received, false otherwise. Will always return
     *     true once the first response has been received.
     */
    boolean isAcknowledged();

    /**
     * Indicates if the transaction is active or not. An active transaction is one that is either in
     * the process of transmitting the request or is still actively waiting for a response. Most
     * transactions become inactive after receiving a a single non-empty response, but multicast and
     * observing transactions will generally remain active until cancelled.
     *
     * @return true if the transaction is active, false otherwise.
     */
    boolean isActive();

    /**
     * Indicates if the transaction is considered finished after receiving the first response. This
     * will be the case for observing transactions and multicast transactions. Note that an
     * acknowledgement packet is not considered a response.
     *
     * @return true if the transaction is finished after the first response, false otherwise.
     */
    boolean isFinishedAfterFirstResponse();

    /**
     * Indicates if this is a multicast transaction, and thus will expect multiple replies from
     * multiple hosts.
     *
     * @return true if the transaction is multicast, false otherwise
     */
    boolean isMulticast();

    /**
     * Indicates if this is an observing transaction, and thus will expect multiple replies from a
     * single host.
     *
     * @return true if the transaction is observing, false otherwise
     */
    boolean isObserving();

    /**
     * {@hide} Cancels this transaction without sending an observation cancel if the underlying
     * request is an observation. Any pending or later calls to {@link #getResponse} will throw a
     * {@link CancellationException}. Cancelling a transaction will permanently invalidate it: it
     * cannot be resumed by calling {@link #restart()}.
     *
     * <p>This method is broken out separately from {@link #cancel} in order to enable unit tests to
     * effectively test implicit observation cancellation.
     *
     * <p>If the transaction is not active, calling this method does nothing.
     */
    void cancelWithoutUnobserve();

    /**
     * Cancels and invalidates this transaction. Any pending or later calls to {@link #getResponse}
     * will throw a {@link CancellationException}. Cancelling a transaction will permanently
     * invalidate it: it cannot be resumed by calling {@link #restart()}.
     *
     * <p>If the transaction is not active, calling this method does nothing.
     */
    /* TODO: This the behavior of Transaction#cancel() is inconsistent,
     *       especially with respect to the previous paragraph. For example,
     *       if canceling invalidates the transaction but finishing normally
     *       doesn't, then why shouldn't calling cancel() after the transaction
     *       is finished cause invalidation? This needs to be explored.
     */
    void cancel();

    /**
     * Returns the {@link LocalEndpoint} instance associated with this transaction. This may change
     * to a more specific LocalEndpoint once a response has been received.
     *
     * @return the {@link LocalEndpoint} instance associated with this transaction
     */
    LocalEndpoint getLocalEndpoint();

    /**
     * Accessor for the underlying request message associated with this transaction.
     *
     * @return the underlying request message associated with this transaction.
     */
    Message getRequest();

    /**
     * Accessor for the response, with timeout. This method will block until a response has been
     * received or until some other condition has occurred, indicated by the exception thrown.
     *
     * <p>If a response has already been received, the most recently received response is
     * immediately returned without blocking. If the underlying transaction has been cancelled or
     * stopped for a reason other than receiving a response message, an exception is immediately
     * thrown.
     *
     * @param timeout the maximum amount of time to block execution, in milliseconds.
     * @return the most recently received response message. If this is a multicast transaction that
     *     has timed out, the returned value will be null.
     * @throws InterruptedException if this thread has been interrupted.
     * @throws HostLookupException if the host name lookup failed.
     * @throws IOException if there was an underlying IOException
     * @throws TimeoutException if there was no response within the time frame described by <code>
     *     timeout</code>. This will not be thrown if this transaction is a multicast transaction.
     * @throws CancellationException if this transaction has been cancelled
     * @see #getResponse()
     */
    Message getResponse(long timeout)
            throws InterruptedException, HostLookupException, IOException, TimeoutException;

    /**
     * Accessor for the response. This method will block until a response has been received or until
     * some other condition has occurred, indicated by the exception thrown.
     *
     * <p>Although this method has no <code>timeout</code> parameter, it may still throw a {@link
     * TimeoutException} if this is not a multicast transaction and the method blocks for a time
     * period larger than given by {@link BehaviorContext#getCoapExchangeLifetimeMs}.
     *
     * <p>If a response has already been received, the most recently received response is
     * immediately returned without blocking. If the underlying transaction has been cancelled or
     * stopped for a reason other than receiving a response message, an exception is immediately
     * thrown.
     *
     * @return the most recently received response message. If this is a multicast transaction that
     *     has timed out, the returned value will be null.
     * @throws InterruptedException if this thread has been interrupted.
     * @throws HostLookupException if the host name lookup failed.
     * @throws IOException if there was an underlying IOException
     * @throws TimeoutException if there was no response within the time frame described by <code>
     *     timeout</code>. This will not be thrown if this transaction is a multicast transaction.
     * @throws CancellationException if this transaction has been cancelled
     * @see #getResponse(long)
     */
    Message getResponse()
            throws InterruptedException, HostLookupException, IOException, TimeoutException;

    /**
     * Registers a callback class with the transaction, to be executed with the given executor.
     *
     * <p>If this transaction is no longer active then the appropriate methods on the callback class
     * will be immediately scheduled for execution with the given executor.
     *
     * @param executor the executor object to use to call the methods on the given callback class
     * @param cb the callback class instance to register
     */
    void registerCallback(Executor executor, Callback cb);

    /**
     * {@hide} Registers a callback class with the transaction.
     *
     * <p>If this transaction is no longer active then the appropriate methods on the callback class
     * will be immediately scheduled for execution using a default executor. The specific executor
     * used may come from {@link LocalEndpointManager#getExecutor()} or {@link
     * Client#getExecutor()}, depending on the context of the transaction's creation.
     *
     * @param cb the callback class instance to register
     */
    void registerCallback(Callback cb);

    /**
     * Unregisters a previously registered callback class from this transaction. If the given
     * callback instance was not previously registered or is <code>null</code>, then this method
     * does nothing.
     *
     * @param cb the callback class instance to unregister
     */
    void unregisterCallback(@Nullable Callback cb);
}
