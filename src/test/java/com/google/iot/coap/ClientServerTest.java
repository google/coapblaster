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

import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

@SuppressWarnings("ConstantConditions")
public class ClientServerTest {
    private static final boolean DEBUG = false;
    private static final Logger LOGGER =
            Logger.getLogger(ClientServerTest.class.getCanonicalName());

    @Test
    void testClientServerBasic1() throws Exception {
        try (LocalEndpointManager manager = new LocalEndpointManager()) {
            Client client = new Client(manager, "loop://localhost/");

            try (Server server = new Server(manager)) {
                server.addLocalEndpoint(manager.getLocalEndpointForScheme("loop"));
                server.start();

                Transaction transaction = client.newRequestBuilder().send();

                Message response = transaction.getResponse(1500);

                if (DEBUG) LOGGER.info("Got response: " + response);

                assertEquals(Code.RESPONSE_NOT_IMPLEMENTED, response.getCode());
            }
        }
    }

    @Test
    void testClientServerBasic2() throws Exception {
        try (LocalEndpointManager manager = new LocalEndpointManager()) {
            Client client = new Client(manager, "loop://localhost/");

            try (Server server = new Server(manager)) {
                LocalEndpoint localEndpoint = manager.getLocalEndpointForScheme("loop");

                server.addLocalEndpoint(localEndpoint);

                Resource<InboundRequestHandler> root = new Resource<>();
                Resource<InboundRequestHandler> d1 = new Resource<>();
                Resource<InboundRequestHandler> d2 = new Resource<>();

                d1.addChild("d2", d2);
                root.addChild("d1", d1);
                root.addChild(
                        "hello",
                        new InboundRequestHandler() {
                            @Override
                            public void onInboundRequest(InboundRequest inboundRequest) {
                                Message request = inboundRequest.getMessage();
                                if (request.getCode() != Code.METHOD_GET) {
                                    inboundRequest.sendSimpleResponse(
                                            Code.RESPONSE_METHOD_NOT_ALLOWED);
                                } else {
                                    inboundRequest.sendSimpleResponse(
                                            Code.RESPONSE_CONTENT, "Hello, World!");
                                }
                            }

                            @Override
                            public void onInboundRequestCheck(InboundRequest inboundRequest) {
                                /* Do nothing */
                            }
                        });

                server.setRequestHandler(root);

                server.start();

                Transaction transaction = client.newRequestBuilder().send();

                Message response = transaction.getResponse(1500);

                if (DEBUG) LOGGER.info("Got response: " + response);

                assertEquals(
                        Code.toString(Code.RESPONSE_CONTENT), Code.toString(response.getCode()));

                transaction = client.newRequestBuilder().changePath("hello").send();

                response = transaction.getResponse(1500);

                if (DEBUG) LOGGER.info("Got response: " + response);

                assertEquals(
                        Code.toString(Code.RESPONSE_CONTENT), Code.toString(response.getCode()));

                assertEquals("Hello, World!", response.getPayloadAsString());

                transaction = client.newRequestBuilder().changePath("d1/d2").send();

                response = transaction.getResponse(1500);

                if (DEBUG) LOGGER.info("Got response: " + response);

                assertEquals(
                        Code.toString(Code.RESPONSE_BAD_REQUEST),
                        Code.toString(response.getCode()));

                transaction = client.newRequestBuilder().changePath("d1/d2/").send();

                response = transaction.getResponse(1500);

                if (DEBUG) LOGGER.info("Got response: " + response);

                assertEquals(
                        Code.toString(Code.RESPONSE_CONTENT), Code.toString(response.getCode()));

                transaction = client.newRequestBuilder().changePath("d1/d3").send();

                response = transaction.getResponse(1500);

                if (DEBUG) LOGGER.info("Got response: " + response);

                assertEquals(
                        Code.toString(Code.RESPONSE_NOT_FOUND), Code.toString(response.getCode()));
            }
        }
    }
}
