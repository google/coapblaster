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

/**
 * Organizational class containing various CoAP-related static constants, such as default port
 * numbers and URI schemes.
 */
public final class Coap {
    /** Default unsecured (UDP/TCP) CoAP port number. */
    public static final int DEFAULT_PORT_NOSEC = 5683;

    /** Default secured (DTLS/TLS) CoAP port number. */
    public static final int DEFAULT_PORT_SECURE = 5684;

    /** URI scheme for standard CoAP over UDP with no security. */
    public static final String SCHEME_UDP = "coap";

    /** URI scheme for CoAP secured with DTLS. */
    public static final String SCHEME_DTLS = "coaps";

    /**
     * URI scheme for CoAP over TCP with no security.
     *
     * @see <a href="https://tools.ietf.org/html/rfc8323#section-8.1">RFC8323 Section 8.1</a>
     */
    public static final String SCHEME_TCP = "coap+tcp";

    /**
     * URI scheme for CoAP over TLS with no security.
     *
     * @see <a href="https://tools.ietf.org/html/rfc8323#section-8.2">RFC8323 Section 8.2</a>
     */
    public static final String SCHEME_TLS = "coaps+tcp";

    /**
     * URI scheme for CoAP over WebSockets with no security.
     *
     * @see <a href="https://tools.ietf.org/html/rfc8323#section-8.3">RFC8323 Section 8.3</a>
     */
    public static final String SCHEME_WS = "coap+ws";

    /**
     * URI scheme for CoAP over WebSockets secured with TLS.
     *
     * @see <a href="https://tools.ietf.org/html/rfc8323#section-8.4">RFC8323 Section 8.4</a>
     */
    public static final String SCHEME_WS_TLS = "coaps+ws";

    /** CoAP "all-nodes" IPv6 link-local multicast address. */
    public static final String ALL_NODES_MCAST_IP6_LINK_LOCAL = "FF02:0:0:0:0:0:0:FD";

    /** CoAP "all-nodes" IPv6 realm-local multicast address. */
    public static final String ALL_NODES_MCAST_IP6_REALM_LOCAL = "FF03:0:0:0:0:0:0:FD";

    /** CoAP "all-nodes" IPv6 admin-local multicast address. */
    public static final String ALL_NODES_MCAST_IP6_ADMIN_LOCAL = "FF04:0:0:0:0:0:0:FD";

    /** CoAP "all-nodes" IPv6 site-local multicast address. */
    public static final String ALL_NODES_MCAST_IP6_SITE_LOCAL = "FF05:0:0:0:0:0:0:FD";

    /** CoAP "all-nodes" IPv4 multicast address. */
    public static final String ALL_NODES_MCAST_IP4 = "224.0.1.187";

    /** CoAP "all-nodes" hostname. */
    public static final String ALL_NODES_MCAST_HOSTNAME = "coap-all-nodes";

    /**
     * Internal URI scheme for Loopback interface.
     *
     * @see LocalEndpointLoopback
     */
    public static final String SCHEME_LOOPBACK = "loop";

    /**
     * Internal URI scheme for Null interface.
     *
     * @see LocalEndpointNull
     */
    public static final String SCHEME_NULL = "null";

    public static final int MAX_SINGLE_MESSAGE_LENGTH = 65535;

    // Prevent instantiation
    private Coap() {}
}
