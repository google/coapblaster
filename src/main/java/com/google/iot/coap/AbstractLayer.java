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

import java.util.concurrent.ScheduledExecutorService;
import org.checkerframework.checker.nullness.qual.Nullable;

abstract class AbstractLayer implements Layer {
    private ScheduledExecutorService mExecutor;
    private Layer mUpperLayer;
    private Layer mLowerLayer;

    /** DO NOT SYNCHRONIZE THIS METHOD, NOR CALL SUPER METHOD WHILE SYNCHRONIZED! */
    @Override
    public void close() {
        // Subclass optionally overrides this method to permanently deactivate object.
    }

    /** DO NOT SYNCHRONIZE THIS METHOD, NOR CALL SUPER METHOD WHILE SYNCHRONIZED! */
    @Override
    public void cleanup() {
        // Subclass optionally overrides this method to do periodic housekeeping.
    }

    @Override
    public abstract int getSortOrder();

    @Override
    public void setScheduledExecutorService(ScheduledExecutorService executor) {
        mExecutor = executor;
    }

    ScheduledExecutorService getScheduledExecutorService() {
        return mExecutor;
    }

    @Override
    public void setUpperLayer(@Nullable Layer upperLayer) {
        mUpperLayer = upperLayer;
    }

    @Override
    public void setLowerLayer(@Nullable Layer lowerLayer) {
        mLowerLayer = lowerLayer;
    }

    /** DO NOT SYNCHRONIZE THIS METHOD, NOR CALL SUPER METHOD WHILE SYNCHRONIZED! */
    @Override
    public void handleInboundRequest(InboundRequest inboundRequest) {
        mUpperLayer.handleInboundRequest(inboundRequest);
    }

    /** DO NOT SYNCHRONIZE THIS METHOD, NOR CALL SUPER METHOD WHILE SYNCHRONIZED! */
    @Override
    public void handleInboundResponse(
            LocalEndpoint localEndpoint,
            Message msg,
            OutboundMessageHandler outboundMessageHandler) {
        try {
            mUpperLayer.handleInboundResponse(localEndpoint, msg, outboundMessageHandler);
        } catch (CoapRuntimeException x) {
            outboundMessageHandler.onOutboundMessageGotRuntimeException(x);
        }
    }

    /** DO NOT SYNCHRONIZE THIS METHOD, NOR CALL SUPER METHOD WHILE SYNCHRONIZED! */
    @Override
    public void handleOutboundRequest(
            LocalEndpoint localEndpoint,
            Message msg,
            @Nullable OutboundMessageHandler outboundMessageHandler) {
        try {
            mLowerLayer.handleOutboundRequest(localEndpoint, msg, outboundMessageHandler);
        } catch (CoapRuntimeException x) {
            if (outboundMessageHandler != null) {
                outboundMessageHandler.onOutboundMessageGotRuntimeException(x);
            }
        }
    }

    /** DO NOT SYNCHRONIZE THIS METHOD, NOR CALL SUPER METHOD WHILE SYNCHRONIZED! */
    @Override
    public void handleOutboundResponse(LocalEndpoint localEndpoint, Message msg) {
        mLowerLayer.handleOutboundResponse(localEndpoint, msg);
    }
}
