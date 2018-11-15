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

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import org.junit.jupiter.api.Test;

class KeyMidTest {

    @Test
    void equals() throws Exception {
        SocketAddress socketA;
        SocketAddress socketB;
        KeyMid keyA;
        KeyMid keyB;

        socketA = NullSocketAddress.create("testhost", 123, false);
        keyA = new KeyMid(1234, socketA);
        socketB = NullSocketAddress.create("testhost", 123, false);
        keyB = new KeyMid(1234, socketB);
        assertEquals(keyA, keyB);
        assertEquals(keyB, keyA);
        assertEquals(keyA.hashCode(), keyB.hashCode());

        keyB = new KeyMid(5678, socketB);
        assertNotEquals(keyA, keyB);
        assertNotEquals(keyB, keyA);

        socketB = NullSocketAddress.create("testhost", 124, false);
        keyB = new KeyMid(1234, socketB);
        assertNotEquals(keyA, keyB);
        assertNotEquals(keyB, keyA);

        socketB = NullSocketAddress.create("testhost-two", 123, false);
        keyB = new KeyMid(1234, socketB);
        assertNotEquals(keyA, keyB);
        assertNotEquals(keyB, keyA);

        socketA = new InetSocketAddress(InetAddress.getByAddress(new byte[] {1, 2, 3, 4}), 123);
        keyA = new KeyMid(1234, socketA);
        socketB = new InetSocketAddress(InetAddress.getByAddress(new byte[] {1, 2, 3, 4}), 123);
        keyB = new KeyMid(1234, socketB);
        assertEquals(keyA, keyB);
        assertEquals(keyB, keyA);
        assertEquals(keyA.hashCode(), keyB.hashCode());
        assertEquals(keyA.toString(), keyB.toString());

        socketB = new InetSocketAddress(InetAddress.getByAddress(new byte[] {1, 2, 3, 5}), 123);
        keyB = new KeyMid(1234, socketB);
        assertNotEquals(keyA, keyB);
        assertNotEquals(keyB, keyA);

        socketB = new InetSocketAddress(InetAddress.getByAddress(new byte[] {1, 2, 3, 5}), 124);
        keyB = new KeyMid(1234, socketB);
        assertNotEquals(keyA, keyB);
        assertNotEquals(keyB, keyA);

        socketB =
                new InetSocketAddress(
                        InetAddress.getByAddress("hostname", new byte[] {1, 2, 3, 4}), 123);
        keyB = new KeyMid(1234, socketB);
        assertEquals(keyA, keyB);
        assertEquals(keyB, keyA);
        assertEquals(keyA.hashCode(), keyB.hashCode());
        assertNotEquals(keyA.toString(), keyB.toString());

        socketA =
                new InetSocketAddress(
                        InetAddress.getByAddress("hostname", new byte[] {1, 2, 3, 4}), 123);
        keyA = new KeyMid(1234, socketA);
        socketB =
                new InetSocketAddress(
                        InetAddress.getByAddress("hostname", new byte[] {1, 2, 3, 5}), 124);
        keyB = new KeyMid(1234, socketB);
        assertNotEquals(keyA, keyB);
        assertNotEquals(keyB, keyA);
    }
}
