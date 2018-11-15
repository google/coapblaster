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

import java.net.SocketAddress;
import org.checkerframework.checker.nullness.qual.Nullable;

interface TransactionLookup {

    Token newToken(
            SocketAddress socketAddress, @Nullable OutboundMessageHandler outboundMessageHandler);

    void registerTransaction(MutableMessage message, @Nullable OutboundMessageHandler handler);

    OutboundMessageHandler lookupTransaction(Message message);

    int getNextMidForSocketAddress(SocketAddress socketAddress);

    void reset();

    void cleanup();
}
