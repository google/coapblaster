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

/**
 * Organizational class containing static constants for various CoAP message types, such as {@link
 * #CON}, {@link #NON}, {@link #ACK}, and {@link #RST}.
 */
public final class Type {
    // Prevent instantiation.
    private Type() {}

    /** Type for confirmable messages. */
    public static final int CON = 0;

    /** Type for non-confirmable messages. */
    public static final int NON = 1;

    /** Type for acknowledgement messages. */
    public static final int ACK = 2;

    /** Type for reset messages. */
    public static final int RST = 3;

    /** Determines if the given number is a valid CoAP type. */
    public static boolean isValid(int type) {
        return (type & ~3) == 0;
    }

    public static String toString(int type) {
        switch (type) {
            case CON:
                return "CON";

            case NON:
                return "NON";

            case ACK:
                return "ACK";

            case RST:
                return "RST";

            default:
                return "XXX";
        }
    }
}
