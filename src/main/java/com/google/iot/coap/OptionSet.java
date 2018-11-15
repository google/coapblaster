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
import java.net.*;
import java.util.*;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * A class for managing a set of {@link Option}s.
 *
 * <p><b>NOTE</b>: This class is will be significantly refactored at some point. It's original
 * design was inspired from the OptionSet class in Californium, but this design has proven itself to
 * be quite problematic in practice.
 */
@SuppressWarnings("UnusedReturnValue")
@CanIgnoreReturnValue
public final class OptionSet {
    private List<Etag> mIfMatchList = null;
    private List<Etag> mEtagList = null;
    private boolean mIfNoneMatch = false;

    private String mUriHost = null;
    private Integer mUriPort = null;
    private List<String> mUriPathList = null;
    private List<String> mUriQueryList = null;

    private List<String> mLocationPathList = null;
    private List<String> mLocationQueryList = null;

    private Integer mContentFormat = null;
    private Integer mAccept = null;

    private Integer mMaxAge = null;
    private Integer mObserve = null;

    private URI mProxyUri = null;
    private String mProxyScheme = null;

    private BlockOption mBlock1 = null;
    private BlockOption mBlock2 = null;
    private Integer mSize1 = null;
    private Integer mSize2 = null;

    private LinkedList<Option> mOtherOptions = null;

    private boolean mIsImmutable = false;

    public OptionSet() {}

    @SuppressWarnings("CopyConstructorMissesField")
    public OptionSet(OptionSet set) {
        addOptions(set.asSortedList());
    }

    OptionSet makeImmutable() {
        mIsImmutable = true;
        return this;
    }

    private void throwIfImmutable() {
        if (mIsImmutable) {
            throw new IllegalStateException("Attempting to mutate an immutable message");
        }
    }

    public OptionSet copy() {
        return new OptionSet(this);
    }

    private static @Nullable URI constructUri(
            @Nullable String scheme,
            @Nullable String host,
            @Nullable Integer portObj,
            @Nullable List<String> pathList,
            @Nullable List<String> queryList) {
        String path = null;
        String query = null;

        if (pathList != null && !pathList.isEmpty()) {
            StringBuilder builder = new StringBuilder();
            for (String unescapedPath : pathList) {
                builder.append("/");
                builder.append(Utils.uriEscapeString(unescapedPath));
            }
            path = builder.toString();
        }

        if (queryList != null && !queryList.isEmpty()) {
            StringBuilder builder = new StringBuilder();
            for (String unescapedQuery : queryList) {
                if (builder.length() != 0) {
                    builder.append("&");
                }
                builder.append(Utils.uriEscapeString(unescapedQuery));
            }
            query = builder.toString();
        }

        try {
            final int port = (portObj == null ? -1 : portObj);
            return new URI(scheme, null, host, port, path, query, null);
        } catch (URISyntaxException e) {
            e.printStackTrace();
            return null;
        }
    }

    public OptionSet addIfMatch(@Nullable Etag etag) {
        throwIfImmutable();
        if (etag == null) {
            throw new NullPointerException();
        }
        if (mIfMatchList == null) {
            mIfMatchList = new LinkedList<>();
        }
        mIfMatchList.add(etag);
        return this;
    }

    public List<Etag> getIfMatches() {
        return mIfMatchList;
    }

    public OptionSet setIfMatches(@Nullable List<Etag> x) {
        throwIfImmutable();
        if ((x != null) && x.isEmpty()) {
            x = null;
        }
        mIfMatchList = x;
        return this;
    }

    public OptionSet addEtag(@Nullable Etag etag) {
        throwIfImmutable();
        if (etag == null) {
            throw new NullPointerException();
        }
        if (mEtagList == null) {
            mEtagList = new LinkedList<>();
        }
        mEtagList.add(etag);
        return this;
    }

    public @Nullable List<Etag> getEtags() {
        return mEtagList;
    }

