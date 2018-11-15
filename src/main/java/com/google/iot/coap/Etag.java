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

import com.google.auto.value.AutoValue;
import java.util.Arrays;

/** A class representing a CoAP ETag. */
@AutoValue
public abstract class Etag {
    public static Etag createFromByteArray(byte[] bytes, int offset, int length) {
        return new AutoValue_Etag(Arrays.copyOfRange(bytes, offset, offset + length));
    }

    public static Etag createFromByteArray(byte[] bytes) {
        return createFromByteArray(bytes, 0, bytes.length);
    }

    public static Etag createFromInteger(int i) {
        return new AutoValue_Etag(Utils.encodeInteger(i));
    }

    /**
     * Gets the byte array containing this Etag. Note that the data contained in this array MUST NOT
     * be mutated.
     */
    @SuppressWarnings("mutable")
    public abstract byte[] byteArrayValue();

    public int length() {
        return byteArrayValue().length;
    }

    public boolean isEmpty() {
        return length() == 0;
    }

    @Override
    public String toString() {
        if (isEmpty()) {
            return "EMPTY_ETAG";
        }

        StringBuilder sb = new StringBuilder();

        for (byte b : byteArrayValue()) {
            sb.append(String.format("%02x", b));
        }

        return sb.toString();
    }
}
