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
import com.google.errorprone.annotations.CheckReturnValue;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Abstract base class representing a mutable CoAP message. The immutable variant is the base class:
 * {@link Message}.
 *
 * <p>Note that inheriting this class does not make the underlying message mutable: it would be an
 * error to check to see if a message is mutable by doing the check {@code (msg instanceof
 * MutableMessage)} because some message implementations start out mutable but become immutable
 * later on.
 */
@SuppressWarnings({"UnusedReturnValue", "unused"})
@CanIgnoreReturnValue
public abstract class MutableMessage extends Message {
    /** Prohibit anyone outside of this package from subclassing it for now. */
    MutableMessage() {}

    /**
     * Creates a new {@link MutableMessage} instance.
     *
     * @return the newly created {@link MutableMessage} instance.
     */
    @CheckReturnValue
    public static MutableMessage create() {
        return new MessageImpl();
    }

    /**
     * Marks this message as being inbound.
     *
     * @param inbound true if this message is inbound, false if this message is outbound.
     * @return this {@link MutableMessage} instance.
     * @see #isInbound()
     */
    public abstract MutableMessage setInbound(boolean inbound);

    /**
     * Sets the token.
     *
     * @return this {@link MutableMessage} instance.
     * @see #getToken()
     */
    public abstract MutableMessage setToken(Token token);

    /**
     * Sets the CoAP message id.
     *
     * @return this {@link MutableMessage} instance.
     * @see #getMid()
     */
    public abstract MutableMessage setMid(int mid);

    /**
     * Sets the CoAP message type.
     *
     * @see Type
     * @return this {@link MutableMessage} instance.
     * @see #getType()
     */
    public abstract MutableMessage setType(int type);

    /**
     * Sets the CoAP message code.
     *
     * @see Code
     * @return this {@link MutableMessage} instance.
     * @see #getCode()
     */
    public abstract MutableMessage setCode(int code);

    /**
     * Sets the remote socket address.
     *
     * @return this {@link MutableMessage} instance.
     * @see #getRemoteSocketAddress()
     */
    public abstract MutableMessage setRemoteSocketAddress(@Nullable SocketAddress socketAddress);

    /**
     * Sets the local socket address.
     *
     * @return this {@link MutableMessage} instance.
     * @see #getLocalSocketAddress()
     */
    public abstract MutableMessage setLocalSocketAddress(@Nullable SocketAddress socketAddress);

    /**
     * Adds an option with an integer value.
     *
     * @return this {@link MutableMessage} instance.
     * @see #getOptionSet()
     */
    public abstract MutableMessage addOption(int number, int value);

    /**
     * Adds an option with a string value.
     *
     * @return this {@link MutableMessage} instance.
     * @see #getOptionSet()
     */
    public abstract MutableMessage addOption(int number, String value);

    /**
     * Adds an option with a byte array value.
     *
     * @return this {@link MutableMessage} instance.
     * @see #getOptionSet()
     */
    public abstract MutableMessage addOption(int number, byte[] value);

    /**
     * Adds an {@link Option}.
     *
     * @return this {@link MutableMessage} instance.
     * @see #getOptionSet()
     */
    public abstract MutableMessage addOption(Option option);

    /**
     * Replaces the current set of options with those specified in {@code options}.
     *
     * @return this {@link MutableMessage} instance.
     * @see #getOptionSet()
     */
    public abstract MutableMessage setOptionSet(OptionSet options);

    /**
     * Adds the current set of options to those specified in {@code options}.
     *
     * @return this {@link MutableMessage} instance.
     * @see #getOptionSet()
     */
    public abstract MutableMessage addOptions(OptionSet options);

    /**
     * Removes all options from this message.
     *
     * @return this {@link MutableMessage} instance.
     * @see #getOptionSet()
     */
    public abstract MutableMessage clearOptions();

    /**
     * Sets the payload to the given byte array value. Any previous payload is discarded.
     *
     * @return this {@link MutableMessage} instance.
     * @see #getPayload()
     */
    public abstract MutableMessage setPayload(byte[] payload);

    /**
     * Sets the payload to the given {@link String} in UTF-8. Any previous payload is discarded.
     *
     * @return this {@link MutableMessage} instance.
     * @see #getPayloadAsString()
     */
    public MutableMessage setPayload(String payload) {
        return setPayload(payload.getBytes(StandardCharsets.UTF_8));
    }
}
