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

/** Organizational class containing static constants for various CoAP message <em>codes</em>. */
@SuppressWarnings("unused")
public final class Code {
    // This class is not instantiable.
    private Code() {}

    /**
     * Organizational class containing static constants for CoAP message code <em>classes</em>.
     *
     * @see Code
     */
    public final class Class {
        // This class is not instantiable.
        private Class() {}

        /** CoAP message code class for request methods. */
        public static final int METHOD = 0;

        /** CoAP message code class for response success. */
        public static final int SUCCESS = 2;

        /** CoAP message code class for response errors originating from the client. */
        public static final int CLIENT_ERROR = 4;

        /** CoAP message code class for response errors originating from the server. */
        public static final int SERVER_ERROR = 5;

        /** CoAP message code class for transport signaling. */
        public static final int SIGNAL = 7;
    }

    /** Empty message code. */
    public static final int EMPTY = 0;

    /** Request Method: GET. */
    public static final int METHOD_GET = 1;

    /** Request Method: POST. */
    public static final int METHOD_POST = 2;

    /** Request Method: PUT. */
    public static final int METHOD_PUT = 3;

    /** Request Method: DELETE. */
    public static final int METHOD_DELETE = 4;

    /** Request Method: FETCH. */
    public static final int METHOD_FETCH = 5;

    /** Request Method: PATCH. */
    public static final int METHOD_PATCH = 6;

    /** Request Method: iPATCH. */
    public static final int METHOD_IPATCH = 7;

    /** Response Success: Created. */
    public static final int RESPONSE_CREATED = (Class.SUCCESS << 5) + 1;

    /** Response Success: Deleted. */
    public static final int RESPONSE_DELETED = (Class.SUCCESS << 5) + 2;

    /** Response Success: Valid. */
    public static final int RESPONSE_VALID = (Class.SUCCESS << 5) + 3;

    /** Response Success: Changed. */
    public static final int RESPONSE_CHANGED = (Class.SUCCESS << 5) + 4;

    /** Response Success: Content. */
    public static final int RESPONSE_CONTENT = (Class.SUCCESS << 5) + 5;

    /** Response Success: Continue. */
    public static final int RESPONSE_CONTINUE = (Class.SUCCESS << 5) + 31;

    /** Response Client Error: Bad Request. */
    public static final int RESPONSE_BAD_REQUEST = (Class.CLIENT_ERROR << 5);

    /** Response Client Error: Unauthorized. */
    public static final int RESPONSE_UNAUTHORIZED = (Class.CLIENT_ERROR << 5) + 1;

    /** Response Client Error: Bad Option. */
    public static final int RESPONSE_BAD_OPTION = (Class.CLIENT_ERROR << 5) + 2;

    /** Response Client Error: Forbidden. */
    public static final int RESPONSE_FORBIDDEN = (Class.CLIENT_ERROR << 5) + 3;

    /** Response Client Error: Not Found. */
    public static final int RESPONSE_NOT_FOUND = (Class.CLIENT_ERROR << 5) + 4;

    /** Response Client Error: Method Not Allowed. */
    public static final int RESPONSE_METHOD_NOT_ALLOWED = (Class.CLIENT_ERROR << 5) + 5;

    /** Response Client Error: Not Acceptable. */
    public static final int RESPONSE_NOT_ACCEPTABLE = (Class.CLIENT_ERROR << 5) + 6;

    /** Response Client Error: Request Entity Incomplete. */
    public static final int RESPONSE_REQUEST_ENTITY_INCOMPLETE = (Class.CLIENT_ERROR << 5) + 8;

    /** Response Client Error: Precondition Failed. */
    public static final int RESPONSE_PRECONDITION_FAILED = (Class.CLIENT_ERROR << 5) + 12;

    /** Response Client Error: Entity Too Large. */
    public static final int RESPONSE_REQUEST_ENTITY_TOO_LARGE = (Class.CLIENT_ERROR << 5) + 13;

    /** Response Client Error: Unsupported Content Format. */
    public static final int RESPONSE_UNSUPPORTED_CONTENT_FORMAT = (Class.CLIENT_ERROR << 5) + 15;

    /** Response Client Error: Unprocessable Entity. From RFC8132 */
    public static final int RESPONSE_UNPROCESSABLE_ENTITY = (Class.CLIENT_ERROR << 5) + 22;

    /** Response Server Error: Internal Server Error. */
    public static final int RESPONSE_INTERNAL_SERVER_ERROR = (Class.SERVER_ERROR << 5);

    /** Response Server Error: Not Implemented. */
    public static final int RESPONSE_NOT_IMPLEMENTED = (Class.SERVER_ERROR << 5) + 1;

    /** Response Server Error: Bad Gateway. */
    public static final int RESPONSE_BAD_GATEWAY = (Class.SERVER_ERROR << 5) + 2;

    /** Response Server Error: Service Unavailable. */
    public static final int RESPONSE_SERVICE_UNAVAILABLE = (Class.SERVER_ERROR << 5) + 3;

    /** Response Server Error: Gateway Timeout. */
    public static final int RESPONSE_GATEWAY_TIMEOUT = (Class.SERVER_ERROR << 5) + 4;

