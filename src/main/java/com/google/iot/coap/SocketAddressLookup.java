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
import com.google.common.util.concurrent.ListenableFutureTask;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.util.concurrent.*;
import org.checkerframework.checker.nullness.qual.Nullable;

abstract class SocketAddressLookup {
    private Executor mExecutor;

    public abstract ListenableFuture<SocketAddress> lookup(String host, @Nullable Integer port);

    public void setExecutor(Executor executor) {
        mExecutor = executor;
    }

    Executor getExecutor() {
        if (mExecutor == null) {
            mExecutor = Executors.newScheduledThreadPool(1);
        }
        return mExecutor;
    }

    public static SocketAddressLookup createInetLookup(int defaultPort) {
        return new InetLookup(defaultPort);
    }

    public static SocketAddressLookup createInetLookup() {
        return new InetLookup();
    }

    public static SocketAddressLookup createNullLookup() {
        return new NullLookup();
    }

    private static class InetLookup extends SocketAddressLookup {
        private final int mDefaultPort;

        InetLookup(int defaultPort) {
            mDefaultPort = defaultPort;
        }

        InetLookup() {
            this(0);
        }

        @Override
        public ListenableFuture<SocketAddress> lookup(String host, @Nullable Integer port) {
            final ListenableFutureTask<SocketAddress> ret =
                    ListenableFutureTask.create(
                            () -> {
                                if (Coap.ALL_NODES_MCAST_HOSTNAME.equals(host)) {
                                    InetSocketAddress tempSockAddr =
                                            new InetSocketAddress(
                                                    Coap.ALL_NODES_MCAST_IP6_LINK_LOCAL, 0);
                                    InetAddress addr;

                                    try {
                                        addr =
                                                InetAddress.getByAddress(
                                                        host,
                                                        tempSockAddr.getAddress().getAddress());
                                    } catch (UnknownHostException x) {
                                        // this will not occur, but if it somehow does, make it
                                        // fatal.
                                        throw new AssertionError(x);
                                    }

                                    return new InetSocketAddress(
                                            addr, port == null ? mDefaultPort : port);

                                } else {
                                    InetSocketAddress addr =
                                            new InetSocketAddress(
                                                    host, port == null ? mDefaultPort : port);
                                    if (addr.isUnresolved()) {
                                        throw new HostLookupException(
                                                "Unable to resolve \"" + host + "\"");
                                    }
                                    return addr;
                                }
                            });

            getExecutor().execute(ret);

            return ret;
        }
    }

    private static class NullLookup extends SocketAddressLookup {

        @Override
        public ListenableFuture<SocketAddress> lookup(String host, @Nullable Integer port) {
            final ListenableFutureTask<SocketAddress> ret =
                    ListenableFutureTask.create(
                            () ->
                                    NullSocketAddress.create(
                                            host,
                                            port != null ? port : 0,
                                            Coap.ALL_NODES_MCAST_HOSTNAME.equals(host)));

            getExecutor().execute(ret);

            return ret;
        }
    }
}
