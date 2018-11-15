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

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

@SuppressWarnings("ConstantConditions")
class CoapMessageCodecTest {
    private static final boolean DEBUG = false;
    private static final Logger LOGGER =
            Logger.getLogger(CoapMessageCodecTest.class.getCanonicalName());

    private String byteArrayToHexString(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x ", b));
        }
        return sb.toString();
    }

    @Test
    public void testFailureCase1() throws Exception {
        byte[] packet =
                new byte[] {
                    0x42,
                    0x01,
                    0x5d,
                    0x47,
                    0x5d,
                    0x47,
                    0x3d,
                    0x00,
                    0x31,
                    0x39,
                    0x32,
                    0x2e,
                    0x31,
                    0x36,
                    0x38,
                    0x2e,
                    0x33,
                    0x33,
                    0x2e,
                    0x32,
                    0x30,
                    (byte) 0x81,
                    0x31,
                    0x04,
                    0x73,
                    0x74,
                    0x61,
                    0x74
                };
        ByteBuffer byteBuffer = ByteBuffer.wrap(packet);

        CoapMessageCodec messageCodec = new CoapMessageCodec();

        Message msg = messageCodec.decodeMessage(byteBuffer);

        assertTrue(msg.isValid());

        assertProperlyEncoded(packet, msg);
    }

    private static final byte[] message1Bytes =
            new byte[] {
                0x41,
                0x01,
                (byte) 0x9D,
                0x35,
                0x20,
                (byte) 0xBB,
                't',
                'e',
                'm',
                'p',
                'e',
                'r',
                'a',
                't',
                'u',
                'r',
                'e'
            };

    private static final byte[] message2Bytes =
            new byte[] {
                0x61, 69, (byte) 0x9D, 0x35, 0x20, (byte) 0xFF, '2', '2', '.', '3', ' ', 'C'
            };

    void assertProperlyEncoded(byte[] expected, Message msg) {
        byte[] buffer = new byte[expected.length];
        ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);

        MessageCodec codec = new CoapMessageCodec();
        codec.encodeMessage(msg, byteBuffer);

        if (DEBUG) {
            LOGGER.info("Expected: " + byteArrayToHexString(expected));
            LOGGER.info("  Actual: " + byteArrayToHexString(byteBuffer.array()));
        }

        assertArrayEquals(expected, byteBuffer.array());
    }

    @Test
    void encodeMessage1() {
        MutableMessage msg = MutableMessage.create();

        msg.setType(Type.CON);
        msg.setCode(Code.METHOD_GET);
        msg.setToken(Token.tokenFromInteger(0x20));
        msg.setMid(0x9D35);
        msg.getOptionSet().setUri(URI.create("/temperature"));

        assertProperlyEncoded(message1Bytes, msg);
    }

    @Test
    void decodeMessage1() throws CoapParseException {
        ByteBuffer byteBuffer = ByteBuffer.wrap(message1Bytes);

        MessageCodec codec = new CoapMessageCodec();

        Message msg = codec.decodeMessage(byteBuffer);

        assertTrue(msg.isValid(), msg.toString());
        assertEquals(Type.CON, msg.getType());
        assertEquals(Code.METHOD_GET, msg.getCode());
        assertEquals(0x9D35, msg.getMid());
        assertEquals(Token.tokenFromInteger(0x20), msg.getToken());
        assertEquals(1, msg.getOptionSet().size());
        assertEquals("temperature", msg.getOptionSet().getUriPaths().get(0));
        assertFalse(msg.hasPayload());
    }

    @Test
    void encodeMessage2() {
        MutableMessage msg = MutableMessage.create();

        msg.setType(Type.ACK);
        msg.setCode(Code.RESPONSE_CONTENT);
        msg.setToken(Token.tokenFromInteger(0x20));
        msg.setMid(0x9D35);
        msg.setPayload("22.3 C");

        assertProperlyEncoded(message2Bytes, msg);
    }

    @Test
    void decodeMessage2() throws CoapParseException {
        ByteBuffer byteBuffer = ByteBuffer.wrap(message2Bytes);

        MessageCodec codec = new CoapMessageCodec();

        Message msg = codec.decodeMessage(byteBuffer);

        assertTrue(msg.isValid());
        assertEquals(Type.ACK, msg.getType());
        assertEquals(Code.RESPONSE_CONTENT, msg.getCode());
        assertEquals(0x9D35, msg.getMid());
        assertEquals(Token.tokenFromInteger(0x20), msg.getToken());
        assertEquals(0, msg.getOptionSet().size());
        assertEquals("22.3 C", msg.getPayloadAsString());
    }
}
