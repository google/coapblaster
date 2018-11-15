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
import java.net.URI;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Builder class for constructing outbound requests. Each instance of this class is good for only
 * one request.
 *
 * @see Client#newRequestBuilder()
 */
public final class RequestBuilder {
    private static final boolean DEBUG = false;
    private static final Logger LOGGER = Logger.getLogger(RequestBuilder.class.getCanonicalName());

    private final Client mClient;
    private final MutableMessage mMessage =
            MutableMessage.create().setType(Type.CON).setCode(Code.METHOD_GET);
    private URI mUri;
    private URI mProxyUri = null;
    private boolean mOmitUriHostPortOptions = false;

    RequestBuilder(Client client) {
        mClient = Objects.requireNonNull(client);
        mUri = client.getUri();
        mMessage.setRemoteSocketAddress(mClient.getRemoteSocketAddress());
        mMessage.setLocalSocketAddress(mClient.getLocalSocketAddress());
        updateUriOptions();
        if (DEBUG) LOGGER.info("New Request Builder for " + mUri);
    }

    private void updateUriOptions() {
        mMessage.getOptionSet().setUri(null);
        mProxyUri = mClient.getProxySelector().onGetProxyForUri(mUri);
        if (mProxyUri != null) {
            mMessage.getOptionSet().setUri(mProxyUri);
            mMessage.getOptionSet().setProxyUri(mUri);
        } else {
            mMessage.getOptionSet().setUri(mUri);
        }
    }

    @CanIgnoreReturnValue
    public RequestBuilder setConfirmable(boolean confirmable) {
        if (confirmable) {
            mMessage.setType(Type.CON);
        } else {
            mMessage.setType(Type.NON);
        }
        return this;
    }

    /**
     * Omits {@link Option#URI_PATH} and {@link Option#URI_PORT} from the request. This will reduce
     * the size of the request but will thwart virtual host implementations.
     *
     * @param value true if {@link Option#URI_PATH} and {@link Option#URI_PORT} should be omitted,
     *     false otherwise.
     * @return a convenience reference to this builder
     */
    @CanIgnoreReturnValue
    public RequestBuilder setOmitUriHostPortOptions(boolean value) {
        mOmitUriHostPortOptions = value;
        return this;
    }

    @CanIgnoreReturnValue
    public RequestBuilder setCode(int code) {
        mMessage.setCode(code);
        return this;
    }

    @CanIgnoreReturnValue
    public RequestBuilder setPayload(byte[] payload) {
        mMessage.setPayload(payload);
        return this;
    }

    @SuppressWarnings("unused")
    @CanIgnoreReturnValue
    public RequestBuilder setPayload(String payload) {
        mMessage.setPayload(payload);
        return this;
    }

    @CanIgnoreReturnValue
    public RequestBuilder setToken(Token token) {
        mMessage.setToken(token);
        return this;
    }

    @SuppressWarnings("unused")
    @CanIgnoreReturnValue
    public RequestBuilder addOption(Option option) {
        mMessage.addOption(option);
        return this;
    }

    @CanIgnoreReturnValue
    public RequestBuilder addOption(int number) {
        mMessage.addOption(new Option(number));
        return this;
    }

    @CanIgnoreReturnValue
    public RequestBuilder addOption(int number, int value) {
        mMessage.addOption(new Option(number, value));
        return this;
    }

    @CanIgnoreReturnValue
    public RequestBuilder addOption(int number, String value) {
        mMessage.addOption(new Option(number, value));
        return this;
    }

    @SuppressWarnings("unused")
    @CanIgnoreReturnValue
    public RequestBuilder addOption(int number, byte[] value) {
        mMessage.addOption(new Option(number, value));
        return this;
    }

    @CanIgnoreReturnValue
    public RequestBuilder addOptions(OptionSet options) {
        mMessage.addOptions(options);
        return this;
    }

    @CanIgnoreReturnValue
    public RequestBuilder clearOptions() {
        mMessage.clearOptions();
        return this;
    }

    @CanIgnoreReturnValue
    public RequestBuilder changePath(String path) {
        mUri = mUri.resolve(path);
        updateUriOptions();
        return this;
    }

    public Transaction prepare() {
        URI destUri = mUri;

        if (mProxyUri != null) {
            destUri = mProxyUri;
        }

        Transaction ret =
                new TransactionImpl(
                        mClient.getLocalEndpointManager(),
                        mClient.getLocalEndpoint(),
                        mMessage,
                        destUri,
                        mOmitUriHostPortOptions);

        mClient.registerTransaction(ret);

        return ret;
    }

    public Transaction send() {
        Transaction ret = prepare();
        ret.restart();
        return ret;
    }
}
