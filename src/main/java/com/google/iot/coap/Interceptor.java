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
 * Interface for peeking at the inbound and outbound messages on a {@link LocalEndpoint}, possibly
 * choosing to drop them.
 *
 * @see LocalEndpoint#setInterceptor(Interceptor)
 */
public interface Interceptor {

    /**
     * Hook which gets called right before an outbound {@link Message} is serialized.
     *
     * @param message the outbound message
     * @return true if the message should be dropped, false if the message should proceed normally
     */
    boolean onInterceptOutbound(Message message);

    /**
     * Hook which gets called right after an inbound {@link Message} is parsed.
     *
     * @param message the inbound message
     * @return true if the message should be dropped, false if the message should proceed normally
     */
    boolean onInterceptInbound(Message message);
}
