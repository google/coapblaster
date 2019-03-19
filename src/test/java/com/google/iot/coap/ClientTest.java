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
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

@SuppressWarnings("ConstantConditions")
class ClientTest {
    private static final boolean DEBUG = false;
    private static final Logger LOGGER = Logger.getLogger(ClientTest.class.getCanonicalName());

    private final LocalEndpointManager mManager = new LocalEndpointManager();

    @Test
    void testClientLoopbackPing() throws Exception {
        Client client = new Client(mManager, "loop://localhost/");
        try {
            assertEquals(Type.RST, client.ping().getResponse(1500).getType());
        } finally {
            client.cancelAllTransactions();
        }
    }

    @Test
    void testClientNullPing() throws Exception {
        Client client = new Client(mManager, "null://localhost/");
        try {
            assertThrows(TimeoutException.class, () -> client.ping().getResponse(500));
        } finally {
            client.cancelAllTransactions();
        }
    }

    @Test
    void getActiveTransactions() throws Exception {
        Client client = new Client(mManager, "null://localhost/");
        try {
            assertEquals(0, client.getActiveTransactions().size());

            Transaction transaction1 = client.ping();

            assertEquals(1, client.getActiveTransactions().size());

            Transaction transaction2 = client.ping();

            assertEquals(2, client.getActiveTransactions().size());

            transaction1.cancel();

            assertThrows(CancellationException.class, () -> transaction1.getResponse(10));

            assertEquals(1, client.getActiveTransactions().size());

            transaction2.cancel();

            assertThrows(CancellationException.class, () -> transaction2.getResponse(10));

            assertEquals(0, client.getActiveTransactions().size());
        } finally {
            client.cancelAllTransactions();
        }
    }

    @Test
    void cancelAllTransactions() throws Exception {
        Client client = new Client(mManager, "null://localhost/");
        try {
            Transaction transaction = client.ping();

            client.cancelAllTransactions();

            assertThrows(CancellationException.class, () -> transaction.getResponse(1500));
        } finally {
            client.cancelAllTransactions();
        }
    }

    @Test
    void setProxy() throws Exception {
        Client client = new Client(mManager, "coap://coap.me/test");

        client.setProxySelector(ProxySelector.alwaysProxy(URI.create("loop://localhost")));

        try {
            Transaction transaction = client.newRequestBuilder().send();

            assertEquals(
                    "coap://coap.me/test",
                    transaction.getRequest().getOptionSet().getProxyUri().toString());

            Message response = transaction.getResponse(1500);

            if (DEBUG) LOGGER.info("Got response: " + response);

            assertTrue(response.isValid());
            assertEquals(Type.ACK, response.getType());
            assertEquals(Code.RESPONSE_PROXYING_NOT_SUPPORTED, response.getCode());
        } finally {
            client.cancelAllTransactions();
        }
    }
}
