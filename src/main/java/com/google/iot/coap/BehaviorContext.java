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

import java.security.SecureRandom;
import java.util.Random;

/**
 * Contains the <a href="https://tools.ietf.org/html/rfc7252#section-4.8">CoAP transmission
 * parameters</a> as well as other important constants that influence CoAP behavior.
 *
 * <p>Instances of this class define the low-level constants which define the behavior of things
 * like retransmission times and response timeouts.
 *
 * <p>If you just need the default behavior RFC7252-recommended behavior, you can just use the
 * instance obtained from {@link #standard()}.
 *
 * <p>If you need to customize some constants, see {@link BehaviorContextPassthru}.
 */
public class BehaviorContext {
    private static final Random sRandom = new SecureRandom();

    BehaviorContext() {}

    public static BehaviorContext standard() {
        return new BehaviorContext();
    }

    public Random getRandom() {
        return sRandom;
    }

    /**
     * The maximum length of a single outbound packet. Packets that are larger than this threshold
     * will either bounce or be automatically broken up as a block transfer. RFC7252 recommends a
     * value of 1152 when nothing else is known about the underlying transport. If you are primarily
     * communicating with highly constrained devices, you should decrease this amount significantly,
     * perhaps to as low as 196 bytes, 80 bytes or even lower.
     */
    public int getMaxOutboundPacketLength() {
        return 1152;
    }

    /**
     * The maximum length of a single inbound packet. This is used to determine the maximum size of
     * the inbound packet buffer. There is generally no downsize to this value being much larger
     * than {@link #getMaxOutboundPacketLength()}.
     */
    public int getMaxInboundPacketLength() {
        return Coap.MAX_SINGLE_MESSAGE_LENGTH;
    }

    /** RFC7252 MAX_RETRANSMIT, in attempts */
    public int getCoapMaxRetransmit() {
        return 4;
    }

    /** RFC7252 ACK_TIMEOUT, in milliseconds */
    public int getCoapAckTimeoutMs() {
        return 2000;
    }

    /** RFC7252 ACK_RANDOM_FACTOR */
    public float getCoapAckRandomFactor() {
        return 1.5f;
    }

    /** RFC7252 NSTART */
    public float getCoapNstart() {
        return 1;
    }

    /** RFC7252 DEFAULT_LEISURE, in milliseconds */
    public int getCoapDefaultLeisureMs() {
        return 5000;
    }

    /** RFC7252 PROBING_RATE, in bytes/second */
    public int getCoapProbingRate() {
        return 1;
    }

    /** RFC7252 MAX_TRANSMIT_SPAN, in milliseconds */
    public int getCoapMaxTransmitSpanMs() {
        return (int)
                (getCoapAckTimeoutMs()
                        * ((1 << getCoapMaxRetransmit()) - 1)
                        * getCoapAckRandomFactor());
    }

    /** RFC7252 MAX_TRANSMIT_WAIT, in milliseconds */
    public int getCoapMaxTransmitWaitMs() {
        return (int)
                (getCoapAckTimeoutMs()
                        * ((1 << (getCoapMaxRetransmit() + 1)) - 1)
                        * getCoapAckRandomFactor());
    }

    /** RFC7252 MAX_LATENCY, in milliseconds */
    public int getCoapMaxLatencyMs() {
        return 100 * 1000;
    }

    /** RFC7252 PROCESSING_DELAY, in milliseconds */
    public int getCoapProcessingDelayMs() {
        return getCoapAckTimeoutMs();
    }

    /** RFC7252 MAX_RTT, in milliseconds */
    public int getCoapMaxRttMs() {
        return 2 * getCoapMaxLatencyMs() + getCoapProcessingDelayMs();
    }

    /** RFC7252 EXCHANGE_LIFETIME, in milliseconds.
     *
     * With the default RFC7252 constants, this value is 247,000 milliseconds.
     */
    public int getCoapExchangeLifetimeMs() {
        return getCoapMaxTransmitSpanMs() + 2 * getCoapMaxLatencyMs() + getCoapProcessingDelayMs();
    }

    /**
     * Maximum number of confirmable messages that can be sent
     * per second without running out of unique message ids.
     *
     * With the default RFC7252 constants, this value is 265 unique confirmable messages per
     * second to a given endpoint. That is around 3.7ms between messages.
     */
    public int getMaxMessageRatePerSecond() {
        return (65535*1000)/getCoapExchangeLifetimeMs();
    }

    /** RFC7252 NON_LIFETIME, in milliseconds */
    public int getCoapNonLifetimeMs() {
        return getCoapMaxTransmitSpanMs() + getCoapMaxLatencyMs();
    }

    /**
     * Calculates an appropriate unicast retransmit timer for the given attempt count. Implements an
     * exponential backoff with random jitter. Result is based on {@link #getCoapAckTimeoutMs()},
     * {@link #getCoapAckRandomFactor()}, and {@link #getRandom()}.
     *
     * @param attempt Number of attempts. Must be at least 1.
     * @return the number of milliseconds to wait before the next retransmission.
     */
    public int calcRetransmitTimeoutAtAttempt(int attempt) {
        if (attempt < 1) {
            throw new IllegalArgumentException("Invalid number of attempts");
        }

        int ret = getCoapAckTimeoutMs();

        if (attempt > 5) {
            attempt = 5;
        }

        ret += (int) (ret * getRandom().nextFloat() * getCoapAckRandomFactor());

        return (ret << (attempt - 1));
    }

    public int getMulticastResponseAverageDelayMs() {
        return 25;
    }

    /**
     * Returns an appropriate amount of time to wait (in milliseconds) before sending a response to
     * a multicast request.
     */
    public int calcMulticastResponseDelay() {
        return (int)
                (getMulticastResponseAverageDelayMs()
                        * getRandom().nextFloat()
                        * getCoapAckRandomFactor());
    }
}
