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

import java.util.Random;

/**
 * Class for overriding specific parameters of a {@link BehaviorContext}.
 *
 * <p>This class can be used to selectively override the
 * transmission parameters (and other values) of another {@link BehaviorContext} instance.
 *
 * <p>For example, if you wanted the standard constants except for just the value
 * of {@link #getCoapAckTimeoutMs()}, you could create such an instance by doing
 * the following:
 *
 * <pre>{@code
 * BehaviorContext context = new BehaviorContextPassthru(BehaviorContext.standard()) {
 *      }&#64;{@code Override
 *      public int getCoapAckTimeoutMs() {
 *          return 250;
 *      }
 * }</pre>
 */
public class BehaviorContextPassthru extends BehaviorContext {
    private final BehaviorContext mPassthru;

    public BehaviorContextPassthru(BehaviorContext passthru) {
        mPassthru = passthru;
    }

    @Override
    public Random getRandom() {
        return mPassthru.getRandom();
    }

    @Override
    public int getMaxOutboundPacketLength() {
        return mPassthru.getMaxOutboundPacketLength();
    }

    @Override
    public int getMaxInboundPacketLength() {
        return mPassthru.getMaxInboundPacketLength();
    }

    @Override
    public int getCoapMaxRetransmit() {
        return mPassthru.getCoapMaxRetransmit();
    }

    @Override
    public int getCoapAckTimeoutMs() {
        return mPassthru.getCoapAckTimeoutMs();
    }

    @Override
    public float getCoapAckRandomFactor() {
        return mPassthru.getCoapAckRandomFactor();
    }

    @Override
    public float getCoapNstart() {
        return mPassthru.getCoapNstart();
    }

    @Override
    public int getCoapDefaultLeisureMs() {
        return mPassthru.getCoapDefaultLeisureMs();
    }

    @Override
    public int getCoapProbingRate() {
        return mPassthru.getCoapProbingRate();
    }

    @Override
    public int getCoapMaxLatencyMs() {
        return mPassthru.getCoapMaxLatencyMs();
    }

    @Override
    public int getMulticastResponseAverageDelayMs() {
        return mPassthru.getMulticastResponseAverageDelayMs();
    }
}
