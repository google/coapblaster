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

import java.io.UnsupportedEncodingException;
import java.net.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Package-private static utility methods. */
class Utils {
    static int decodeInteger(byte[] bytes) {
        int ret = 0;
        for (byte aByte : bytes) {
            if ((ret & 0xFF000000) != 0) {
                break;
            }
            ret <<= 8;
            ret += aByte & 0xFF;
        }
        return ret;
    }

    static byte[] encodeInteger(int i) {
        int len;

        if (i == 0) {
            return new byte[0];
        } else if (i == (i & 0xFF)) {
            len = 0;
        } else if (i == (i & 0xFFFF)) {
            len = 1;
        } else if (i == (i & 0xFFFFFF)) {
            len = 2;
        } else {
            len = 3;
        }

        byte[] ret = new byte[len + 1];

        for (; len >= 0; len--) {
            ret[len] = (byte) (i & 0xff);
            i >>= 8;
        }
        return ret;
    }

    static boolean isSocketAddressMulticast(SocketAddress socketAddress) {
        if (socketAddress instanceof InetSocketAddress) {
            InetSocketAddress inetsaddr = (InetSocketAddress) socketAddress;
            InetAddress addr = inetsaddr.getAddress();
            return addr != null && addr.isMulticastAddress();
        }
        if (socketAddress instanceof NullSocketAddress) {
            NullSocketAddress saddr = (NullSocketAddress) socketAddress;
            return saddr.isMulticast();
        }
        return false;
    }

    static URI createUriFromSocketAddress(SocketAddress socketAddress, String scheme) {
        if (socketAddress instanceof InetSocketAddress) {
            InetSocketAddress inetsaddr = (InetSocketAddress) socketAddress;
            InetAddress addr = inetsaddr.getAddress();

            String addrString = addr.toString();

            // Next two lines removes the unfortunate slash at the start.
            // TODO: There must be a better way to do this.
            String[] hack = addrString.split("/");
            addrString = hack[hack.length - 1];

            if (addr instanceof Inet6Address) {
                // IPv6 addresses need to be boxed.
                addrString = "[" + addrString + "]";
            }

            return URI.create(scheme + "://" + addrString + ":" + inetsaddr.getPort() + "/");
        }
        return URI.create(scheme + "://" + socketAddress + "/");
    }

    static String uriEscapeString(String unescaped) {
        // TODO: URLEncoder is for "application/x-www-form-urlencoded" instead of real URI escaping!
        try {
            return URLEncoder.encode(unescaped, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            // This is fatal.
            throw new RuntimeException(e);
        }
    }

    static String uriUnescapeString(String escaped) {
        // TODO: URLDecoder is for "application/x-www-form-urlencoded" instead of real URI
        // unescaping!
        try {
            return URLDecoder.decode(escaped, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            // This is fatal.
            throw new RuntimeException(e);
        }
    }

    static ScheduledExecutorService createSafeExecutor() {
        return new ScheduledThreadPoolExecutor(2) {
            @Override
            protected void afterExecute(Runnable r, @Nullable Throwable t) {
                super.afterExecute(r, t);

                if (t != null) {
                    Thread.getDefaultUncaughtExceptionHandler()
                            .uncaughtException(Thread.currentThread(), t);
                }
            }
        };
    }

    private static ScheduledExecutorService sSafeExecutor = null;

    /** Returns a singleton {@link ScheduledExecutorService}. */
    static synchronized ScheduledExecutorService getSafeExecutor() {
        if (sSafeExecutor == null || sSafeExecutor.isShutdown()) {
            sSafeExecutor = createSafeExecutor();
        }

        return sSafeExecutor;
    }
}
