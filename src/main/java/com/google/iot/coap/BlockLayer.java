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

import java.net.SocketAddress;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.checkerframework.checker.nullness.qual.Nullable;

class BlockLayer extends AbstractLayer {
    private static final boolean DEBUG = false;
    private static final Logger LOGGER = Logger.getLogger(BlockLayer.class.getCanonicalName());
    private final Map<KeyToken, OutboundResponseEntry> mOutboundResponseLookupTable =
            new ConcurrentHashMap<>();
    private final Map<KeyToken, OutboundRequestEntry> mOutboundRequestLookupTable =
            new ConcurrentHashMap<>();
    private final BlockOption mDefaultFirstBlockOption = BlockOption.create(0, true, 3);

    class OutboundResponseEntry {
        final KeyToken mKey;
        final long mExpiresAfter;
        final BlockOption mDefaultBlockOption;
        Message mMessage = null;

        OutboundResponseEntry(
                BehaviorContext behaviorContext, KeyToken key, BlockOption defaultBlockOption) {
            mExpiresAfter =
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime())
                            + behaviorContext.getCoapExchangeLifetimeMs();
            mKey = key;
            mDefaultBlockOption = defaultBlockOption;
            if (DEBUG) LOGGER.info("New response entry for " + mKey);
        }

        void setMessage(Message msg) {
            mMessage = msg.copy();
        }

