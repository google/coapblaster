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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

public class RemoteServerTest {
    private static final boolean DEBUG = true;
    private static final Logger LOGGER =
            Logger.getLogger(RemoteServerTest.class.getCanonicalName());

    @Test
    void testClientGetCoapMe_test() throws Exception {
        try (LocalEndpointManager manager = new LocalEndpointManager()) {
            manager.setDefaultBehaviorContext(
                    new BehaviorContextPassthru(BehaviorContext.standard()) {
                        @Override
                        public int getCoapAckTimeoutMs() {
                            return 250;
                        }
                    });
            Client client = new Client(manager, "coap://coap.me/test");
            try {
                Transaction transaction = client.newRequestBuilder().send();

                Message response = transaction.getResponse(3000);

                if (DEBUG) LOGGER.info("Got response: " + response);

                assertTrue(response.isValid());
                assertEquals(Type.ACK, response.getType());
                assertEquals(Code.RESPONSE_CONTENT, response.getCode());

            } finally {
                client.cancelAllTransactions();
            }
        }
    }

    @Test
    void testClientGetCoapMe_large() throws Exception {
        try (LocalEndpointManager manager = new LocalEndpointManager()) {
            manager.setDefaultBehaviorContext(
                    new BehaviorContextPassthru(BehaviorContext.standard()) {
                        @Override
                        public int getCoapAckTimeoutMs() {
                            return 250;
                        }
                    });
            Client client = new Client(manager, "coap://coap.me/large");
            try {
                Transaction transaction = client.newRequestBuilder().send();

                Message response = transaction.getResponse(3000);

                if (DEBUG) {
                    LOGGER.info("Got response: " + response);
                }
                LOGGER.info(response.getPayloadAsString());

                assertTrue(response.isValid());
                assertEquals(Type.ACK, response.getType());
                assertEquals(Code.RESPONSE_CONTENT, response.getCode());

            } finally {
                client.cancelAllTransactions();
            }
        }
    }

    @Test
    void testClientGetCoapMe_ping() throws Exception {
        try (LocalEndpointManager manager = new LocalEndpointManager()) {
            manager.setDefaultBehaviorContext(
                    new BehaviorContextPassthru(BehaviorContext.standard()) {
                        @Override
                        public int getCoapAckTimeoutMs() {
                            return 250;
                        }
                    });
            Client client = new Client(manager, "coap://coap.me/");
            try {
                Transaction transaction = client.ping();

                Message response = transaction.getResponse(3000);

                if (DEBUG) LOGGER.info("Got response: " + response);

                assertTrue(response.isValid());
                assertTrue(response.isEmpty());
                assertEquals(Type.RST, response.getType());

            } finally {
                client.cancelAllTransactions();
            }
        }
    }

    @Test
    void testClientGetCoapMe_separate() throws Exception {
        try (LocalEndpointManager manager = new LocalEndpointManager()) {
            manager.setDefaultBehaviorContext(
                    new BehaviorContextPassthru(BehaviorContext.standard()) {
                        @Override
                        public int getCoapAckTimeoutMs() {
                            return 250;
                        }
                    });
            Client client = new Client(manager, "coap://coap.me/separate");
            try {
                Transaction transaction = client.newRequestBuilder().send();

                Message response = transaction.getResponse(6000);

                LOGGER.info("Got response: " + response);

                assertFalse(response.isEmptyAck());
                assertTrue(response.isValid());

                assertEquals(Code.RESPONSE_CONTENT, response.getCode());
                assertEquals(Type.CON, response.getType());

            } finally {
                client.cancelAllTransactions();
            }
        }
    }
}
