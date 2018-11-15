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
 * Thrown by methods of {@link InboundRequest} if the calling scope is not recognized.
 *
 * <p>This will be thrown if {@link InboundRequest} is used from an unexpected thread when {@link
 * InboundRequest#responsePending()} wasn't previously called on the original thread handling the
 * InboundRequest.
 *
 * <p>While this exception will not be thrown 100% of the time when {@link InboundRequest} is abused
 * way described above, if it is ever thrown then it indicates that there is a bug in the code using
 * the {@link InboundRequest}.
 *
 * @see InboundRequest#responsePending()
 */
public final class InboundRequestOutOfScopeException extends CoapRuntimeException {
    InboundRequestOutOfScopeException(String reason) {
        super(reason);
    }
}