        MutableMessage getMessageForBlock(@Nullable BlockOption blockOption) {
            if (blockOption == null) {
                blockOption = mDefaultBlockOption;
            }
            MutableMessage msg = mMessage.mutableCopy();

            int begin = blockOption.getBlockOffset();
            int end = begin + blockOption.getBlockSize();

            if (end >= msg.getPayloadLength()) {
                end = msg.getPayloadLength();
                blockOption = blockOption.getLastBlockOption();
            } else {
                blockOption = blockOption.getMoreBlockOption();
            }

            if (DEBUG)
                LOGGER.info(
                        String.format("%s: %s (%d)", mKey, blockOption, blockOption.toInteger()));

            msg.getOptionSet().setBlock2(blockOption);
            msg.setPayload(Arrays.copyOfRange(msg.getPayload(), begin, end));
            return msg;
        }
    }

    class OutboundRequestEntry {
        final KeyToken mKey;
        final long mExpiresAfter;
        final Message mMessage;

        static final int STATE_SENT = 0;
        static final int STATE_BLOCK1 = 1;
        static final int STATE_FINISHED = 2;

        int mState = STATE_SENT;

        private Map<SocketAddress, BlockReconstructor> mBlockReconstructorMap = null;
        private BlockReconstructor mBlockReconstructor = null;

        OutboundRequestEntry(BehaviorContext behaviorContext, KeyToken key, Message message) {
            mExpiresAfter =
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime())
                            + behaviorContext.getCoapExchangeLifetimeMs();
            mKey = key;
            mMessage = message;
            if (DEBUG) LOGGER.info("New request entry for " + mKey);
        }

        Message getFirstOutboundRequest() {
            return mMessage;
        }

        boolean isFinished() {
            return mState == STATE_FINISHED;
        }

        BlockReconstructor getBlockReconstructor(SocketAddress saddr) {
            if (mMessage.isMulticast()) {
                if (mBlockReconstructorMap == null) {
                    mBlockReconstructorMap = new HashMap<>();
                }
                return mBlockReconstructorMap.computeIfAbsent(saddr, s -> new BlockReconstructor());
            }
            if (mBlockReconstructor == null) {
                mBlockReconstructor = new BlockReconstructor();
            }
            return mBlockReconstructor;
        }

        void handleInboundResponse(
                LocalEndpoint localEndpoint,
                Message msg,
                OutboundMessageHandler outboundMessageHandler) {
            final OptionSet optionSet = msg.getOptionSet();
            // final BlockOption block1 = optionSet.getBlock1();
            final BlockOption block2 = optionSet.getBlock2();

            if (mState == STATE_SENT) {
                if (msg.getCode() == Code.RESPONSE_REQUEST_ENTITY_INCOMPLETE) {
                    // TODO: Start Block 1 transfer. Just passing up the error for now.
                    LOGGER.warning(
                            String.format(
                                    "REQUEST-BLOCK1: %s: Support for Block1 isn't yet implemented.",
                                    mKey));
                    if (!mMessage.isMulticast()) {
                        mState = STATE_FINISHED;
                    }
                    BlockLayer.super.handleInboundResponse(
                            localEndpoint, msg, outboundMessageHandler);
                    return;
                } else if (block2 == null) {
                    // This is just a normal non-block-transfer response
                    if (!mMessage.isMulticast()) {
                        // Multicast requests never enter the finished state
                        // (but they do eventually time out)
                        mState = STATE_FINISHED;
                    }
                    BlockLayer.super.handleInboundResponse(
                            localEndpoint, msg, outboundMessageHandler);
                    return;
                } else {
                    BlockReconstructor blockReconstructor =
                            getBlockReconstructor(msg.getRemoteSocketAddress());
                    boolean isFinished = blockReconstructor.feedBlock(block2, msg.getPayload());

                    if (isFinished) {
                        if (DEBUG)
                            LOGGER.info(
                                    String.format(
                                            "REQUEST-BLOCK2: %s: Got %s, FINISHED!", mKey, block2));
                        // We have successfully reconstructed our message!
                        if (!mMessage.isMulticast()) {
                            // Multicast requests never enter the finished state
                            // (but they do eventually time out)
                            mState = STATE_FINISHED;
                        }
                        MutableMessage finalResponse = msg.mutableCopy();
                        finalResponse.setPayload(blockReconstructor.copyData());
                        finalResponse.getOptionSet().setBlock1(null);
                        finalResponse.getOptionSet().setBlock2(null);
                        BlockLayer.super.handleInboundResponse(
                                localEndpoint, finalResponse, outboundMessageHandler);

                    } else {
                        // Request the next block.
                        BlockOption nextBlock2 = blockReconstructor.getNextWantedBlock();

                        if (DEBUG)
                            LOGGER.info(
                                    String.format(
                                            "REQUEST-BLOCK2: %s: Got %s, next is %s",
                                            mKey, block2, nextBlock2));

                        MutableMessage nextRequest = mMessage.mutableCopy();

                        nextRequest.setRemoteSocketAddress(msg.getRemoteSocketAddress());
                        nextRequest.setPayload("");
                        nextRequest.getOptionSet().setBlock2(nextBlock2);
                        nextRequest.setMid(localEndpoint.newMid(msg.getRemoteSocketAddress()));
                        BlockLayer.super.handleOutboundRequest(
                                localEndpoint, nextRequest, outboundMessageHandler);
                    }
                    return;
                }
            }

            LOGGER.warning(String.format("REQUEST: %s: Unexpected state %d", mKey, mState));
            BlockLayer.super.handleInboundResponse(localEndpoint, msg, outboundMessageHandler);
        }
    }

    public BlockLayer(LocalEndpointManager ignored) {
        if (DEBUG)
            LOGGER.info(
                    "Default block size is " + mDefaultFirstBlockOption.getBlockSize() + " bytes.");
    }

    @Override
    public void close() {
        mOutboundResponseLookupTable.clear();
        super.close();
    }

    @Override
    public int getSortOrder() {
        return 20;
    }

    @Override
    public void cleanup() {
        long now = TimeUnit.NANOSECONDS.toMillis(System.nanoTime());
        mOutboundResponseLookupTable.forEach(
                (k, entry) -> {
                    if (now > entry.mExpiresAfter) {
                        if (DEBUG) LOGGER.info("Removing old block response entry " + k);
                        mOutboundResponseLookupTable.remove(k);
                    }
                });
        mOutboundRequestLookupTable.forEach(
                (k, entry) -> {
                    if (now > entry.mExpiresAfter) {
                        if (DEBUG) LOGGER.info("Removing old block request entry " + k);
                        mOutboundRequestLookupTable.remove(k);
                    }
                });
    }

    @Override
    public void handleOutboundResponse(LocalEndpoint localEndpoint, Message msg) {
        if (msg.getPayloadLength() < mDefaultFirstBlockOption.getBlockSize()
                || msg.getOptionSet().hasBlock1()
                || msg.getOptionSet().hasBlock2()) {
            super.handleOutboundResponse(localEndpoint, msg);
            return;
        }

        final KeyToken key = new KeyToken(msg);
        final OutboundResponseEntry entry =
                mOutboundResponseLookupTable.computeIfAbsent(
                        key,
                        k ->
                                new OutboundResponseEntry(
                                        localEndpoint.getBehaviorContext(),
                                        k,
                                        mDefaultFirstBlockOption));

        entry.setMessage(msg);

        super.handleOutboundResponse(localEndpoint, entry.getMessageForBlock(null));
    }

    @Override
    public void handleInboundRequest(InboundRequest inboundRequest) {
        final Message msg = inboundRequest.getMessage();
        final OptionSet optionSet = msg.getOptionSet();
        final BlockOption block1 = optionSet.getBlock1();
        final BlockOption block2 = optionSet.getBlock2();

        if (block2 != null) {
            final KeyToken key = new KeyToken(msg);

            OutboundResponseEntry entry = mOutboundResponseLookupTable.get(key);

            if (entry != null) {
                MutableMessage response = entry.getMessageForBlock(block2);
                response.setMid(msg.getMid());
                response.setType(Type.ACK);
                super.handleOutboundResponse(inboundRequest.getLocalEndpoint(), response);
                inboundRequest.drop();
                return;
            }

            entry =
                    new OutboundResponseEntry(
                            inboundRequest.getLocalEndpoint().getBehaviorContext(), key, block2);
            mOutboundResponseLookupTable.put(key, entry);
        }

        // TODO: Handle (block1 != null)

        super.handleInboundRequest(inboundRequest);
    }

    @Override
    public void handleOutboundRequest(
            LocalEndpoint localEndpoint,
            Message msg,
            @Nullable OutboundMessageHandler outboundMessageHandler) {
        if (msg.getOptionSet().hasBlock1()) {
            super.handleOutboundRequest(localEndpoint, msg, outboundMessageHandler);
            return;
        }

        final KeyToken key = new KeyToken(msg);
        OutboundRequestEntry entry;
        entry = new OutboundRequestEntry(localEndpoint.getBehaviorContext(), key, msg);
        mOutboundRequestLookupTable.put(key, entry);

        super.handleOutboundRequest(
                localEndpoint, entry.getFirstOutboundRequest(), outboundMessageHandler);
    }

    @Override
    public void handleInboundResponse(
            LocalEndpoint localEndpoint,
            Message msg,
            OutboundMessageHandler outboundMessageHandler) {
        final KeyToken key = new KeyToken(msg);
        OutboundRequestEntry entry = mOutboundRequestLookupTable.get(key);

        if (entry != null) {
            entry.handleInboundResponse(localEndpoint, msg, outboundMessageHandler);

            if (entry.isFinished()) {
                mOutboundRequestLookupTable.remove(key);
            }
            return;
        }

        super.handleInboundResponse(localEndpoint, msg, outboundMessageHandler);
    }
}