    public @Nullable Etag getEtag() {
        if (mEtagList != null && !mEtagList.isEmpty()) {
            return mEtagList.get(0);
        }
        return null;
    }

    public OptionSet setEtags(@Nullable List<Etag> x) {
        throwIfImmutable();
        if ((x != null) && x.isEmpty()) {
            x = null;
        }
        mEtagList = x;
        return this;
    }

    public @Nullable String getUriHost() {
        return mUriHost;
    }

    public OptionSet setUriHost(@Nullable String x) {
        throwIfImmutable();
        mUriHost = x;
        return this;
    }

    public boolean getIfNoneMatch() {
        return mIfNoneMatch;
    }

    public OptionSet setIfNoneMatch(boolean x) {
        throwIfImmutable();
        mIfNoneMatch = x;
        return this;
    }

    public Integer getUriPort() {
        return mUriPort;
    }

    public OptionSet setUriPort(@Nullable Integer x) {
        throwIfImmutable();
        if ((x != null) && (x < 0)) {
            throw new IllegalArgumentException("Invalid Uri-Port value " + x);
        }
        mUriPort = x;
        return this;
    }

    public OptionSet addLocationPath(@Nullable String x) {
        throwIfImmutable();
        if (x == null) {
            throw new NullPointerException();
        }
        if (mLocationPathList == null) {
            mLocationPathList = new LinkedList<>();
        }
        mLocationPathList.add(x);
        return this;
    }

    public List<String> getLocationPaths() {
        return mLocationPathList;
    }

    public OptionSet setLocationPaths(@Nullable List<String> x) {
        throwIfImmutable();
        if ((x != null) && x.isEmpty()) {
            x = null;
        }
        mLocationPathList = x;
        return this;
    }

    public OptionSet addUriPath(@Nullable String x) {
        throwIfImmutable();
        if (x == null) {
            throw new NullPointerException();
        }
        if (mUriPathList == null) {
            mUriPathList = new LinkedList<>();
        }
        mUriPathList.add(x);
        return this;
    }

    public @Nullable List<String> getUriPaths() {
        return mUriPathList;
    }

    public OptionSet setUriPaths(@Nullable List<String> x) {
        throwIfImmutable();
        if ((x != null) && x.isEmpty()) {
            x = null;
        }
        mUriPathList = x;
        return this;
    }

    public @Nullable Integer getContentFormat() {
        return mContentFormat;
    }

    public OptionSet setContentFormat(@Nullable Integer x) {
        throwIfImmutable();
        if ((x != null) && (x < 0)) {
            throw new IllegalArgumentException("Invalid Content-Format");
        }
        mContentFormat = x;
        return this;
    }

    public @Nullable Integer getMaxAge() {
        return mMaxAge;
    }

    public OptionSet setMaxAge(@Nullable Integer x) {
        throwIfImmutable();
        if ((x != null) && (x < 0)) {
            throw new IllegalArgumentException("Invalid Max-Age");
        }
        mMaxAge = x;
        return this;
    }

    public OptionSet addLocationQuery(@Nullable String x) {
        throwIfImmutable();
        if (x == null) {
            throw new NullPointerException();
        }
        if (mLocationQueryList == null) {
            mLocationQueryList = new LinkedList<>();
        }
        mLocationQueryList.add(x);
        return this;
    }

    public @Nullable List<String> getLocationQueries() {
        return mLocationQueryList;
    }

    public OptionSet setLocationQueries(@Nullable List<String> x) {
        throwIfImmutable();
        if ((x != null) && x.isEmpty()) {
            x = null;
        }
        mLocationQueryList = x;
        return this;
    }

    public OptionSet addUriQuery(@Nullable String x) {
        throwIfImmutable();
        if (x == null) {
            throw new NullPointerException();
        }
        if (mUriQueryList == null) {
            mUriQueryList = new LinkedList<>();
        }
        mUriQueryList.add(x);
        return this;
    }

