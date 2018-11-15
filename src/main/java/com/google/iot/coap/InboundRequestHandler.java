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
 * An object which handles inbound requests via a {@link Server} instance.
 *
 * @see InboundRequest
 * @see Server#setRequestHandler(InboundRequestHandler)
 * @see Resource
 * @see WellKnownCoreHandler
 */
public interface InboundRequestHandler {

    /**
     * Handles a inbound request. If a response action has not been indicated via the <code>
     * inboundRequest</code> object by the time this method returns, then the caller may
     * automatically respond on the callee's behalf as appropriate.
     *
     * <p>Note that the inboundRequest object is only valid for the scope of this call unless the
     * method {@link InboundRequest#responsePending()} has been called on it.
     *
     * @param inboundRequest an object used to get information about the request and to perform
     *     associated actions such as sending a response
     * @see #onInboundRequestCheck(InboundRequest)
     */
    void onInboundRequest(InboundRequest inboundRequest);

    /**
     * Allows the handler to get a peek at an inbound {@link Option#BLOCK1} request packets as they
     * are being reassembled. This can be used to cause a block1 request to be rejected early
     * without wasting expensive resources to reassemble a packet which would be rejected anyway. It
     * can also be used for incrementally updating a user interface as blocks arrive, rather than
     * waiting for the entire reassembled message to arrive on {@link #onInboundRequest}.
     *
     * <p>This method will only be called if fully processing the request requires significant state
     * to be maintained, such as for block1 reassembly.
     *
     * <p>Calling any of the action methods on the inboundRequest object will prevent this request
     * from being reassembled. This should only be done to indicate an error, or to take over
     * handling things like block reassembly.
     *
     * <p>The default implementation of this method does nothing.
     *
     * @param inboundRequest an object used to get information about the request and to perform
     *     associated actions such as sending a response
     * @see Option#BLOCK1
     * @see #onInboundRequest(InboundRequest)
     */
    default void onInboundRequestCheck(InboundRequest inboundRequest) {}
}
