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

import com.google.common.collect.Sets;
import java.net.URI;
import java.util.Set;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Interface for determining if and when a proxy should be used on a {@link Client}.
 *
 * @see Client#setProxySelector(ProxySelector)
 * @see Client#getProxySelector()
 */
@SuppressWarnings("unused")
public interface ProxySelector {
    /**
     * Determines if a proxy should be used for the given {@link URI} and, if so, which proxy to
     * use.
     *
     * @return the {@link URI} of the proxy to use, or {@code null} if no proxy is needed
     */
    @Nullable
    URI onGetProxyForUri(URI res);

    /**
     * Returns a {@link ProxySelector} instance which <b>never</b> indicates that a proxy should be
     * used.
     */
    static ProxySelector neverProxy() {
        return (res) -> null;
    }

    /**
     * Returns a {@link ProxySelector} instance which <b>always</b> indicates that the given proxy
     * should be used.
     */
    static ProxySelector alwaysProxy(URI proxyUri) {
        return (res) -> proxyUri;
    }

    /**
     * Returns a {@link ProxySelector} instance which will only indicate that the given proxy should
     * be used if the {@link URI} scheme isn't specified in {@code schemes}.
     */
    static ProxySelector proxyUnless(URI proxyUri, String... schemes) {
        final Set<String> mSchemes = Sets.newHashSet(schemes);
        return (res) -> {
            String scheme = res.getScheme();
            if (mSchemes.contains(scheme)) {
                return null;
            }
            return proxyUri;
        };
    }

    /**
     * Returns a {@link ProxySelector} instance which will only indicate that the given proxy should
     * be used if the {@link URI} scheme isn't <code>coap:</code>, <code>coaps:</code>, <code>loop:
     * </code>, or <code>null:</code>.
     */
    static ProxySelector proxyNonCoap(URI proxyUri) {
        return proxyUnless(
                proxyUri,
                Coap.SCHEME_UDP,
                Coap.SCHEME_DTLS,
                Coap.SCHEME_LOOPBACK,
                Coap.SCHEME_NULL);
    }
}
