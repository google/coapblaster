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

import java.net.SocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Logger;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Class used for sending CoAP requests and receiving responses to those requests. It is the
 * intended usage that, at a minimum, a new {@link Client} instance be used for each remote
 * endpoint.
 *
 * @see Server
 */
public final class Client {
    private static final boolean DEBUG = false;
    private static final Logger LOGGER = Logger.getLogger(Client.class.getCanonicalName());

    private final LinkedList<Transaction> mActiveTransactions = new LinkedList<>();
    private final LocalEndpointManager mLocalEndpointManager;
    private URI mUri;

    private ProxySelector mProxySelector = ProxySelector.neverProxy();

    private SocketAddress mRemoteSocketAddress;
    private LocalEndpoint mLocalEndpoint;

    private final boolean mLocalEndpointOverride;
    private final boolean mRemoteSocketAddressOverride;

    private SocketAddress mCachedRemoteSocketAddress;

    public Client(
            LocalEndpointManager manager,
            @Nullable URI uri,
            @Nullable LocalEndpoint localEndpoint,
            @Nullable SocketAddress remoteAddress) {

        mLocalEndpointManager = manager;

        mLocalEndpoint = localEndpoint;

        if (localEndpoint != null) {
            mLocalEndpointOverride = true;
            mUri = URI.create(mLocalEndpoint.getScheme() + "://localhost/");
        } else {
            mLocalEndpointOverride = false;
            mUri = URI.create("coap://localhost/");
        }

        mRemoteSocketAddress = remoteAddress;
        mRemoteSocketAddressOverride = (mRemoteSocketAddress != null);
        mCachedRemoteSocketAddress = remoteAddress;

        refreshLocalEndpoint();

        if (uri != null) {
            changeUri(uri);
        }
    }

    public Client(LocalEndpointManager manager, URI uri) {
        this(manager, uri, null, null);
    }

    public Client(LocalEndpointManager manager, String uri) {
        this(manager, URI.create(uri));
    }

    public LocalEndpointManager getLocalEndpointManager() {
        return mLocalEndpointManager;
    }

    /** {@hide} */
    public ScheduledExecutorService getExecutor() {
        return getLocalEndpointManager().getExecutor();
    }

    /** Returns the default URI used for new requests from {@link #newRequestBuilder()}. */
    public URI getUri() {
        return mUri;
    }

    private void refreshLocalEndpoint() {
        if (!mRemoteSocketAddressOverride) {
            mRemoteSocketAddress = null;
        }

        mCachedRemoteSocketAddress = null;

        if (!mLocalEndpointOverride) {
            URI proxyUri = mProxySelector.onGetProxyForUri(mUri);

            if (proxyUri != null) {
                mLocalEndpoint =
                        mLocalEndpointManager.getLocalEndpointForScheme(proxyUri.getScheme());
            } else {
                mLocalEndpoint = mLocalEndpointManager.getLocalEndpointForScheme(mUri.getScheme());
            }
        }

        if (mLocalEndpoint == null) {
            throw new AssertionError("mLocalEndpoint cannot be null here");
        }
    }

    /**
     * Changes the default URI by resolving {@code relativeUri} relative to the current default URI.
     */
    public void changeUri(URI relativeUri) {
        String scheme = mUri.getScheme();
        String host = mUri.getHost();
        int port = mUri.getPort();

        mUri = mUri.resolve(relativeUri);

        if (!Objects.equals(mUri.getScheme(), scheme)
                || !Objects.equals(mUri.getHost(), host)
                || !Objects.equals(mUri.getPort(), port)) {
            refreshLocalEndpoint();
        }
    }

    /**
     * Returns the current {@link ProxySelector} instance. The default proxy selector is {@link
     * ProxySelector#neverProxy()}.
     *
     * @see #setProxySelector(ProxySelector)
     */
    public ProxySelector getProxySelector() {
        return mProxySelector;
    }

    /**
     * Changes the {@link ProxySelector} instance to use with this {@link Client}.
     *
     * @param proxySelector the {@link ProxySelector} instance to use.
     */
    public void setProxySelector(ProxySelector proxySelector) {
        mProxySelector = proxySelector;
        refreshLocalEndpoint();
    }

    /**
     * Returns the {@link SocketAddress} of the remote endpoint, or {@code null} if it is not yet
     * known.
     */
    public @Nullable SocketAddress getRemoteSocketAddress() {
        return mRemoteSocketAddress != null ? mRemoteSocketAddress : mCachedRemoteSocketAddress;
    }

    /**
     * Returns the {@link SocketAddress} of the local endpoint, or {@code null} if it is not yet
     * known.
     */
    public @Nullable SocketAddress getLocalSocketAddress() {
        return mLocalEndpoint.getLocalSocketAddress();
    }

    /**
     * Returns the {@link LocalEndpoint} that was configured for this client or the most recently
     * used {@link LocalEndpoint} is none was specified.
     */
    public LocalEndpoint getLocalEndpoint() {
        return Objects.requireNonNull(mLocalEndpoint);
    }

    /**
     * Returns a new instance of a {@link RequestBuilder} for constructing and sending a new CoAP
     * request.
     */
    public RequestBuilder newRequestBuilder() {
        return new RequestBuilder(this);
    }

    void registerTransaction(Transaction transaction) {
        final Transaction.Callback cb =
                new Transaction.Callback() {
                    @Override
                    public void onTransactionResponse(LocalEndpoint endpoint, Message response) {
                        // Cache the remote socket address so we don't have
                        // to do a lookup in the future.
                        mCachedRemoteSocketAddress = response.getRemoteSocketAddress();

                        if (!mLocalEndpointOverride) {
                            mLocalEndpoint = endpoint;
                        }
                    }

                    @Override
                    public void onTransactionCancelled() {
                        if (DEBUG) LOGGER.info(transaction + " was cancelled");
                    }

                    @Override
                    public void onTransactionException(Exception exception) {
                        // Always clear the cached remote socket address if
                        // there was any exception.
                        mCachedRemoteSocketAddress = null;
                    }

                    @Override
                    public void onTransactionFinished() {
                        synchronized (mActiveTransactions) {
                            mActiveTransactions.remove(transaction);
                        }
                        if (DEBUG) LOGGER.info(transaction + " has finished");
                    }
                };

        // We use "Runnable::run" here to ensure that
        // the above callbacks get fired ASAP on whatever
        // thread the event comes in on.
        transaction.registerCallback(Runnable::run, cb);

        synchronized (mActiveTransactions) {
            mActiveTransactions.add(transaction);
        }
    }

    /** Retrieves a copy of the current list of active {@link Transaction} objects. */
    public List<Transaction> getActiveTransactions() {
        synchronized (mActiveTransactions) {
            return new ArrayList<>(mActiveTransactions);
        }
    }

    /** Cancels all outstanding transactions. */
    public void cancelAllTransactions() {
        getActiveTransactions().forEach(Transaction::cancel);
    }

    /**
     * Send a CoAP ping to the endpoint identified by this Client's current URI.
     *
     * @return a {@link Transaction} object tracking the ping
     */
    public Transaction ping() {
        return newRequestBuilder().setConfirmable(true).setCode(Code.EMPTY).clearOptions().send();
    }
}
