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

import static com.google.iot.coap.Utils.decodeInteger;
import static com.google.iot.coap.Utils.encodeInteger;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/** Class representing a CoAP message option, as well as all of the associated option constants. */
@SuppressWarnings("unused")
public final class Option {
    /** Mask constants for CoAP options. */
    public final class Mask {
        public static final int CRITICAL = 1;
        public static final int UN_SAFE = 2;
        public static final int NO_CACHE_KEY = 0x1e;
        public static final int NO_CACHE_KEY_MAGIC = 0x1c;

        private Mask() {}
    }

    public static final int IF_MATCH = 1;

    public static final int URI_HOST = 3;

    public static final int ETAG = 4;

    public static final int IF_NONE_MATCH = 5;

    public static final int URI_PORT = 7;

    public static final int LOCATION_PATH = 8;

    public static final int URI_PATH = 11;

    public static final int CONTENT_FORMAT = 12;

    public static final int MAX_AGE = 14;

    public static final int URI_QUERY = 15;

    public static final int ACCEPT = 17;

    public static final int LOCATION_QUERY = 20;

    public static final int PROXY_URI = 35;

    public static final int PROXY_SCHEME = 39;

    public static final int OBSERVE = 6;

    public static final int BLOCK2 = 23;

    public static final int BLOCK1 = 27;

    public static final int SIZE2 = 28;

    public static final int SIZE1 = 60;

    /**
     * Determines if this option number is considered "critical", as defined by RFC7252.
     *
     * @return true if the option number is critical, false if not
     */
    public static boolean isCritical(int number) {
        return (number & Mask.CRITICAL) != 0;
    }

    /**
     * Determines if this option number is considered "un-safe", as defined by RFC7252.
     *
     * @return true if the option number is un-safe, false if not
     */
    public static boolean isUnSafe(int number) {
        return (number & Mask.UN_SAFE) != 0;
    }

    /**
     * Determines if this option number is considered "no-cache-key", as defined by RFC7252.
     *
     * @return true if the option number is no-cache-key, false if not
     */
    public static boolean isNoCacheKey(int number) {
        return (number & Mask.NO_CACHE_KEY) == Mask.NO_CACHE_KEY_MAGIC;
    }

    /** Returns true if the value of the given option number is considered an integer. */
    public static boolean isValueInt(int number) {
        switch (number) {
            case URI_PORT:
            case CONTENT_FORMAT:
            case MAX_AGE:
            case ACCEPT:
            case OBSERVE:
            case BLOCK2:
            case BLOCK1:
            case SIZE2:
            case SIZE1:
                return true;

            default:
                return false;
        }
    }

    /** Returns true if the value of the given option number is considered a text string. */
    public static boolean isValueString(int number) {
        switch (number) {
            case URI_HOST:
            case LOCATION_PATH:
            case URI_PATH:
            case URI_QUERY:
            case LOCATION_QUERY:
            case PROXY_URI:
            case PROXY_SCHEME:
                return true;

            default:
                return false;
        }
    }

    /**
     * Converts the given option number to its associated string representation. If the option
     * number is not recognized, a descriptive replacement is used instead.
     *
     * @param number the option number
     * @return a {@link String} describing the option number
     */
    public static String toString(int number) {
        switch (number) {
            case IF_MATCH:
                return "If-Match";
            case URI_HOST:
                return "Uri-Host";
            case ETAG:
                return "ETag";
            case IF_NONE_MATCH:
                return "If-None-Match";
            case URI_PORT:
                return "Uri-Port";
            case LOCATION_PATH:
                return "Location-Path";
            case URI_PATH:
                return "Uri-Path";
            case CONTENT_FORMAT:
                return "Content-Format";
            case MAX_AGE:
                return "Max-Age";
            case URI_QUERY:
                return "Uri-Query";
            case ACCEPT:
                return "Accept";
            case LOCATION_QUERY:
                return "Location-Query";
            case PROXY_URI:
                return "Proxy-Uri";
            case PROXY_SCHEME:
                return "Proxy-Scheme";
            case OBSERVE:
                return "Observe";
            case BLOCK2:
                return "Block2";
            case BLOCK1:
                return "Block1";
            case SIZE2:
                return "Size2";
            case SIZE1:
                return "Size1";

            default:
                /* Come up with a reasonably descriptive string */
                StringBuilder sb = new StringBuilder("X-CoAP-");

                if (isCritical(number)) {
                    sb.append("critical-");
                } else {
                    sb.append("elective-");
                }

                if (isUnSafe(number)) {
                    sb.append("unsafe-");
                }

                if (isNoCacheKey(number)) {
                    sb.append("nocachekey-");
                }

                sb.append(number);
                return sb.toString();
        }
    }

