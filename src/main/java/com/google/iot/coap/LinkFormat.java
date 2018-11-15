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

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Classes related to creating and parsing Link Format resources, as defined by <a
 * href="https://tools.ietf.org/html/rfc6690">RFC6690</a>.
 *
 * @see <a href="https://tools.ietf.org/html/rfc6690">RFC6690</a>
 */
public final class LinkFormat {
    private static final boolean DEBUG = false;
    private static final Logger LOGGER = Logger.getLogger(LinkFormat.class.getCanonicalName());

    /**
     * Relation Type. Optional in <a href="https://tools.ietf.org/html/rfc6690">RFC6690</a> link
     * format resources.
     *
     * @see <a href="https://tools.ietf.org/html/rfc8288#section-3.3">RFC8288, Section 3.3</a>
     */
    public static final String PARAM_REL = "rel";

    /**
     * Anchor attribute. Provides an override of the document context URI when parsing relative URIs
     * in the links. The value itself may be a relative URI, which is evaluated against the document
     * context URI.
     *
     * @see <a href="https://tools.ietf.org/html/rfc8288#section-3.2">RFC8288, Section 3.2</a>
     */
    public static final String PARAM_ANCHOR = "anchor";

    /**
     * A hint indicating what the language of the result of dereferencing the link should be.
     *
     * @see <a href="https://tools.ietf.org/html/rfc8288#section-3.4.1">RFC8288, Section 3.4.1</a>
     */
    @SuppressWarnings("unused")
    public static final String PARAM_HREFLANG = "hreflang";

    /**
     * Media Attribute, used to indicate intended destination medium or media for style information.
     *
     * @see <a href="https://tools.ietf.org/html/rfc8288#section-3.4.1">RFC8288, Section 3.4.1</a>
     */
    @SuppressWarnings("unused")
    public static final String PARAM_MEDIA = "media";

    /**
     * Human-readable label describing the resource.
     *
     * @see <a href="https://tools.ietf.org/html/rfc8288#section-3.4.1">RFC8288, Section 3.4.1</a>
     */
    public static final String PARAM_TITLE = "title";

    /**
     * Human-readable label describing the resource, along with language information. Is is
     * typically formatted as <code>"utf-8'&lt;LANG_CODE&gt;'&lt;TITLE_TEXT&gt;"</code>. For
     * example:
     *
     * <ul>
     *   <li><code>"utf-8'en'£ rates"</code>
     * </ul>
     *
     * Note that since <a href="https://tools.ietf.org/html/rfc6690">RFC6690</a> requires the link
     * format serialization to always be in UTF-8 format, the value of this attribute MUST ALWAYS
     * start with either the string <code>utf-8</code> or <code>UTF-8</code> and MUST NOT be
     * percent-encoded.
     *
     * @see <a href="https://tools.ietf.org/html/rfc8288#section-3.4.1">RFC8288, Section 3.4.1</a>
     * @see <a href="https://tools.ietf.org/html/rfc8187">RFC8187</a>
     */
    @SuppressWarnings("unused")
    public static final String PARAM_TITLE_STAR = "title*";

    /**
     * @hide MIME content type attribute. This attribute should be avoided in favor of {@link
     *     #PARAM_CONTENT_FORMAT}.
     * @see <a href="https://tools.ietf.org/html/rfc8288#section-3.4.1">RFC8288, Section 3.4.1</a>
     */
    public static final String PARAM_TYPE = "type";

    /**
     * Resource Type Attribute. The Resource Type 'rt' attribute is an opaque string used to assign
     * an application-specific semantic type to a resource. One can think of this as a noun
     * describing the resource.
     *
     * @see <a href="https://tools.ietf.org/html/rfc6690#section-3.1">RFC6690, Section 3.1</a>
     */
    public static final String PARAM_RESOURCE_TYPE = "rt";

