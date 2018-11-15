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

import java.util.Arrays;

/** A class representing a CoAP token. */
public final class Token {

    private final byte[] mBytes;
    private final int mHashCode;

    private static final Token EMPTY_TOKEN = new Token();

    private Token(byte[] bytes, int len) {
        mBytes = Arrays.copyOf(bytes, len);
        mHashCode = Arrays.hashCode(mBytes);
    }

    private Token(byte[] bytes) {
        mBytes = Arrays.copyOf(bytes, bytes.length);
        mHashCode = Arrays.hashCode(mBytes);
    }

    private Token(int i) {
        mBytes = Utils.encodeInteger(i);
        mHashCode = Arrays.hashCode(mBytes);
    }

    private Token() {
        this(0);
    }

    public static Token emptyToken() {
        return EMPTY_TOKEN;
    }

    public static Token tokenFromInteger(int i) {
        if (i == 0) {
            return EMPTY_TOKEN;
        }
        return new Token(i);
    }

    public static Token tokenFromBytes(byte[] bytes, int len) {
        if (len == 0) {
            return EMPTY_TOKEN;
        }
        return new Token(bytes, len);
    }

    public static Token tokenFromBytes(byte[] bytes) {
        if (bytes.length == 0) {
            return EMPTY_TOKEN;
        }
        return new Token(bytes);
    }

    public byte[] getBytes() {
        return mBytes;
    }

    public int getLength() {
        return mBytes.length;
    }

    public boolean isEmpty() {
        return mBytes.length == 0;
    }

    @Override
    public int hashCode() {
        return mHashCode;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (obj == null) {
            return false;
        }

        if (obj.getClass() != getClass()) {
            return false;
        }

        Token rhs = (Token) obj;

        return Arrays.equals(mBytes, rhs.mBytes);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (byte b : mBytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
