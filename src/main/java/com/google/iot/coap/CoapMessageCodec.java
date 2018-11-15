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

import java.nio.ByteBuffer;

class CoapMessageCodec implements MessageCodec {

    private static final byte COAP_VERSION = (byte) 0x40;
    private static final byte COAP_VERSION_MASK = (byte) 0xC0;

    private static final byte COAP_TYPE_MASK = (byte) 0x30;
    private static final byte COAP_TOKEN_LENGTH_MASK = (byte) 0x0F;

    private static final byte COAP_PAYLOAD_MARKER = (byte) 0xFF;

    @Override
    public void encodeMessage(Message msg, ByteBuffer buffer) {
        try {
            msg.throwIfInvalid();
        } catch (CoapException x) {
            throw new IllegalArgumentException(x);
        }

        if (msg.getMid() == Message.MID_NONE) {
            throw new IllegalArgumentException("Missing MID");
        }

        Token token = msg.getToken();

        final byte firstByte = (byte) (COAP_VERSION + (msg.getType() << 4) + token.getLength());

        buffer.put(firstByte);
        buffer.put((byte) msg.getCode());
        buffer.putShort((short) msg.getMid());
        buffer.put(token.getBytes());

        encodeOptionsAndPayload(msg, buffer);
    }

    public void encodeOptionsAndPayload(Message msg, ByteBuffer buffer) {
        Option lastOption = null;
        for (Option option : msg.getOptionSet().asSortedList()) {
            int delta = option.getNumber();
            int length = option.byteArrayLength();

            if (lastOption != null) {
                delta -= lastOption.getNumber();
            }

            int headerN = delta;
            int headerL = length;

            //noinspection StatementWithEmptyBody
            if (delta < 13) {
                // It fits! Don't change it.
            } else if (delta < 269) {
                headerN = 13;
                delta -= 13;
            } else {
                headerN = 14;
                delta -= 269;
            }

            //noinspection StatementWithEmptyBody
            if (length < 13) {
                // It fits! Don't change it.
            } else if (length < 269) {
                headerL = 13;
                length -= 13;
            } else {
                headerL = 14;
                length -= 269;
            }

            buffer.put((byte) ((headerN << 4) + headerL));

            if (headerN == 13) {
                buffer.put((byte) delta);
            } else if (headerN == 14) {
                buffer.putShort((short) delta);
            }

            if (headerL == 13) {
                buffer.put((byte) length);
            } else if (headerL == 14) {
                buffer.putShort((short) length);
            }

            buffer.put(option.byteArrayValue());

            lastOption = option;
        }

        if (msg.hasPayload()) {
            buffer.put((byte) 0xFF);
            buffer.put(msg.getPayload());
        }
    }

    @Override
    public MutableMessage decodeMessage(ByteBuffer buffer) throws CoapParseException {
        byte firstByte = buffer.get();

        if ((firstByte & COAP_VERSION_MASK) != COAP_VERSION) {
            throw new CoapParseException("Unrecognized CoAP version");
        }

        final MutableMessage ret = MutableMessage.create();

        try {
            ret.setType((firstByte & COAP_TYPE_MASK) >> 4);
            ret.setCode(buffer.get() & 0xFF);
            ret.setMid(buffer.getShort() & 0xFFFF);

            byte[] tokBytes = new byte[firstByte & COAP_TOKEN_LENGTH_MASK];

            buffer.get(tokBytes);
            ret.setToken(Token.tokenFromBytes(tokBytes));

            int lastNumber = 0;

            while (buffer.hasRemaining()) {
                byte optionHeader = buffer.get();
                int number = (optionHeader >> 4) & 0xF;
                int length = optionHeader & 0xF;

                if (optionHeader == COAP_PAYLOAD_MARKER) {
                    byte[] payload = new byte[buffer.remaining()];
                    buffer.get(payload);
                    ret.setPayload(payload);
                    break;
                }

                if (number == 13) {
                    number = (buffer.get() & 0xFF) + 13;
                } else if (number == 14) {
                    number = (buffer.getShort() & 0xFFFF) + 269;
                } else if (number == 15) {
                    throw new CoapParseException("Illegal encoding of option number");
                }

                if (length == 13) {
                    length = (buffer.get() & 0xFF) + 13;
                } else if (length == 14) {
                    length = (buffer.getShort() & 0xFFFF) + 269;
                } else if (length == 15) {
                    throw new CoapParseException("Illegal encoding of option number");
                }

                lastNumber += number;

                byte[] optValue = new byte[length];

                buffer.get(optValue);
                ret.addOption(new Option(lastNumber, optValue));
            }

        } catch (IllegalArgumentException x) {
            throw new CoapParseException(x);
        }

        return ret;
    }
}
