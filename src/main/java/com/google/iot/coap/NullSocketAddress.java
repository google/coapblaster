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

import com.google.auto.value.AutoValue;
import java.net.SocketAddress;

/**
 * A subclass of SocketAddress used by {@link LocalEndpointNull} and {@link LocalEndpointLoopback}.
 */
@AutoValue
abstract class NullSocketAddress extends SocketAddress {

    public static NullSocketAddress create(String host, int port, boolean isMulticast) {
        return new AutoValue_NullSocketAddress(host, port, isMulticast);
    }

    public abstract String getHost();

    public abstract int getPort();

    public abstract Boolean isMulticast();

    @Override
    public String toString() {
        String sb = (getHost() != null ? getHost() : "(null)") + ":" + getPort();
        return sb;
    }
}