    /**
     * Interface Description Attribute. The Interface Description 'if' attribute is an opaque string
     * used to provide a name or URI indicating a specific interface definition used to interact
     * with the target resource. One can think of this as describing verbs usable on a resource.
     *
     * @see <a href="https://tools.ietf.org/html/rfc6690#section-3.2">RFC6690, Section 3.2</a>
     */
    public static final String PARAM_INTERFACE_DESCRIPTION = "if";

    /**
     * The estimated maximum size of the fetched resource. The maximum size estimate attribute 'sz'
     * gives an indication of the maximum size of the resource representation returned by performing
     * a GET on the target URI. For links to CoAP resources, this attribute is not expected to be
     * included for small resources that can comfortably be carried in a single Maximum Transmission
     * Unit (MTU) but SHOULD be included for resources larger than that. The maximum size estimate
     * attribute MUST NOT appear more than once in a link.
     *
     * @see <a href="https://tools.ietf.org/html/rfc6690#section-3.3">RFC6690, Section 3.3</a>
     */
    public static final String PARAM_MAXIMUM_SIZE_ESTIMATE = "sz";

    /**
     * The value of this resource expressed as a human-readable string. Must be less than 63 bytes.
     */
    public static final String PARAM_VALUE = "v";

    /**
     * Content-Format Code(s). Space-separated list of content type integers appropriate for being
     * specified in an Accept option.
     *
     * @see <a href="https://tools.ietf.org/html/rfc7252#section-7.2.1">RFC7252, Section 7.2.1</a>
     */
    public static final String PARAM_CONTENT_FORMAT = "ct";

    /**
     * Identifies this resource as observable if present.
     *
     * @see <a href="https://tools.ietf.org/html/rfc7641#section-6">RFC7641, Section 6</a>
     */
    public static final String PARAM_OBSERVABLE = "obs";

    /**
     * Name of the endpoint, max 63 bytes.
     *
     * @see <a href="https://goo.gl/6e2s7C#section-5.3">draft-ietf-core-resource-directory-14</a>
     */
    public static final String PARAM_ENDPOINT_NAME = "ep";

    /**
     * Lifetime of the registration in seconds. Valid values are between 60-4294967295, inclusive.
     *
     * @see <a href="https://goo.gl/6e2s7C#section-5.3">draft-ietf-core-resource-directory-14</a>
     */
    @SuppressWarnings("unused")
    public static final String PARAM_LIFETIME = "lt";

    /**
     * Sector to which this endpoint belongs. Must be less than 63 bytes.
     *
     * @see <a href="https://goo.gl/6e2s7C#section-5.3">draft-ietf-core-resource-directory-14</a>
     */
    @SuppressWarnings("unused")
    public static final String PARAM_SECTOR = "d";

    /**
     * The scheme, address and point and path at which this server is available. MUST be a valid
     * URI.
     *
     * @see <a href="https://goo.gl/6e2s7C#section-5.3">draft-ietf-core-resource-directory-14</a>
     */
    @SuppressWarnings("unused")
    public static final String PARAM_REGISTRATION_BASE_URI = "base";

    /**
     * Name of a group in this RD. Must be less than 63 bytes.
     *
     * @see <a href="https://goo.gl/6e2s7C#section-6.1">draft-ietf-core-resource-directory-14</a>
     */
    @SuppressWarnings("unused")
    public static final String PARAM_GROUP_NAME = "gp";

    /**
     * Semantic name of the endpoint. Must be less than 63 bytes.
     *
     * @see <a href="https://goo.gl/6e2s7C#section-10.3.1">draft-ietf-core-resource-directory-14</a>
     */
    @SuppressWarnings("unused")
    public static final String PARAM_ENDPOINT_TYPE = "et";

    /** @hide Not really a parameter; only used when making queries. */
    @SuppressWarnings("unused")
    public static final String PARAM_HREF = "href";

    /** @hide Not really a parameter; only used when making queries. */
    public static final String PARAM_PAGE = "page";

    /** @hide Not really a parameter; only used when making queries. */
    public static final String PARAM_COUNT = "count";

