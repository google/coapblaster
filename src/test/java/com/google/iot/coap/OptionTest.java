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

class OptionTest {

    @Test
    void getNumber() {
        assertEquals(Option.OBSERVE, new Option(Option.OBSERVE).getNumber());
    }

    @Test
    void getRawValueLength() {
        assertEquals(0, new Option(Option.OBSERVE, 0).byteArrayLength());
        assertEquals(1, new Option(Option.OBSERVE, 0xdf).byteArrayLength());
        assertEquals(2, new Option(Option.OBSERVE, 0x1337).byteArrayLength());
        assertEquals(3, new Option(Option.OBSERVE, 0xc0ffee).byteArrayLength());
        assertEquals(4, new Option(Option.OBSERVE, 0xdeafbeef).byteArrayLength());
    }

    @Test
    void getIntegerValue() {
        assertEquals(0x00, new Option(Option.OBSERVE, 0x00).intValue());
        assertEquals(0xdf, new Option(Option.OBSERVE, 0xdf).intValue());
        assertEquals(0x1337, new Option(Option.OBSERVE, 0x1337).intValue());
        assertEquals(0xc0ffee, new Option(Option.OBSERVE, 0xc0ffee).intValue());
        assertEquals(0xdeafbeef, new Option(Option.OBSERVE, 0xdeafbeef).intValue());
    }

    @Test
    void getStringValue() {
        assertEquals("", new Option(Option.URI_PATH, "").stringValue());
        assertEquals("FOO", new Option(Option.URI_PATH, "FOO").stringValue());
        assertEquals("FOO", new Option(Option.URI_PATH, 0x464F4F).stringValue());
        assertEquals(
                "Hence! Home you idle creatures, get you home!",
                new Option(Option.URI_PATH, "Hence! Home you idle creatures, get you home!")
                        .stringValue());
    }
}
