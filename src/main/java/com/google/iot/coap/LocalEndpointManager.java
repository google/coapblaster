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

import com.google.common.collect.Maps;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.Closeable;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.MulticastSocket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Logger;

import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Manager class for {@link LocalEndpoint} instances. */
public class LocalEndpointManager implements Closeable {
    private static final boolean DEBUG = false;
    private static final Logger LOGGER =
            Logger.getLogger(LocalEndpointManager.class.getCanonicalName());

    private ListeningScheduledExecutorService mExecutor = null;
    private final Map<String, LocalEndpoint> mSchemeEndpointMap = Maps.newConcurrentMap();

    private final SocketAddressLookup mInetLookup = SocketAddressLookup.createInetLookup();
    private final SocketAddressLookup mNullLookup = SocketAddressLookup.createNullLookup();

    private BehaviorContext mBehaviorContext = BehaviorContext.standard();

    private Interceptor mDefaultInterceptor = null;

    public LocalEndpointManager(ListeningScheduledExecutorService executor) {
        mExecutor = executor;
        mInetLookup.setExecutor(getExecutor());
        mNullLookup.setExecutor(getExecutor());
    }

    public LocalEndpointManager() {
        this(MoreExecutors.listeningDecorator(Utils.getSafeExecutor()));
    }

    public BehaviorContext getDefaultBehaviorContext() {
        return mBehaviorContext;
    }

    public void setDefaultBehaviorContext(BehaviorContext behaviorContext) {
        mBehaviorContext = behaviorContext;
    }

    public void setDefaultInterceptor(Interceptor interceptor) {
        mDefaultInterceptor = interceptor;
    }

    public Interceptor getDefaultInterceptor() {
        return mDefaultInterceptor;
    }

    public int getDefaultPortForScheme(String scheme) {
        switch (scheme) {
            case "http":
            case Coap.SCHEME_WS:
                return 80;

            case "https":
            case Coap.SCHEME_WS_TLS:
                return 443;

            case Coap.SCHEME_DTLS:
            case Coap.SCHEME_TLS:
                return Coap.DEFAULT_PORT_SECURE;

            default:
            case Coap.SCHEME_TCP:
            case Coap.SCHEME_UDP:
                return Coap.DEFAULT_PORT_NOSEC;
        }
    }

    /**
     * Returns the default {@link ListeningScheduledExecutorService} associated with this context instance.
     * This can optionally be used by other objects associated with this context instance.
     */
    public ListeningScheduledExecutorService getExecutor() {
        return mExecutor;
    }

    /** @hide Convert a scheme, host, and port into an appropriate SocketAddress. */
    public ListenableFuture<SocketAddress> lookupSocketAddress(
            String scheme, String host, @Nullable Integer port) {
        switch (scheme) {
            case Coap.SCHEME_NULL:
            case Coap.SCHEME_LOOPBACK:
                if (port == null) {
                    port = Coap.DEFAULT_PORT_NOSEC;
                }
                return mNullLookup.lookup(host, port);

            case Coap.SCHEME_DTLS:
                if (port == null) {
                    port = Coap.DEFAULT_PORT_SECURE;
                }
                return mInetLookup.lookup(host, port);

            case Coap.SCHEME_UDP:
            default:
                if (port == null) {
                    port = Coap.DEFAULT_PORT_NOSEC;
                }
                return mInetLookup.lookup(host, port);
        }
    }

    public boolean supportsScheme(String scheme) {
        switch (scheme) {
            case Coap.SCHEME_LOOPBACK:
            case Coap.SCHEME_NULL:
            case Coap.SCHEME_UDP:
                return true;

            default:
                return mSchemeEndpointMap.containsKey(scheme);
        }
    }

    /**
     * Returns a {@link LocalEndpoint} for the given scheme that is suitable for sending outbound
     * requests (ie, for use with {@link Client}). This method should not be used to obtain
     * LocalEndpoints for use with {@link Server} since the binding port can be unpredictable.
     *
     * <p>Supported schemes include:
     *
     * <ul>
     *   <li><tt>null</tt>: "null" local endpoint (drops everything)
     *   <li><tt>loop</tt>/<tt>loopback</tt>: Loopback local endpoint (outbound packets are fed back
     *       into itself)
     *   <li><tt>coap</tt>: Standard UDP Coap local endpoint
     * </ul>
     *
     * <p>Additional scheme types are planned.
     *
     * @param scheme the scheme for the endpoint
     * @return a {@link LocalEndpoint} instance
     * @throws UnsupportedSchemeException if the given scheme is not supported
     */
    public LocalEndpoint getLocalEndpointForScheme(String scheme) throws UnsupportedSchemeException {
        LocalEndpoint ret = mSchemeEndpointMap.get(scheme);
        if (ret == null) {
            switch (scheme) {
                case Coap.SCHEME_LOOPBACK:
                    ret = new LocalEndpointLoopback(this);
                    break;
                case Coap.SCHEME_NULL:
                    ret = new LocalEndpointNull(this);
                    break;
                case Coap.SCHEME_UDP:
                    try {
                        try {
                            ret = new LocalEndpointCoap(this, new MulticastSocket());
                        } catch (IOException | SecurityException x) {
                            LOGGER.warning(
                                    "Unable to open multicast socket ("
                                            + x
                                            + "), trying unicast...");
                            ret = new LocalEndpointCoap(this, new DatagramSocket());
                        }
                    } catch (SocketException x) {
                        LOGGER.warning("Unable to open UDP socket (" + x + ")");
                        if (DEBUG) x.printStackTrace();
                        ret = null;
                    }
                    break;
                default:
                    break;
            }

            if (ret != null) {
                try {
                    ret.setInterceptor(getDefaultInterceptor());
                    ret.start();

                    mSchemeEndpointMap.put(scheme, ret);
                } catch (IOException x) {
                    throw new CoapRuntimeException(x);
                }
            }
        }

        if (ret == null) {
            throw new UnsupportedSchemeException("Unsupported URI scheme \"" + scheme + "\"");
        }

        return ret;
    }

    /**
     * Closes all associated {@link LocalEndpoint} instances.
     *
     * @throws IOException if there was a problem closing any {@link LocalEndpoint}s
     */
    @Override
    public void close() throws IOException {
        IOException exception = null;

        synchronized (mSchemeEndpointMap) {
            for (Map.Entry<String, LocalEndpoint> entry : mSchemeEndpointMap.entrySet()) {
                try {
                    entry.getValue().close();
                } catch (IOException x) {
                    x.printStackTrace();
                    exception = x;
                }
            }

            mSchemeEndpointMap.clear();

            if (exception != null) {
                throw exception;
            }
        }
    }
}