    /**
     * Complement interface to {@link InboundRequestHandler} that allows a parent {@link Resource}
     * object to get additional information when building link format listings.
     *
     * @see Resource
     * @see InboundRequestHandler
     * @see Observable.Provider
     */
    public interface Provider {
        /**
         * Hook to allow additional link-format parameters to be added for this child in the link
         * format.
         */
        void onBuildLinkParams(LinkBuilder builder);

        /**
         * Override to return {@code true} if this item should be hidden from the link format
         * listing.
         */
        default boolean isHiddenFromLinkList() {
            return false;
        }
    }

    /** Private constructor to prevent instantiation. */
    private LinkFormat() {}

    /**
     * Private method for determining if the value for a given link parameter is expected to be a
     * whitespace-separated list of values.
     *
     * @param key the key string of the parameter
     * @return true if the given key may have multiple values, false otherwise
     */
    private static boolean paramKeyContainsMultipleValues(String key) {
        switch (key) {
            case PARAM_REL:
            case PARAM_CONTENT_FORMAT:
            case PARAM_RESOURCE_TYPE:
                return true;
            default:
                return false;
        }
    }

    /**
     * Function for determing if the given character is a "ptoken" or not. If a value string
     * consists entirely of "ptoken" characters, then it does not need to be escaped.
     *
     * @param x the character to test
     * @return true if the character is a ptoken, false otherwise.
     */
    private static boolean isPtokenChar(char x) {
        if (x > 127) {
            return false;
        }
        if (Character.isDigit(x) || Character.isAlphabetic(x)) {
            return true;
        }
        switch (x) {
            case '!':
            case '#':
            case '$':
            case '%':
            case '&':
            case '\'':
            case '(':
            case ')':
            case '*':
            case '+':
            case '-':
            case '.':
            case '/':
            case ':':
            case '<':
            case '=':
            case '>':
            case '?':
            case '@':
            case '[':
            case ']':
            case '^':
            case '_':
            case '`':
            case '{':
            case '|':
            case '}':
            case '~':
                return true;
            default:
                return false;
        }
    }