    public @Nullable List<String> getUriQueries() {
        return mUriQueryList;
    }

    public Map<String, String> getUriQueriesAsMap() {
        LinkedHashMap<String, String> ret = new LinkedHashMap<>();

        if (hasUriQuery()) {
            for (String query : mUriQueryList) {
                if (query.contains("=")) {
                    String key = query.split("=")[0];
                    String value;
                    if (query.equals(key)) {
                        value = "";
                    } else {
                        value = query.substring(key.length() + 1);
                    }
                    ret.put(key, value);
                } else {
                    ret.put(query, "");
                }
            }
        }

        return ret;
    }

    public OptionSet setUriQueries(@Nullable List<String> x) {
        throwIfImmutable();
        if ((x != null) && x.isEmpty()) {
            x = null;
        }
        mUriQueryList = x;
        return this;
    }

    public @Nullable Integer getAccept() {
        return mAccept;
    }

    public OptionSet setAccept(@Nullable Integer x) {
        throwIfImmutable();
        if ((x != null) && (x < 0)) {
            throw new IllegalArgumentException("Invalid Accept");
        }
        mAccept = x;
        return this;
    }

    public OptionSet setProxyUriFromString(@Nullable String x) {
        throwIfImmutable();
        if (x == null) {
            setProxyUri(null);
        } else {
            setProxyUri(URI.create(x));
        }
        return this;
    }

    public @Nullable String getProxyScheme() {
        return mProxyScheme;
    }

    public OptionSet setProxyScheme(@Nullable String x) {
        throwIfImmutable();
        mProxyScheme = x;
        return this;
    }

    public @Nullable Integer getObserve() {
        return mObserve;
    }

    public OptionSet setObserve(@Nullable Integer x) {
        throwIfImmutable();
        if ((x != null) && (x < 0)) {
            throw new IllegalArgumentException("Invalid Observe value " + x);
        }
        mObserve = x;
        return this;
    }

    public @Nullable Integer getSize1() {
        return mSize1;
    }

    public OptionSet setSize1(@Nullable Integer x) {
        throwIfImmutable();
        if ((x != null) && (x < 0)) {
            throw new IllegalArgumentException("Invalid Size1 value " + x);
        }
        mSize1 = x;
        return this;
    }

    public @Nullable Integer getSize2() {
        return mSize2;
    }

    public OptionSet setSize2(@Nullable Integer x) {
        throwIfImmutable();
        if ((x != null) && (x < 0)) {
            throw new IllegalArgumentException("Invalid Size2 value " + x);
        }
        mSize2 = x;
        return this;
    }

    public @Nullable BlockOption getBlock1() {
        return mBlock1;
    }

    public OptionSet setBlock1(@Nullable BlockOption x) {
        throwIfImmutable();
        mBlock1 = x;
        return this;
    }

    public @Nullable BlockOption getBlock2() {
        return mBlock2;
    }

    public OptionSet setBlock2(@Nullable BlockOption x) {
        throwIfImmutable();
        mBlock2 = x;
        return this;
    }

