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

import java.io.Closeable;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Class used for serving resource to other machines by receiving and handling CoAP requests.
 *
 * <p>This class allows you to set up a CoAP server that can respond to CoAP requests. Requests are
 * handled by a {@link InboundRequestHandler}, which the caller is responsible for creating and
 * registering with one or more Server instances.
 *
 * @see Client
 * @see Resource
 * @see WellKnownCoreHandler
 */
public final class Server implements Closeable {

    private final LocalEndpointManager mLocalEndpointManager;
    private final LinkedList<LocalEndpoint> mLocalEndpoints = new LinkedList<>();
    private InboundRequestHandler mInboundRequestHandler = null;
    private InboundRequestHandler mProxyHandler = null;
    private boolean mIsRunning = true;
    private ScheduledExecutorService mExecutor = null;

    private final InboundRequestHandler mInternalRequestHandler =
            new InboundRequestHandler() {
                @Override
                public void onInboundRequest(InboundRequest inboundRequest) {
                    final Message request = inboundRequest.getMessage();
                    final InboundRequestHandler rh;

                    if (request.getOptionSet().hasProxyUri()
                            || request.getOptionSet().hasProxyScheme()) {
                        rh = mProxyHandler;
                        if (rh == null) {
                            inboundRequest.sendSimpleResponse(Code.RESPONSE_PROXYING_NOT_SUPPORTED);
                        }
                    } else {
                        rh = mInboundRequestHandler;
                        if (rh == null) {
                            inboundRequest.sendSimpleResponse(Code.RESPONSE_NOT_IMPLEMENTED);
                        }
                    }

                    if (rh != null) {
                        mInboundRequestHandler.onInboundRequest(inboundRequest);
                    }
                }

                @Override
                public void onInboundRequestCheck(InboundRequest inboundRequest) {
                    final Message request = inboundRequest.getMessage();
                    final InboundRequestHandler rh;

                    if (request.getOptionSet().hasProxyUri()
                            || request.getOptionSet().hasProxyScheme()) {
                        rh = mProxyHandler;
                        if (rh == null) {
                            inboundRequest.sendSimpleResponse(Code.RESPONSE_PROXYING_NOT_SUPPORTED);
                        }
                    } else {
                        rh = mInboundRequestHandler;
                        if (rh == null) {
                            inboundRequest.sendSimpleResponse(Code.RESPONSE_NOT_IMPLEMENTED);
                        }
                    }

                    if (rh != null) {
                        mInboundRequestHandler.onInboundRequestCheck(inboundRequest);
                    }
                }
            };

    public Server(LocalEndpointManager localEndpointManager) {
        mLocalEndpointManager = Objects.requireNonNull(localEndpointManager);
    }

    public ScheduledExecutorService getExecutor() {
        if (mExecutor == null) {
            mExecutor = mLocalEndpointManager.getExecutor();
        }
        return mExecutor;
    }

    /** @hide */
    public void setScheduledExecutorService(ScheduledExecutorService executor) {
        mExecutor = executor;
    }

    public synchronized void addLocalEndpoint(LocalEndpoint endpoint) throws IOException {
        if (mLocalEndpoints.contains(endpoint)) {
            return;
        }

        if (endpoint.getRequestHandler() != null) {
            throw new IllegalArgumentException("Local endpoint already has request handler");
        }

        mLocalEndpoints.add(endpoint);
        endpoint.setRequestHandler(mInternalRequestHandler);

        if (mIsRunning) {
            endpoint.start();
        }
    }

    public synchronized void removeLocalEndpoint(LocalEndpoint endpoint) {
        if (mLocalEndpoints.contains(endpoint)) {
            mLocalEndpoints.remove(endpoint);
            endpoint.setRequestHandler(null);
        }
    }

    public synchronized Collection<LocalEndpoint> getLocalEndpoints() {
        return new LinkedList<>(mLocalEndpoints);
    }

    public synchronized void setRequestHandler(InboundRequestHandler rh) {
        mInboundRequestHandler = rh;
    }

    public synchronized void setProxyHandler(InboundRequestHandler rh) {
        mProxyHandler = rh;
    }

    private IOException mDeferredIOException = null;

    public synchronized void start() throws IOException {
        mDeferredIOException = null;
        mLocalEndpoints.forEach(
                (x) -> {
                    try {
                        x.start();
                    } catch (IOException e) {
                        mDeferredIOException = e;
                    }
                });
        if (mDeferredIOException != null) {
            throw mDeferredIOException;
        }
        mIsRunning = true;
    }

    public synchronized void stop() {
        mIsRunning = false;
        mLocalEndpoints.forEach(LocalEndpoint::stop);
    }

    @Override
    public void close() {
        mLocalEndpoints.forEach((x) -> x.setRequestHandler(null));
    }
}
