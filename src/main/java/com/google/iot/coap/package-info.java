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
/**
 * CoapBlaster: A experimental <a href="https://tools.ietf.org/html/rfc7252">CoAP</a> client/server
 * library written in Java.
 *
 * <p>The most important classes in this package are:
 *
 * <ul>
 *   <li>{@link com.google.iot.coap.Message}: A CoAP message
 *   <li>{@link com.google.iot.coap.Client}: A CoAP client
 *       <ul>
 *         <li>{@link com.google.iot.coap.Transaction}: Tracks an outbound request
 *       </ul>
 *   <li>{@link com.google.iot.coap.Server}: A CoAP server
 *       <ul>
 *         <li>{@link com.google.iot.coap.InboundRequest}: Represents an inbound request
 *         <li>{@link com.google.iot.coap.InboundRequestHandler}: Handles inbound requests
 *         <li>{@link com.google.iot.coap.Resource}: Special InboundRequestHandler for folders
 *         <li>{@link com.google.iot.coap.Observable}: Helper for implementing observable resources
 *       </ul>
 * </ul>
 *
 * @see <a href="https://github.com/google/coapblaster">CoapBlaster Github Page</a>
 * @see <a href="https://tools.ietf.org/html/rfc7252">RFC7252: The Constrained Application Protocol
 *     (CoAP)</a>
 */
@CheckReturnValue
@ParametersAreNonnullByDefault
package com.google.iot.coap;

import com.google.errorprone.annotations.CheckReturnValue;
import javax.annotation.ParametersAreNonnullByDefault;