    public OptionSet addOption(Option option) {
        throwIfImmutable();
        try {
            switch (option.getNumber()) {
                case Option.IF_MATCH:
                    return addIfMatch(Etag.createFromByteArray(option.byteArrayValue()));

                case Option.URI_HOST:
                    if (mUriHost != null) {
                        throw new BadOptionException(
                                "Duplicate " + Option.toString(option.getNumber()) + "Option");
                    }
                    return setUriHost(option.stringValue());

                case Option.ETAG:
                    return addEtag(Etag.createFromByteArray(option.byteArrayValue()));

                case Option.IF_NONE_MATCH:
                    return setIfNoneMatch(true);

                case Option.URI_PORT:
                    if (mUriPort != null) {
                        throw new BadOptionException(
                                "Duplicate " + Option.toString(option.getNumber()) + "Option");
                    }
                    return setUriPort(option.intValue());

                case Option.LOCATION_PATH:
                    return addLocationPath(option.stringValue());

                case Option.URI_PATH:
                    return addUriPath(option.stringValue());

                case Option.CONTENT_FORMAT:
                    if (mContentFormat != null) {
                        throw new BadOptionException(
                                "Duplicate " + Option.toString(option.getNumber()) + "Option");
                    }
                    return setContentFormat(option.intValue());

                case Option.MAX_AGE:
                    if (mMaxAge != null) {
                        throw new BadOptionException(
                                "Duplicate " + Option.toString(option.getNumber()) + "Option");
                    }
                    return setMaxAge(option.intValue());

                case Option.URI_QUERY:
                    return addUriQuery(option.stringValue());

                case Option.ACCEPT:
                    if (mAccept != null) {
                        throw new BadOptionException(
                                "Duplicate " + Option.toString(option.getNumber()) + "Option");
                    }
                    return setAccept(option.intValue());

                case Option.LOCATION_QUERY:
                    return addLocationQuery(option.stringValue());

                case Option.PROXY_URI:
                    if (mProxyUri != null) {
                        throw new BadOptionException(
                                "Duplicate " + Option.toString(option.getNumber()) + "Option");
                    }
                    return setProxyUriFromString(option.stringValue());

                case Option.PROXY_SCHEME:
                    if (mProxyScheme != null) {
                        throw new BadOptionException(
                                "Duplicate " + Option.toString(option.getNumber()) + "Option");
                    }
                    return setProxyScheme(option.stringValue());

                case Option.OBSERVE:
                    if (mObserve != null) {
                        throw new BadOptionException(
                                "Duplicate " + Option.toString(option.getNumber()) + "Option");
                    }
                    return setObserve(option.intValue());

                case Option.BLOCK2:
                    if (mBlock2 != null) {
                        throw new BadOptionException(
                                "Duplicate " + Option.toString(option.getNumber()) + "Option");
                    } else {
                        BlockOption bo = BlockOption.create(option.intValue());
                        if (!bo.isValid()) {
                            throw new BadOptionException(
                                    "Invalid value for " + Option.toString(option.getNumber()));
                        }
                        return setBlock2(bo);
                    }

                case Option.BLOCK1:
                    if (mBlock1 != null) {
                        throw new BadOptionException(
                                "Duplicate " + Option.toString(option.getNumber()) + "Option");
                    } else {
                        BlockOption bo = BlockOption.create(option.intValue());
                        if (!bo.isValid()) {
                            throw new BadOptionException(
                                    "Invalid value for " + Option.toString(option.getNumber()));
                        }
                        return setBlock1(bo);
                    }

                case Option.SIZE2:
                    if (mSize2 != null) {
                        throw new BadOptionException(
                                "Duplicate " + Option.toString(option.getNumber()) + "Option");
                    }
                    return setSize2(option.intValue());

                case Option.SIZE1:
                    if (mSize2 != null) {
                        throw new BadOptionException(
                                "Duplicate " + Option.toString(option.getNumber()) + "Option");
                    }
                    return setSize1(option.intValue());

                default:
                    break;
            }
        } catch (IllegalArgumentException x) {
            throw new BadOptionException(
                    "Invalid value for " + Option.toString(option.getNumber()), x);
        }

        if (mOtherOptions == null) {
            mOtherOptions = new LinkedList<>();
        }

        mOtherOptions.add(option);

        return this;
    }

    public OptionSet addOptions(List<Option> options) {
        for (Option option : options) {
            addOption(option);
        }
        return this;
    }

    public OptionSet addOptions(OptionSet options) {
        return addOptions(options.asSortedList());
    }

