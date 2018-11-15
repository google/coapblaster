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

/** Organizational class containing static constants for various CoAP <em>content formats</em>. */
@SuppressWarnings("unused")
public abstract class ContentFormat {
    /** From RFC7252 */
    public static final int TEXT_PLAIN_UTF8 = 0;

    /** From RFC7252, RFC6690 */
    public static final int APPLICATION_LINK_FORMAT = 40;

    /** From RFC7252 */
    public static final int APPLICATION_XML = 41;

    /** From RFC7252 */
    public static final int APPLICATION_OCTET_STREAM = 42;

    /** From RFC7252 */
    public static final int APPLICATION_EXI = 47;

    /** From RFC7252 */
    public static final int APPLICATION_JSON = 50;

    /** From RFC7049 */
    public static final int APPLICATION_CBOR = 60;

    /** From RFC8152 */
    public static final int APPLICATION_COSE_COSE_ENCRYPT0 = 16;

    /** From RFC8152 */
    public static final int APPLICATION_COSE_COSE_MAC0 = 17;

    /** From RFC8152 */
    public static final int APPLICATION_COSE_COSE_SIGN1 = 18;

    /** From RFC8152 */
    public static final int APPLICATION_COSE_COSE_ENCRYPT = 96;

    /** From RFC8152 */
    public static final int APPLICATION_COSE_COSE_MAC = 97;

    /** From RFC8152 */
    public static final int APPLICATION_COSE_COSE_SIGN = 98;

    /** From RFC8152 */
    public static final int APPLICATION_COSE_KEY = 101;

    /** From RFC8152 */
    public static final int APPLICATION_COSE_KEY_SET = 102;

    private ContentFormat() {}

    public static boolean isValid(int cf) {
        return cf >= 0;
    }

    public static String toString(int cf) {
        if (!isValid(cf)) {
            throw new IllegalArgumentException("Negative content format");
        }

        switch (cf) {
            case TEXT_PLAIN_UTF8:
                return "text/plain;charset=utf-8";
            case APPLICATION_LINK_FORMAT:
                return "application/link-format";
            case APPLICATION_XML:
                return "application/xml";
            case APPLICATION_OCTET_STREAM:
                return "application/octet-stream";
            case APPLICATION_EXI:
                return "application/exi";
            case APPLICATION_JSON:
                return "application/json";
            case APPLICATION_CBOR:
                return "application/cbor";
            case APPLICATION_COSE_COSE_ENCRYPT0:
                return "application/cose;cose-type=\"cose-encrypt0\"";
            case APPLICATION_COSE_COSE_MAC0:
                return "application/cose;cose-type=\"cose-mac0\"";
            case APPLICATION_COSE_COSE_SIGN1:
                return "application/cose;cose-type=\"cose-sign1\"";
            case APPLICATION_COSE_COSE_ENCRYPT:
                return "application/cose;cose-type=\"cose-encrypt\"";
            case APPLICATION_COSE_COSE_MAC:
                return "application/cose;cose-type=\"cose-mac\"";
            case APPLICATION_COSE_COSE_SIGN:
                return "application/cose;cose-type=\"cose-sign\"";
            case APPLICATION_COSE_KEY:
                return "application/cose-key";
            case APPLICATION_COSE_KEY_SET:
                return "application/cose-key-set";
            default:
                return "application/octet-stream;content-format=" + cf;
        }
    }
}