    /**
     * Function for determining if the given value string needs to be escaped in the link format.
     */
    private static boolean needsEscaping(String string) {
        for (char x : string.toCharArray()) {
            if (!isPtokenChar(x)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Function for preparing a value string for being used directly in the link format, escaping it
     * if necessary.
     */
    private static String escapeValue(String string) {
        if (needsEscaping(string)) {
            String sb = "\"" + string.replace("\"", "\\\"") + "\"";
            return sb;
        }
        return string;
    }

    /**
     * Builder class for populating the attributes associated with a single link. This class is not
     * thread safe.
     */
    @SuppressWarnings("UnusedReturnValue")
    public static final class LinkBuilder {
        // All non-hidden parameters.
        final Map<String, String> mParams = new LinkedHashMap<>();

        // All parameters, including hidden parameters, to be used for filtering.
        final Map<String, String> mFilterParams = new LinkedHashMap<>();

        LinkBuilder() {}

        /**
         * Key presence test function.
         *
         * @return true if this link already has the given key, false if the key is hidden or
         *     otherwise not present.
         */
        public boolean hasKey(String key) {
            return mParams.containsKey(key);
        }

        @CanIgnoreReturnValue
        public LinkBuilder put(String key, String value) {
            return put(key, value, false);
        }

        @CanIgnoreReturnValue
        public LinkBuilder put(String key, String value, boolean hidden) {
            mFilterParams.put(key, value);
            if (!hidden) {
                mParams.put(key, value);
            }
            return this;
        }

        @CanIgnoreReturnValue
        public LinkBuilder addToRelation(String key, String value) {
            return addToRelation(key, value, false);
        }

        @CanIgnoreReturnValue
        public LinkBuilder addToRelation(String key, String value, boolean hidden) {
            if (mFilterParams.containsKey(key)) {
                mFilterParams.put(key, mFilterParams.get(key) + " " + value);
            } else {
                mFilterParams.put(key, value);
            }
            if (!hidden) {
                if (mParams.containsKey(key)) {
                    mParams.put(key, mParams.get(key) + " " + value);
                } else {
                    mParams.put(key, value);
                }
            }
            return this;
        }

        @CanIgnoreReturnValue
        public LinkBuilder setTitle(String title) {
            return put(PARAM_TITLE, title);
        }

        /** @hide */
        @CanIgnoreReturnValue
        public LinkBuilder setType(String type) {
            return put(PARAM_TYPE, type);
        }

        @CanIgnoreReturnValue
        public LinkBuilder setValue(String value) {
            return put(PARAM_VALUE, value);
        }

        @CanIgnoreReturnValue
        public LinkBuilder setObservable(boolean observable) {
            if (observable) {
                put(PARAM_OBSERVABLE, "1");
            } else {
                mParams.remove(PARAM_OBSERVABLE);
            }
            return this;
        }

        @CanIgnoreReturnValue
        public LinkBuilder addResourceType(String resourceType) {
            return addToRelation(PARAM_RESOURCE_TYPE, resourceType);
        }

        @CanIgnoreReturnValue
        public LinkBuilder addContentFormat(int ct) {
            return addToRelation(PARAM_CONTENT_FORMAT, Integer.toString(ct));
        }

        @CanIgnoreReturnValue
        public LinkBuilder setMaximumSizeEstimate(int sz) {
            return put(PARAM_MAXIMUM_SIZE_ESTIMATE, Integer.toString(sz));
        }

        @CanIgnoreReturnValue
        public LinkBuilder addInterfaceDescription(String ifDesc) {
            return addToRelation(PARAM_INTERFACE_DESCRIPTION, ifDesc);
        }

        @CanIgnoreReturnValue
        public LinkBuilder setAnchor(URI uri) {
            return put(PARAM_ANCHOR, uri.toASCIIString());
        }

        @CanIgnoreReturnValue
        public LinkBuilder useParamProvider(Provider x) {
            x.onBuildLinkParams(this);
            return this;
        }
    }

    private static int readPossiblyQuotedString(Reader reader, StringBuilder sb)
            throws IOException {
        int nextc = reader.read();

        if (nextc == '"') {
            // Quoted string.
            int lastc = nextc;
            for (nextc = reader.read(); nextc > 0; nextc = reader.read()) {
                if (nextc == '"') {
                    if (lastc != '\\') {
                        // End of the string.
                        break;
                    }
                    // Quoted quote mark.
                    sb.deleteCharAt(sb.length() - 1);
                }
                sb.append((char) nextc);
                lastc = nextc;
            }
        } else {
            // Unquoted string.
            for (;
                    nextc > 0 && nextc != ';' && nextc != ',' && !Character.isWhitespace(nextc);
                    nextc = reader.read()) {
                sb.append((char) nextc);
            }
        }

        return nextc;
    }

    public static Map<URI, Map<String, String>> parseLinkFormat(
            Reader reader, @Nullable Map<String, String> queryFilter)
            throws IOException, LinkFormatParseException {
        final Map<URI, Map<String, String>> ret = new HashMap<>();

        for (int nextc = reader.read(); nextc > 0; nextc = reader.read()) {
            if (Character.isWhitespace(nextc)) {
                continue;
            }
            if (nextc != '<') {
                throw new LinkFormatParseException(
                        String.format("Unexpected character '%c'", nextc));
            }
            StringBuilder sb = new StringBuilder();

            for (nextc = reader.read(); nextc > 0 && nextc != '>'; nextc = reader.read()) {
                sb.append((char) nextc);
            }

            if (nextc <= 0) {
                break;
            }

            final URI uri = URI.create(sb.toString());
            final Map<String, String> map = new HashMap<>();

            for (nextc = reader.read(); nextc > 0 && nextc != ','; nextc = reader.read()) {
                if (Character.isWhitespace(nextc) || nextc == ';') {
                    continue;
                }

                sb = new StringBuilder();

                for (;
                        nextc > 0 && nextc != '=' && nextc != ';' && nextc != ',';
                        nextc = reader.read()) {
                    sb.append((char) nextc);
                }

                final String key = sb.toString();

                if (nextc <= 0 || nextc == ',') {
                    break;
                }

                sb = new StringBuilder();

                if (nextc == '=') {
                    nextc = readPossiblyQuotedString(reader, sb);
                }

                map.put(key, sb.toString());

                if (nextc <= 0 || nextc == ',') {
                    break;
                }
            }

            if (DEBUG) LOGGER.info("Parsed item: <" + uri + "> " + map);

            if (!checkIsFiltered(queryFilter, uri, map)) {
                ret.put(uri, map);
            } else {
                if (DEBUG)
                    LOGGER.info(
                            "Link format item was filtered after parsing: <" + uri + "> " + map);
            }
        }

        return ret;
    }

    @SuppressWarnings("unused")
    public static Map<URI, Map<String, String>> parseLinkFormat(Reader reader)
            throws IOException, LinkFormatParseException {
        return parseLinkFormat(reader, null);
    }

    private static boolean doesValueMatch(String query, @Nullable String value) {
        if (value == null) {
            return false;
        }
        if ("*".equals(query)) {
            return true;
        }
        boolean wildcardAtEnd = !query.isEmpty() && query.charAt(query.length() - 1) == '*';
        boolean wildcardAtBegin = !query.isEmpty() && query.charAt(0) == '*';
        if (wildcardAtEnd && wildcardAtBegin) {
            String sub = query.substring(1, query.length() - 1);
            return value.contains(sub);
        } else if (wildcardAtEnd) {
            String sub = query.substring(0, query.length() - 1);
            return value.startsWith(sub);
        } else if (wildcardAtBegin) {
            String sub = query.substring(1, query.length());
            return value.endsWith(sub);
        }

        return query.equals(value);
    }

    private static boolean checkIsFiltered(
            @Nullable Map<String, String> queryFilter, URI uri, Map<String, String> params) {
        if (queryFilter == null) {
            return false;
        }

        for (Map.Entry<String, String> entry : queryFilter.entrySet()) {
            if (paramKeyContainsMultipleValues(entry.getKey())) {
                for (String query : entry.getValue().split(" *")) {
                    boolean didMatch = false;
                    for (String value : params.get(entry.getKey()).split(" *")) {
                        if (doesValueMatch(query, value)) {
                            didMatch = true;
                            break;
                        }
                    }
                    if (!didMatch) {
                        return true;
                    }
                }
            } else {
                if ("href".equals(entry.getKey())) {
                    if (!doesValueMatch(entry.getValue(), uri.toString())) {
                        return true;
                    }
                } else {
                    if (!doesValueMatch(entry.getValue(), params.get(entry.getKey()))) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * Builder class for constructing an <a href="https://tools.ietf.org/html/rfc6690">RFC6690</a>
     * formatted ("link format") list of links.
     */
    public static final class Builder {
        private final Map<URI, LinkBuilder> mMap = new LinkedHashMap<>();

        Map<String, String> mQueryFilter = null;

        boolean mAddNewlines = true;
        int mPage = 1;
        int mCount = Integer.MAX_VALUE;

        public Builder() {}

        /**
         * Indicates if the builder should emit newline characters to improve the human readability
         * of the output.
         *
         * @param x true if newlines should be added, false if newlines should not be added
         * @return this {@link Builder} instance
         */
        @CanIgnoreReturnValue
        public Builder setAddNewlines(boolean x) {
            mAddNewlines = x;
            return this;
        }

        /**
         * Sets which page of links should be build.
         *
         * @return this {@link Builder} instance
         * @see #setCount(int)
         */
        @CanIgnoreReturnValue
        Builder setPage(int page) {
            if (page > 0) {
                mPage = page;
            } else {
                mPage = 1;
            }
            return this;
        }

        /**
         * Sets the maximum number of links that can appear on one "page".
         *
         * @return this {@link Builder} instance
         * @see #setPage(int)
         */
        @CanIgnoreReturnValue
        Builder setCount(int count) {
            if (count > 0) {
                mCount = count;
            } else {
                mCount = 1;
            }
            return this;
        }

        /**
         * Adds the given link to the link format.
         *
         * @return a {@link LinkBuilder} to allow additional properties to be associated with the
         *     link.
         */
        @CanIgnoreReturnValue
        public LinkBuilder addLink(URI link) {
            LinkBuilder ret = new LinkBuilder();
            mMap.put(link, ret);
            return ret;
        }

        /**
         * Sets the <a href="https://tools.ietf.org/html/rfc6690">RFC6690</a> query parameters that
         * are used to filter the results.
         *
         * @return this {@link Builder} instance
         */
        @CanIgnoreReturnValue
        public Builder setQueryFilter(Map<String, String> queryFilter) {
            mQueryFilter = new HashMap<>(queryFilter);

            if (mQueryFilter.containsKey(PARAM_PAGE)) {
                try {
                    int page = Integer.valueOf(mQueryFilter.get(PARAM_PAGE));
                    setPage(page);
                } catch (NumberFormatException ignore) {
                    // We ignore badly formatted page integers.
                }
                mQueryFilter.remove(PARAM_PAGE);
            }

            if (mQueryFilter.containsKey(PARAM_COUNT)) {
                try {
                    int count = Integer.valueOf(mQueryFilter.get(PARAM_COUNT));
                    setCount(count);
                } catch (NumberFormatException ignore) {
                    // We ignore badly formatted count integers.
                }
                mQueryFilter.remove(PARAM_COUNT);
            }

            return this;
        }

        private boolean isFiltered(URI uri, Map<String, String> params) {
            if (mQueryFilter == null) {
                return false;
            }
            return checkIsFiltered(mQueryFilter, uri, params);
        }

        /**
         * Builds the link format as a {@link String}.
         *
         * @return this {@link Builder} instance
         */
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            boolean firstLink = true;
            int currCount = 0;

            for (Map.Entry<URI, LinkBuilder> linkEntry : mMap.entrySet()) {
                if (isFiltered(linkEntry.getKey(), linkEntry.getValue().mFilterParams)) {
                    continue;
                }

                final int i = currCount++;
                final int page = i / mCount + 1; // +1 because we are 1-based

                if (page < mPage) {
                    // Skip prior pages.
                    continue;
                } else if (page > mPage) {
                    // We are done, but there are more items to list.
                    // We can indicate this by providing a link to the
                    // next page.
                    sb.append(",\n<?count=");
                    sb.append(mCount);
                    sb.append("&page=");
                    sb.append(page);
                    if (mQueryFilter != null) {
                        for (Map.Entry<String, String> entry : mQueryFilter.entrySet()) {
                            sb.append("&");
                            sb.append(entry.getKey());
                            sb.append("=");
                            sb.append(Utils.uriEscapeString(entry.getValue()));
                        }
                    }
                    sb.append(">;rel=next");
                    break;
                }

                if (firstLink) {
                    firstLink = false;
                } else {
                    sb.append(",");
                    if (mAddNewlines) {
                        sb.append("\n");
                    }
                }
                sb.append("<");
                sb.append(linkEntry.getKey().toASCIIString());
                sb.append(">");

                for (Map.Entry<String, String> paramEntry :
                        linkEntry.getValue().mParams.entrySet()) {
                    sb.append(";");
                    sb.append(paramEntry.getKey());
                    sb.append("=");
                    sb.append(escapeValue(paramEntry.getValue()));
                }
            }

            if (mAddNewlines) {
                sb.append("\n");
            }

            return sb.toString();
        }
    }
}