    public List<Option> asSortedList() {
        ArrayList<Option> options = new ArrayList<>();

        if (mIfMatchList != null) {
            for (Etag x : mIfMatchList) {
                options.add(new Option(Option.IF_MATCH, x.byteArrayValue()));
            }
        }

        if (mUriHost != null) {
            options.add(new Option(Option.URI_HOST, mUriHost));
        }

        if (mEtagList != null) {
            for (Etag x : mEtagList) {
                options.add(new Option(Option.ETAG, x.byteArrayValue()));
            }
        }

        if (mIfNoneMatch) {
            options.add(new Option(Option.IF_NONE_MATCH));
        }

        if (mUriPort != null) {
            options.add(new Option(Option.URI_PORT, mUriPort));
        }

        if (mLocationPathList != null) {
            for (String x : mLocationPathList) {
                options.add(new Option(Option.LOCATION_PATH, x));
            }
        }

        if (mUriPathList != null) {
            for (String x : mUriPathList) {
                options.add(new Option(Option.URI_PATH, x));
            }
        }

        if (mContentFormat != null) {
            options.add(new Option(Option.CONTENT_FORMAT, mContentFormat));
        }

        if (mMaxAge != null) {
            options.add(new Option(Option.MAX_AGE, mMaxAge));
        }

        if (mUriQueryList != null) {
            for (String x : mUriQueryList) {
                options.add(new Option(Option.URI_QUERY, x));
            }
        }

        if (mAccept != null) {
            options.add(new Option(Option.ACCEPT, mAccept));
        }

        if (mLocationQueryList != null) {
            for (String x : mLocationQueryList) {
                options.add(new Option(Option.LOCATION_QUERY, x));
            }
        }

        if (mProxyUri != null) {
            options.add(new Option(Option.PROXY_URI, mProxyUri.toString()));
        }

        if (mProxyScheme != null) {
            options.add(new Option(Option.PROXY_SCHEME, mProxyScheme));
        }

        if (mObserve != null) {
            options.add(new Option(Option.OBSERVE, mObserve));
        }

        if (mBlock2 != null) {
            options.add(new Option(Option.BLOCK2, mBlock2.toInteger()));
        }

        if (mBlock1 != null) {
            options.add(new Option(Option.BLOCK1, mBlock1.toInteger()));
        }

        if (mSize2 != null) {
            options.add(new Option(Option.SIZE2, mSize2));
        }

        if (mSize1 != null) {
            options.add(new Option(Option.SIZE1, mSize1));
        }

        if (mOtherOptions != null) {
            options.addAll(mOtherOptions);
        }

        options.sort(Option.Comparator.get());

        return options;
    }

    public boolean hasIfMatch() {
        return (mIfMatchList != null) && !mIfMatchList.isEmpty();
    }

    public boolean hasUriHost() {
        return mUriHost != null;
    }

    public boolean hasEtag() {
        return (mEtagList != null) && !mEtagList.isEmpty();
    }

    public boolean hasUriPort() {
        return mUriPort != null;
    }

    public boolean hasLocationPath() {
        return (mLocationPathList != null) && !mLocationPathList.isEmpty();
    }

    public boolean hasUriPath() {
        return (mUriPathList != null) && !mUriPathList.isEmpty();
    }

    public boolean hasContentFormat() {
        return mContentFormat != null;
    }

    public boolean hasMaxAge() {
        return mMaxAge != null;
    }

    public boolean hasUriQuery() {
        return (mUriQueryList != null) && !mUriQueryList.isEmpty();
    }

    public boolean hasAccept() {
        return mAccept != null;
    }

    public boolean hasLocationQuery() {
        return (mLocationQueryList != null) && !mLocationQueryList.isEmpty();
    }

    public boolean hasProxyUri() {
        return mProxyUri != null;
    }

    public boolean hasProxyScheme() {
        return mProxyScheme != null;
    }

    public boolean hasObserve() {
        return mObserve != null;
    }

    public boolean hasBlock2() {
        return mBlock2 != null;
    }

