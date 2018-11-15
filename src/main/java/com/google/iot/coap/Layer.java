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
import java.util.concurrent.ScheduledExecutorService;
import org.checkerframework.checker.nullness.qual.Nullable;

interface Layer extends Closeable {

    class Comparator implements java.util.Comparator<Layer> {
        private static final Layer.Comparator sSingleton = new Layer.Comparator();

        public static Layer.Comparator get() {
            return sSingleton;
        }

        @Override
        public int compare(Layer lhs, Layer rhs) {
            return Integer.compare(lhs.getSortOrder(), rhs.getSortOrder());
        }
    }

    @Override
    void close();

    /**
     * Cleans up any state that this layer may have accumulated. DO NOT SYNCHRONIZE THIS METHOD, NOR
     * CALL SUPER METHOD WHILE SYNCHRONIZED!
     */
    void cleanup();

    /** Returns the sort order index for this layer. */
    int getSortOrder();

    void setScheduledExecutorService(ScheduledExecutorService executor);

    void setUpperLayer(@Nullable Layer upperLayer);

    void setLowerLayer(@Nullable Layer lowerLayer);

    /** {@hide} DO NOT SYNCHRONIZE THIS METHOD, NOR CALL SUPER METHOD WHILE SYNCHRONIZED! */
    void handleInboundRequest(InboundRequest inboundRequest);

    /** {@hide} DO NOT SYNCHRONIZE THIS METHOD, NOR CALL SUPER METHOD WHILE SYNCHRONIZED! */
    void handleInboundResponse(
            LocalEndpoint localEndpoint,
            Message msg,
            OutboundMessageHandler outboundMessageHandler);

    /** {@hide} DO NOT SYNCHRONIZE THIS METHOD, NOR CALL SUPER METHOD WHILE SYNCHRONIZED! */
    void handleOutboundRequest(
            LocalEndpoint localEndpoint,
            Message msg,
            @Nullable OutboundMessageHandler outboundMessageHandler);

    /** {@hide} DO NOT SYNCHRONIZE THIS METHOD, NOR CALL SUPER METHOD WHILE SYNCHRONIZED! */
    void handleOutboundResponse(LocalEndpoint localEndpoint, Message msg);
}
