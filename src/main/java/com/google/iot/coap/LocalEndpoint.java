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
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.net.URI;
import java.util.concurrent.ScheduledExecutorService;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Interface representing a local CoAP endpoint, typically associated with an open port on this
 * machine.
 *
 * <p>End developers should not typically need to use this class directly: they should use the
 * {@link Client} and {@link Server} classes instead.
 *
 * @see LocalEndpointCoap
 * @see LocalEndpointLoopback
 * @see LocalEndpointNull
 */
@SuppressWarnings("unused")
public interface LocalEndpoint extends Closeable {

    /**
     * Closes and permanently invalidates this instance.
     *
     * @throws IOException if there was a problem encountered while closing
     */
    @Override
    void close() throws IOException;

    /**
     * Starts the LocalEndpoint listening for inbound messages or sending outbound messages.
     *
     * @throws IOException if there was a problem from the underlying IO mechanism
     */
    void start() throws IOException;

    /**
     * Stops this LocalEndpoint from listening for inbound messages or sending outbound messages.
     * Call {@link #start()} to resume.
     */
    void stop();

    /** Returns true if this LocalEndpoint has been started, false otherwise. */
    boolean isRunning();

    /** Returns true if this LocalEndpoint supports sending and receiving multicast traffic. */
    boolean supportsMulticast();

    /**
     * Join the given multicast group.
     *
     * @param address {@link SocketAddress} containing the address of the multicast group (the port
     *     is ignored)
     * @param netIf the network interface on which to join the group, or <i>null</i> to defer to the
     *     underlying socket.
     * @throws IOException if there was a problem from the underlying IO mechanism.
     */
    void joinGroup(SocketAddress address, @Nullable NetworkInterface netIf) throws IOException;

    /**
     * Leave the given multicast group.
     *
     * @param address {@link SocketAddress} containing the address of the multicast group (the port
     *     is ignored)
     * @param netIf the network interface from which to leave the group, or <i>null</i> to defer to
     *     the underlying socket
     * @throws IOException if there was a problem from the underlying IO mechanism.
     */
    void leaveGroup(SocketAddress address, @Nullable NetworkInterface netIf) throws IOException;

    /**
     * Joins the default all-nodes multicast groups on the given network interface.
     *
     * @param netIf the {@link NetworkInterface} on which to join the group(s), or <i>null</i> to
     *     defer to the underlying socket
     * @return true if this operation was successful, false otherwise.
     */
    boolean attemptToJoinDefaultCoapGroups(@Nullable NetworkInterface netIf);

    /** Returns the local socket address to which this LocalEndpoint is bound. */
    @Nullable
    SocketAddress getLocalSocketAddress();

    /** {@hide} Hidden in this release. */
    void sendRequest(Message message, @Nullable OutboundMessageHandler outboundMessageHandler);

    /** {@hide} Hidden in this release. */
    void sendResponse(Message message);

    /** {@hide} Hidden in this release. */
    Token newToken(
            SocketAddress socketAddress, @Nullable OutboundMessageHandler outboundMessageHandler);

    /** {@hide} Hidden in this release. */
    int newMid(SocketAddress socketAddress);

    /** Returns the URI scheme for this {@link LocalEndpoint}. */
    String getScheme();

    /** Returns the default outbound port used for URIs with this {@link LocalEndpoint}. */
    int getDefaultPort();

    /**
     * Returns the {@link BehaviorContext} object defining the transmission parameters that this
     * local endpoint uses.
     */
    BehaviorContext getBehaviorContext();

    /** Changes the {@link BehaviorContext} object to use for the transmission parameters. */
    void setBehaviorContext(BehaviorContext behaviorContext);

    /** Sets the {@link InboundRequestHandler} to use for inbound requests. */
    void setRequestHandler(@Nullable InboundRequestHandler rh);

    /** Returns the current {@link InboundRequestHandler} being used for inbound requests. */
    @Nullable
    InboundRequestHandler getRequestHandler();

    /** Creates a {@link URI} from a {@link SocketAddress} for this endpoint. */
    URI createUriFromSocketAddress(SocketAddress socketAddress);

    /** {@hide} */
    void setExecutor(ListeningScheduledExecutorService executor);

    /** {@hide} */
    ListeningScheduledExecutorService getExecutor();

    /**
     * Ensures that the given future will be cancelled if it is still outstanding when
     * {@link #close()} is called.
     * @param futureToCancelAtClose The future to ensure is canceled at close.
     */
    void cancelAtClose(ListenableFuture<?> futureToCancelAtClose);

    void setInterceptor(Interceptor interceptor);
}