    public boolean hasBlock1() {
        return mBlock1 != null;
    }

    public boolean hasSize2() {
        return mSize2 != null;
    }

    public boolean hasSize1() {
        return mSize1 != null;
    }

    public boolean hasOption(int number) {
        switch (number) {
            case Option.IF_MATCH:
                return hasIfMatch();

            case Option.URI_HOST:
                return hasUriHost();

            case Option.ETAG:
                return hasEtag();

            case Option.IF_NONE_MATCH:
                return getIfNoneMatch();

            case Option.URI_PORT:
                return hasUriPort();

            case Option.LOCATION_PATH:
                return hasLocationPath();

            case Option.URI_PATH:
                return hasUriPath();

            case Option.CONTENT_FORMAT:
                return hasContentFormat();

            case Option.MAX_AGE:
                return hasMaxAge();

            case Option.URI_QUERY:
                return hasUriQuery();

            case Option.ACCEPT:
                return hasAccept();

            case Option.LOCATION_QUERY:
                return hasLocationQuery();

            case Option.PROXY_URI:
                return hasProxyUri();

            case Option.PROXY_SCHEME:
                return hasProxyScheme();

            case Option.OBSERVE:
                return hasObserve();

            case Option.BLOCK2:
                return hasBlock2();

            case Option.BLOCK1:
                return hasBlock1();

            case Option.SIZE2:
                return hasSize2();

            case Option.SIZE1:
                return hasSize1();

            default:
                return Collections.binarySearch(
                                asSortedList(), new Option(number), Option.Comparator.get())
                        >= 0;
        }
    }

    public OptionSet clear() {
        throwIfImmutable();

        mIfMatchList = null;
        mEtagList = null;
        mUriHost = null;
        mUriPort = null;
        mUriPathList = null;
        mUriQueryList = null;
        mLocationPathList = null;
        mLocationQueryList = null;
        mContentFormat = null;
        mAccept = null;
        mMaxAge = null;
        mObserve = null;
        mProxyUri = null;
        mProxyScheme = null;
        mBlock1 = null;
        mBlock2 = null;
        mSize1 = null;
        mSize2 = null;
        mOtherOptions = null;
        mIfNoneMatch = false;

        return this;
    }

    public int size() {
        return asSortedList().size();
    }

    public OptionSet removeOption(int number) {
        throwIfImmutable();
        switch (number) {
            case Option.IF_MATCH:
                return setIfMatches(null);

            case Option.URI_HOST:
                return setUriHost(null);

            case Option.ETAG:
                return setEtags(null);

            case Option.IF_NONE_MATCH:
                return setIfNoneMatch(false);

            case Option.URI_PORT:
                return setUriPort(null);

            case Option.LOCATION_PATH:
                return setLocationPaths(null);

            case Option.URI_PATH:
                return setUriPaths(null);

            case Option.CONTENT_FORMAT:
                return setContentFormat(null);

            case Option.MAX_AGE:
                return setMaxAge(null);

            case Option.URI_QUERY:
                return setUriQueries(null);

            case Option.ACCEPT:
                return setAccept(null);

            case Option.LOCATION_QUERY:
                return setLocationQueries(null);

            case Option.PROXY_URI:
                return setProxyUri(null);

            case Option.PROXY_SCHEME:
                return setProxyScheme(null);

            case Option.OBSERVE:
                return setObserve(null);

            case Option.BLOCK2:
                return setBlock2(null);

            case Option.BLOCK1:
                return setBlock1(null);

            case Option.SIZE2:
                return setSize2(null);

            case Option.SIZE1:
                return setSize1(null);

            default:
                for (Option option : new LinkedList<>(mOtherOptions)) {
                    if (number == option.getNumber()) {
                        mOtherOptions.remove(option);
                    }
                }
                break;
        }
        return this;
    }