    /** Response Server Error: Proxying Not Supported. */
    public static final int RESPONSE_PROXYING_NOT_SUPPORTED = (Class.SERVER_ERROR << 5) + 5;

    /** Signal: CSM. */
    public static final int SIGNAL_CSM = (Class.SIGNAL << 5) + 1;

    /** Signal: Ping. */
    public static final int SIGNAL_PING = (Class.SIGNAL << 5) + 2;

    /** Signal: Pong. */
    public static final int SIGNAL_PONG = (Class.SIGNAL << 5) + 3;

    /** Signal: Release. */
    public static final int SIGNAL_RELEASE = (Class.SIGNAL << 5) + 4;

    /** Signal: Abort. */
    public static final int SIGNAL_ABORT = (Class.SIGNAL << 5) + 5;

    /** Builds a code number from the class and detail components. */
    public static int of(int c, int d) {
        return (c << 5) + d;
    }

    /** Returns the class of the given code. */
    public static int classValue(int code) {
        return (code >> 5) & 0x7;
    }

    /** Returns the detail of the given code. */
    public static int detailValue(int code) {
        return code & 0x1F;
    }

    /** Returns the code as a string in the standard CoAP X.XX format. */
    public static String toNumericString(int code) {
        return String.format("%d.%02d", classValue(code), detailValue(code));
    }

    /**
     * Returns a textual representation of the given code, if available. If not, returns the code in
     * the standard CoAP X.XX format.
     */
    public static String toString(int code) {
        switch (code) {
            case EMPTY:
                return "EMPTY";

            case METHOD_GET:
                return "GET";

            case METHOD_POST:
                return "POST";

            case METHOD_PUT:
                return "PUT";

            case METHOD_DELETE:
                return "DELETE";

            case METHOD_FETCH:
                return "FETCH";

            case METHOD_PATCH:
                return "PATCH";

            case METHOD_IPATCH:
                return "iPATCH"; /* Yep, they really made the 'i' lower case. */

            case RESPONSE_CREATED:
                return "CREATED";

            case RESPONSE_DELETED:
                return "DELETED";

            case RESPONSE_VALID:
                return "VALID";

            case RESPONSE_CHANGED:
                return "CHANGED";

            case RESPONSE_CONTENT:
                return "CONTENT";

            case RESPONSE_CONTINUE:
                return "CONTINUE";

            case RESPONSE_BAD_REQUEST:
                return "BAD_REQUEST";

            case RESPONSE_UNAUTHORIZED:
                return "UNAUTHORIZED";

            case RESPONSE_BAD_OPTION:
                return "BAD_OPTION";

            case RESPONSE_FORBIDDEN:
                return "FORBIDDEN";

            case RESPONSE_NOT_FOUND:
                return "NOT_FOUND";

            case RESPONSE_METHOD_NOT_ALLOWED:
                return "METHOD_NOT_ALLOWED";

            case RESPONSE_NOT_ACCEPTABLE:
                return "NOT_ACCEPTABLE";

            case RESPONSE_REQUEST_ENTITY_INCOMPLETE:
                return "REQUEST_ENTITY_INCOMPLETE";

            case RESPONSE_PRECONDITION_FAILED:
                return "PRECONDITION_FAILED";

            case RESPONSE_REQUEST_ENTITY_TOO_LARGE:
                return "REQUEST_ENTITY_TOO_LARGE";

            case RESPONSE_UNSUPPORTED_CONTENT_FORMAT:
                return "UNSUPPORTED_CONTENT_FORMAT";

            case RESPONSE_INTERNAL_SERVER_ERROR:
                return "INTERNAL_SERVER_ERROR";

            case RESPONSE_NOT_IMPLEMENTED:
                return "NOT_IMPLEMENTED";

            case RESPONSE_BAD_GATEWAY:
                return "BAD_GATEWAY";

            case RESPONSE_SERVICE_UNAVAILABLE:
                return "SERVICE_UNAVAILABLE";

            case RESPONSE_GATEWAY_TIMEOUT:
                return "GATEWAY_TIMEOUT";

            case RESPONSE_PROXYING_NOT_SUPPORTED:
                return "PROXYING_NOT_SUPPORTED";

            case RESPONSE_UNPROCESSABLE_ENTITY:
                return "UNPROCESSABLE_ENTITY";

            case SIGNAL_CSM:
                return "CSM";

            case SIGNAL_PING:
                return "PING";

            case SIGNAL_PONG:
                return "PONG";

            case SIGNAL_RELEASE:
                return "RELEASE";

            case SIGNAL_ABORT:
                return "ABORT";

            default:
                return toNumericString(code);
        }
    }

    /** Determines if the given code represents a request or not. */
    public static boolean isRequest(int code) {
        return (code != 0) && (classValue(code) == Class.METHOD);
    }

    /** Determines if the given code represents a response or not. */
    public static boolean isResponse(int code) {
        int c = classValue(code);
        return (c >= Class.SUCCESS) && (c < Class.SIGNAL);
    }

    /** Determines if the given number is a valid CoAP message code. */
    public static boolean isValid(int code) {
        return (code >= 0) && (code <= of(7, 31));
    }
}