    /** Comparator class for sorting {@link Option} instances. */
    public static class Comparator implements java.util.Comparator<Option> {
        private static final Comparator sSingleton = new Comparator();

        public static Comparator get() {
            return sSingleton;
        }

        @Override
        public int compare(Option lhs, Option rhs) {
            return Integer.compare(lhs.getNumber(), rhs.getNumber());
        }
    }

    private final int mNumber;
    private final byte[] mValue;

    /**
     * Constructs a option with the given option number and byte array value.
     *
     * @param number the option number
     * @param value the byte array value for this option
     */
    public Option(int number, byte[] value) {
        if (number < 0) {
            throw new IllegalArgumentException("Option number cannot be negative");
        }
        mNumber = number;
        mValue = Objects.requireNonNull(value);
    }

    /**
     * Constructs an empty option with the given option number.
     *
     * @param number the option number
     */
    public Option(int number) {
        this(number, new byte[0]);
    }

    /**
     * Constructs a option with the given option number and integer value.
     *
     * @param number the option number
     * @param intValue the integer value for this option
     */
    public Option(int number, int intValue) {
        this(number, encodeInteger(intValue));
    }

    /**
     * Constructs a option with the given option number and {@link String} value.
     *
     * @param number the option number
     * @param utf8Value the string to use for this option, to be encoded as UTF-8
     */
    public Option(int number, String utf8Value) {
        this(number, utf8Value.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Determines if this option is considered "critical", as defined by RFC7252.
     *
     * @return true if the option number is critical, false if not
     */
    public boolean isCritical() {
        return isCritical(mNumber);
    }

    /**
     * Determines if this option is considered "un-safe", as defined by RFC7252.
     *
     * @return true if the option number is un-safe, false if it is safe
     */
    public boolean isUnSafe() {
        return isUnSafe(mNumber);
    }

    /**
     * Determines if this option is considered "no-cache-key", as defined by RFC7252.
     *
     * @return true if the option number is no-cache-key, false if not
     */
    public boolean isNoCacheKey() {
        return isNoCacheKey(mNumber);
    }

    /**
     * Returns the option number for this {@link Option}.
     *
     * @return the option number
     */
    public int getNumber() {
        return mNumber;
    }

    /** Returns the length (in bytes) of the byte-array representation of this option's value. */
    public int byteArrayLength() {
        return mValue.length;
    }

    /** Returns the byte-array representation of this option's value. */
    public byte[] byteArrayValue() {
        return mValue;
    }

    /** Returns the integer representation of this option's value. */
    public int intValue() {
        return decodeInteger(mValue);
    }

    /** Returns the {@link String} representation of this option's value. */
    public String stringValue() {
        return new String(mValue, StandardCharsets.UTF_8);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(mValue) * 13 + Integer.hashCode(mNumber);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (!(obj instanceof Option)) {
            return false;
        }

        Option rhs = (Option) obj;

        return mNumber == rhs.mNumber && Arrays.equals(mValue, rhs.mValue);
    }

    String valueToString() {
        if (mNumber == BLOCK1 || mNumber == BLOCK2) {
            return BlockOption.create(intValue()).toString();

        } else if (mNumber == ACCEPT || mNumber == CONTENT_FORMAT) {
            return ContentFormat.toString(intValue());

        } else if (isValueInt(mNumber)) {
            return Integer.toString(intValue());

        } else if (isValueString(mNumber)) {
            return stringValue();

        } else {
            StringBuilder sb = new StringBuilder();

            for (byte b : byteArrayValue()) {
                sb.append(String.format("%02x", b));
            }

            return sb.toString();
        }
    }

    @Override
    public String toString() {
        if (byteArrayLength() == 0 && !isValueInt(mNumber)) {
            return toString(mNumber);
        }
        return toString(mNumber) + ":" + valueToString();
    }
}