    public final boolean hasUri() {
        return !hasProxyScheme() && (hasUriPath() || hasUriQuery() || hasUriHost() || hasUriPort());
    }

    public @Nullable URI getUri() {
        if (mProxyScheme != null) {
            return null;
        }

        String scheme = null;
        String host = mUriHost;

        if ((host != null) || (mUriPort != null)) {
            scheme = Coap.SCHEME_UDP;
            if (host == null) {
                host = "";
            }
        }

        return constructUri(scheme, host, mUriPort, mUriPathList, mUriQueryList);
    }

    public OptionSet setLocation(@Nullable String path) {
        setLocationPaths(null);
        setLocationQueries(null);

        if (path != null) {
            String rawPath = path;
            if (!rawPath.isEmpty()) {
                rawPath = rawPath.replaceFirst("^/", "");
                for (String escapedPath : rawPath.split("/", -1)) {
                    addLocationPath(Utils.uriUnescapeString(escapedPath));
                }
            }
        }

        return this;
    }

    public @Nullable URI getLocation() {
        return constructUri(null, null, null, mLocationPathList, mLocationQueryList);
    }

    public OptionSet setUri(@Nullable URI uri) {
        throwIfImmutable();
        if (uri == null) {
            setUriHost(null);
            setUriPort(null);
            setUriPaths(null);
            setUriQueries(null);
            setProxyScheme(null);
            return this;
        }

        if (uri.isOpaque()) {
            throw new IllegalArgumentException("URI cannot be opaque");
        }

        final URI prevUri = getUri();

        if (!uri.isAbsolute() && (prevUri != null && prevUri.isAbsolute())) {
            uri = prevUri.resolve(uri);
        }

        setUriHost(uri.getHost());
        setUriPort(uri.getPort() >= 0 ? uri.getPort() : null);

        setProxyUri(null);
        setProxyScheme(null);

        setUriPaths(null);
        String rawPath = uri.getRawPath();
        if ((rawPath != null) && !rawPath.isEmpty()) {
            rawPath = rawPath.replaceFirst("^/", "");
            for (String escapedPath : rawPath.split("/", -1)) {
                addUriPath(Utils.uriUnescapeString(escapedPath));
            }
        }

        setUriQueries(null);
        if (uri.getRawQuery() != null) {
            for (String escapedQuery : uri.getRawQuery().split("[&;]", -1)) {
                addUriQuery(Utils.uriUnescapeString(escapedQuery));
            }
        }
        return this;
    }

    public @Nullable URI getProxyUri() {
        if (mProxyUri != null) {
            return mProxyUri;
        }
        if (mProxyScheme != null) {
            return constructUri(mProxyScheme, mUriHost, mUriPort, mUriPathList, mUriQueryList);
        }
        return null;
    }

    public OptionSet setProxyUri(@Nullable URI x) {
        throwIfImmutable();
        if ((x != null) && !x.isAbsolute()) {
            throw new IllegalArgumentException("Invalid Proxy-Uri");
        }
        mProxyUri = x;
        return this;
    }

    @Override
    public String toString() {
        int lastOption = -1;
        boolean firstValue = true;
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        String separator = ";";
        for (Option option : asSortedList()) {
            if (option.getNumber() != lastOption) {
                if (lastOption != -1) {
                    sb.append(", ");
                }
                lastOption = option.getNumber();
                sb.append(Option.toString(lastOption));

                if (option.getNumber() == Option.URI_PATH
                        || option.getNumber() == Option.LOCATION_PATH) {
                    separator = "/";
                } else if (option.getNumber() == Option.URI_QUERY
                        || option.getNumber() == Option.URI_PATH) {
                    separator = "&";
                } else {
                    separator = ";";
                }

                sb.append(":");
                firstValue = true;
            }

            if (!firstValue) {
                sb.append(separator);
            }

            sb.append(option.valueToString());

            firstValue = false;
        }
        sb.append("]");
        return sb.toString();
    }
}
