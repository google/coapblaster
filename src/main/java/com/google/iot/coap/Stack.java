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
import java.util.LinkedList;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * A class for managing a stack of processing layers.
 *
 * <p><b>NOTE</b>: This class is will be significantly refactored at some point. It's original
 * design was inspired by the Stack class in Californium, but this design has proven itself to be
 * quite problematic in practice. It remains here as a private implementation detail.
 */
class Stack implements Closeable {
    private static final boolean DEBUG = false;
    private static final Logger LOGGER = Logger.getLogger(Stack.class.getCanonicalName());

    public interface Outbox {
        void onDeliverOutboundMessage(Message msg);
    }

    // Inbound packets enter through the bottom layer and exit through the top layer.
    // Outbound packets enter through the top layer and exit through the bottom layer.
    // Upper layers have higher sort orders. Lower layers have lower sort orders.

    private class TopLayer extends AbstractLayer {

        @Override
        public int getSortOrder() {
            return Integer.MAX_VALUE;
        }

        @Override
        public void setUpperLayer(@Nullable Layer upperLayer) {
            throw new AssertionError("Top layer cannot have upper layer");
        }

        @Override
        public void handleInboundResponse(
                LocalEndpoint localEndpoint,
                Message msg,
                OutboundMessageHandler outboundMessageHandler) {
            if (DEBUG)
                LOGGER.info(
                        "Inbound response has reached top of stack: "
                                + msg
                                + " ("
                                + outboundMessageHandler
                                + ")");
            outboundMessageHandler.onOutboundMessageGotReply(localEndpoint, msg);
        }

        @Override
        public void handleInboundRequest(InboundRequest inboundRequest) {
            dispatchInboundRequest(inboundRequest);
        }
    }

    private class BottomLayer extends AbstractLayer {

        @Override
        public int getSortOrder() {
            return Integer.MIN_VALUE;
        }

        @Override
        public void setLowerLayer(@Nullable Layer lowerLayer) {
            throw new AssertionError("Bottom layer cannot have lower layer");
        }

        @Override
        public void handleOutboundResponse(LocalEndpoint localEndpoint, Message msg) {
            if (DEBUG) LOGGER.info("Outbound response has reached bottom of stack: " + msg);
            mOutbox.onDeliverOutboundMessage(msg);
        }

        @Override
        public void handleOutboundRequest(
                LocalEndpoint localEndpoint,
                Message msg,
                @Nullable OutboundMessageHandler outboundMessageHandler) {
            if (DEBUG) LOGGER.info("Outbound request has reached bottom of stack: " + msg);

            try {
                mOutbox.onDeliverOutboundMessage(msg);
            } catch (CoapRuntimeException x) {
                if (outboundMessageHandler != null) {
                    outboundMessageHandler.onOutboundMessageGotRuntimeException(x);
                } else {
                    x.printStackTrace();
                }
            }
        }
    }

    private final LinkedList<Layer> mLayers = new LinkedList<>();
    private ScheduledExecutorService mExecutor = null;
    private final LocalEndpointManager mLocalEndpointManager;
    private final TopLayer mTopLayer = new TopLayer();
    private final BottomLayer mBottomLayer = new BottomLayer();
    private InboundRequestHandler mInboundRequestHandler = null;
    private final Outbox mOutbox;

    Stack(LocalEndpointManager manager, Outbox outbox) {
        mLocalEndpointManager = Objects.requireNonNull(manager);
        mOutbox = Objects.requireNonNull(outbox);
        mLayers.add(mBottomLayer);
        mLayers.add(mTopLayer);

        updateLayers();
    }

    public void addStandardLayers() {
        addLayer(new BlockLayer(mLocalEndpointManager));
    }

    public void addReliabilityLayers() {
        addLayer(new DeduplicationLayer(mLocalEndpointManager));
        addLayer(new RetransmissionLayer(mLocalEndpointManager));
    }

    public void clearLayers() {
        mLayers.forEach(Layer::close);
        mLayers.clear();
        mLayers.add(mBottomLayer);
        mLayers.add(mTopLayer);
        updateLayers();
    }

    @Override
    public void close() {
        mLayers.forEach(Layer::close);

        mTopLayer.setLowerLayer(null);
        mBottomLayer.setUpperLayer(null);
        mInboundRequestHandler = null;
    }

    void cleanup() {
        mLayers.forEach(Layer::cleanup);
    }

    private ScheduledExecutorService getScheduledExecutorService() {
        if (mExecutor == null) {
            mExecutor = mLocalEndpointManager.getExecutor();
        }
        return mExecutor;
    }

    public void setScheduledExecutorService(ScheduledExecutorService executor) {
        if (mExecutor != executor) {
            mExecutor = executor;
            updateLayers();
        }
    }

    private void updateLayers() {
        mLayers.sort(Layer.Comparator.get());
        Layer prev = null;
        for (Layer curr : mLayers) {
            if (prev != null) {
                curr.setLowerLayer(prev);
                prev.setUpperLayer(curr);
            }
            curr.setScheduledExecutorService(getScheduledExecutorService());
            prev = curr;
        }
    }

    public void addLayer(Layer layer) {
        mLayers.add(layer);
        updateLayers();
    }

    public void removeLayer(Layer layer) {
        mLayers.remove(layer);
        updateLayers();
    }

    /** {@hide} */
    public void handleOutboundResponse(LocalEndpoint localEndpoint, Message msg) {
        mTopLayer.handleOutboundResponse(localEndpoint, msg);
    }

    /** {@hide} */
    public void handleOutboundRequest(
            LocalEndpoint localEndpoint, Message msg, @Nullable OutboundMessageHandler handler) {
        mTopLayer.handleOutboundRequest(localEndpoint, msg, handler);
    }

    /** {@hide} */
    public void handleInboundRequest(InboundRequest inboundRequest) {
        mBottomLayer.handleInboundRequest(inboundRequest);
    }

    /** {@hide} */
    public void handleInboundResponse(
            LocalEndpoint localEndpoint, Message msg, OutboundMessageHandler handler) {
        mBottomLayer.handleInboundResponse(localEndpoint, msg, handler);
    }

    private void dispatchInboundRequest(InboundRequest request) {
        final Message msg = request.getMessage();

        if (mInboundRequestHandler != null) {
            if (DEBUG) LOGGER.info("Inbound request has reached top of stack: " + msg);
            try {
                mInboundRequestHandler.onInboundRequest(request);

                if (!request.isResponsePending() && !request.didAcknowledge()) {
                    request.sendSimpleResponse(Code.RESPONSE_NOT_IMPLEMENTED);
                }
            } catch (SecurityException x) {
                LOGGER.log(Level.WARNING, "Security exception while handling inbound request", x);
                request.sendSimpleResponse(Code.RESPONSE_UNAUTHORIZED);
            }
        } else {
            if (DEBUG) LOGGER.info("No request handler for inbound request: " + msg);

            if (msg.hasOptions()
                    && (msg.getOptionSet().hasProxyUri() || msg.getOptionSet().hasProxyScheme())) {
                request.sendSimpleResponse(Code.RESPONSE_PROXYING_NOT_SUPPORTED);
            } else {
                request.sendSimpleResponse(Code.RESPONSE_NOT_IMPLEMENTED);
            }
        }
    }

    void setRequestHandler(@Nullable InboundRequestHandler rh) {
        mInboundRequestHandler = rh;
    }
}
