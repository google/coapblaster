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

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ContextTest {

    //    @Test
    //    void getDefaultPortForScheme() {
    //    }

    @Test
    void getCoapMaxRetransmit() {
        assertEquals(4, BehaviorContext.standard().getCoapMaxRetransmit());
    }

    @Test
    void getCoapAckTimeoutMs() {
        assertEquals(2000, BehaviorContext.standard().getCoapAckTimeoutMs());
    }

    @Test
    void getCoapAckRandomFactor() {
        assertEquals(1.5f, BehaviorContext.standard().getCoapAckRandomFactor());
    }

    @Test
    void getCoapNstart() {
        assertEquals(1, BehaviorContext.standard().getCoapNstart());
    }

    @Test
    void getCoapDefaultLeisureMs() {
        assertEquals(5000, BehaviorContext.standard().getCoapDefaultLeisureMs());
    }

    @Test
    void getCoapProbingRate() {
        assertEquals(1, BehaviorContext.standard().getCoapProbingRate());
    }

    @Test
    void getCoapMaxTransmitSpanMs() {
        assertEquals(45000, BehaviorContext.standard().getCoapMaxTransmitSpanMs());
    }

    @Test
    void getCoapMaxTransmitWaitMs() {
        assertEquals(93000, BehaviorContext.standard().getCoapMaxTransmitWaitMs());
    }

    @Test
    void getCoapMaxLatencyMs() {
        assertEquals(100000, BehaviorContext.standard().getCoapMaxLatencyMs());
    }

    @Test
    void getCoapProcessingDelayMs() {
        assertEquals(2000, BehaviorContext.standard().getCoapProcessingDelayMs());
    }

    @Test
    void getCoapMaxRttMs() {
        assertEquals(202000, BehaviorContext.standard().getCoapMaxRttMs());
    }

    @Test
    void getCoapExchangeLifetimeMs() {
        assertEquals(247000, BehaviorContext.standard().getCoapExchangeLifetimeMs());
    }

    @Test
    void getCoapNonLifetimeMs() {
        assertEquals(145000, BehaviorContext.standard().getCoapNonLifetimeMs());
    }

    //    @Test
    //    void lookupSocketAddress() {
    //    }
    //
    //    @Test
    //    void getLocalEndpointForScheme() {
    //    }
}
