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

import com.google.iot.cbor.CborObject;
import com.google.iot.cbor.CborParseException;
import java.net.SocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Abstract base class representing an immutable CoAP message. The mutable variant is {@link
 * MutableMessage}.
 *
 * @see MutableMessage
 */
@SuppressWarnings("unused")
public abstract class Message {

    /** Prohibit anyone outside of this package from subclassing it for now. */
    Message() {}

    /**
     * Placeholder Message ID (MID) value indicating that no MID has been set.
     *
     * @see #getMid()
     * @see MutableMessage#setMid(int)
     */
    public static final int MID_NONE = -1;

    /** Constant describing the largest valid CoAP message id (MID). */
    public static final int MID_MAX = (1 << 16) - 1;

    /**
     * Indicates if this is an inbound or outbound packet.
     *
     * @return true if this is an inbound packet, false if this is an outbound packet.
     * @see MutableMessage#setInbound(boolean)
     */
    public abstract boolean isInbound();

    /**
     * Gets the CoAP token for this message.
     *
     * @return the Token for this message
     * @see MutableMessage#setToken(Token)
     */
    public abstract Token getToken();

    /**
     * Gets the CoAP message id (MID) for this message.
     *
     * @return the MID for this message, or {@link #MID_NONE} if there is not the mid has not been
     *     set for this message.
     * @see MutableMessage#setMid(int)
     */
    public abstract int getMid();

    /**
     * Gets the set of {@link Option Options} for this message.
     *
     * @return the {@link OptionSet} for this message
     * @see MutableMessage#addOption(Option)
     * @see MutableMessage#addOption(int, int)
     * @see MutableMessage#addOption(int, byte[])
     * @see MutableMessage#addOption(int, String)
     * @see MutableMessage#addOptions(OptionSet)
     * @see MutableMessage#clearOptions()
     * @see MutableMessage#setOptionSet(OptionSet)
     */
    public abstract OptionSet getOptionSet();

    /**
     * Indicates if this message has any options or not.
     *
     * @return true if this message has options, false otherwise.
     * @see #getOptionSet()
     */
    public abstract boolean hasOptions();

    /**
     * Gets the CoAP message type for this message.
     *
     * @return the CoAP message type for this message
     * @see Type
     * @see MutableMessage#setType(int)
     */
    public abstract int getType();

    /**
     * Gets the CoAP message code for this message.
     *
     * @return the CoAP message code for this message
     * @see Code
     * @see MutableMessage#setCode(int)
     */
    public abstract int getCode();

    /**
     * Gets the payload for this message as a byte array.
     *
     * @return the payload for this message as a byte array
     * @see #getPayloadAsString()
     * @see MutableMessage#setPayload(byte[])
     */
    public abstract byte[] getPayload();

    /**
     * Gets the length of this message's payload in bytes.
     *
     * @return the length of this message's payload in bytes
     * @see #getPayload()
     */
    public abstract int getPayloadLength();

    /**
     * Gets the remote socket address of this message.
     *
     * @return the remote socket address of this message, or null if there is no remote socket
     *     address associated with this message.
     * @see #getLocalSocketAddress()
     * @see MutableMessage#setRemoteSocketAddress(SocketAddress)
     */
    @Nullable
    public abstract SocketAddress getRemoteSocketAddress();

    /**
     * Gets the local socket address of this message.
     *
     * @return the local socket address of this message, or null if there is no local socket address
     *     associated with this message.
     * @see #getRemoteSocketAddress()
     * @see MutableMessage#setLocalSocketAddress(SocketAddress)
     */
    @Nullable
    public abstract SocketAddress getLocalSocketAddress();

    /**
     * Indicates if this message has an MID set or not.
     *
     * <p>This is equivalent to the expression {@code getMid() != MID_NONE}.
     *
     * @return true if this message has a MID set, false otherwise.
     */
    public final boolean hasMid() {
        return getMid() != MID_NONE;
    }

    /**
     * Indicates if this message has an empty token.
     *
     * <p>This is equivalent to the expression {@code getToken().isEmpty()}.
     *
     * @return true if this message has an empty Token, false otherwise.
     */
    public final boolean hasEmptyToken() {
        return getToken().isEmpty();
    }

    /**
     * Indicates if this message has a non-empty payload.
     *
     * <p>This is equivalent to the expression {@code getPayloadLength() > 0}.
     *
     * @return true if this message has a non-empty payload, false otherwise.
     */
    public final boolean hasPayload() {
        return getPayloadLength() > 0;
    }

    /**
     * Makes an immutable deep copy of this message.
     *
     * @return the created immutable deep copy of this message.
     * @see #mutableCopy()
     */
    public Message copy() {
        return new MessageImpl(this).makeImmutable();
    }

    /**
     * Makes a mutable deep copy of this message.
     *
     * @return the created mutable deep copy of this message.
     * @see #copy()
     */
    public MutableMessage mutableCopy() {
        return new MessageImpl(this);
    }

    /**
     * Indicates if this message is a CoAP request.
     *
     * @return true if this message is a CoAP request, false otherwise
     */
    public final boolean isRequest() {
        return ((getType() & ~1) == 0) && Code.isRequest(getCode());
    }

    /**
     * Indicates if this is an "empty" message (according to RFC7252).
     *
     * <p>A message is a valid "empty" message only if its code is {@link Code#EMPTY}, it has no
     * payload, has no options, and has a type of {@link Type#ACK} or {@link Type#RST}.
     *
     * @return true if this is an "empty" message, false otherwise
     */
    public final boolean isEmpty() {
        return isEmptyAck() || isReset() || isPing();
    }

    /**
     * Indicates if this is a valid "empty" acknowledgement message.
     *
     * @return true if this is a valid "empty" acknowledgement message, false otherwise
     */
    public final boolean isEmptyAck() {
        return (getType() == Type.ACK)
                && (getCode() == Code.EMPTY)
                && !hasOptions()
                && !hasPayload();
    }

    /**
     * Indicates if this is a valid reset message.
     *
     * @return true if this is a valid reset message, false otherwise
     */
    public final boolean isReset() {
        return (getType() == Type.RST)
                && (getCode() == Code.EMPTY)
                && !hasOptions()
                && !hasPayload();
    }

    /**
     * Indicates if this is a valid ping message.
     *
     * @return true if this is a valid ping message, false otherwise
     */
    public final boolean isPing() {
        return (getType() == Type.CON)
                && (getCode() == Code.EMPTY)
                && !hasOptions()
                && !hasPayload();
    }

    /**
     * Throws a descriptive {@link CoapException} if this message is invalid.
     *
     * @throws CoapException if this message is invalid
     */
    public final void throwIfInvalid() throws CoapException {
        if (!Code.isValid(getCode())) {
            throw new CoapException("Message code " + getCode() + " is invalid");
        }

        if (!Type.isValid(getType())) {
            throw new CoapException("Message type " + getCode() + " is invalid");
        }

        if (getMid() > MID_MAX || getMid() < MID_NONE) {
            throw new CoapException("Message id " + getMid() + " is invalid");
        }

        if ((getCode() == Code.EMPTY)) {
            if (hasOptions() || hasPayload()) {
                throw new CoapException(
                        "Message code is empty, but message has options and/or payload");
            }

            if (!isReset() && !isEmptyAck() && !isPing()) {
                throw new CoapException(
                        "Message code is empty, but message isn't a valid reset, ack, or ping");
            }
        }
    }

    /**
     * Indicates if this message is considered valid or not.
     *
     * <p>Note that the presence or absence of a MID is not used in this determination.
     *
     * @return true if this message is considered valid, false otherwise
     */
    public final boolean isValid() {
        if (!Code.isValid(getCode()) || !Type.isValid(getType())) {
            return false;
        }

        if ((getCode() == Code.EMPTY) && !(isPing() || isEmptyAck() || isReset())) {
            return false;
        }

        return getMid() <= MID_MAX && getMid() >= MID_NONE;
    }

    /**
     * Indicates if this message is a multicast message or not.
     *
     * @return true if this message is a multicast message, false otherwise
     */
    public final boolean isMulticast() {
        final SocketAddress address;

        if (isInbound()) {
            address = getLocalSocketAddress();
        } else {
            address = getRemoteSocketAddress();
        }

        if (address == null) {
            return false;
        }

        return Utils.isSocketAddressMulticast(address);
    }

    /**
     * Creates a new {@link MutableMessage} that is a response to this message with the given code.
     *
     * <p>The given code may be {@link Code#EMPTY} to create an empty acknowledgement, but if that
     * is your goal you should use {@link #createAckResponse()} instead.
     *
     * @param code the message {@link Code code} for the response message.
     * @return a new {@link MutableMessage} that is a response to this message
     * @see Code
     * @see #createRstResponse()
     * @see #createAckResponse()
     */
    public final MutableMessage createResponse(int code) {
        if (!isRequest()) {
            throw new IllegalArgumentException("Response can only be made to request");
        }
        MutableMessage ret = MutableMessage.create();

        // Responses are piggy-backed by default.
        ret.setType(Type.ACK);
        ret.setMid(getMid());

        if (code != Code.EMPTY) {
            ret.setCode(code);
            ret.setToken(getToken());
        }

        ret.setRemoteSocketAddress(getRemoteSocketAddress());

        final SocketAddress localSockAddr = getLocalSocketAddress();

        if (localSockAddr != null && !Utils.isSocketAddressMulticast(localSockAddr)) {
            // We only set the local socket address on the reply when
            // it is not multicast.
            ret.setLocalSocketAddress(localSockAddr);
        }

        return ret;
    }

    /**
     * Creates a new {@link Message} that is a reset response to this message.
     *
     * @return the new reset {@link Message} instance
     * @see Type#RST
     * @see #createResponse(int)
     * @see #createAckResponse()
     */
    public final Message createRstResponse() {
        MessageImpl ret = new MessageImpl();

        ret.setType(Type.RST);
        ret.setMid(getMid());
        ret.setCode(Code.EMPTY);

        ret.setRemoteSocketAddress(getRemoteSocketAddress());

        final SocketAddress localSockAddr = getLocalSocketAddress();

        if (localSockAddr != null && !Utils.isSocketAddressMulticast(localSockAddr)) {
            // We only set the local socket address on the reply when
            // it is not multicast.
            ret.setLocalSocketAddress(localSockAddr);
        }

        return ret.makeImmutable();
    }

    /**
     * Creates a new {@link Message} that is an empty acknowledgement response to this message.
     *
     * @return the new empty acknowledgement {@link Message} instance
     * @see Type#ACK
     * @see #createResponse(int)
     * @see #createRstResponse()
     */
    public final Message createAckResponse() {
        MessageImpl ret = new MessageImpl();

        ret.setType(Type.ACK);
        ret.setMid(getMid());
        ret.setCode(Code.EMPTY);

        ret.setRemoteSocketAddress(getRemoteSocketAddress());

        final SocketAddress localSockAddr = getLocalSocketAddress();

        if (localSockAddr != null && !Utils.isSocketAddressMulticast(localSockAddr)) {
            // We only set the local socket address on the reply when
            // it is not multicast.
            ret.setLocalSocketAddress(localSockAddr);
        }

        return ret.makeImmutable();
    }

    /**
     * Indicates if the payload consists entirely of ASCII characters.
     *
     * @return true if the entire payload contains only ascii characters or if the payload is empty,
     *     false otherwise
     */
    public final boolean isPayloadAscii() {
        for (byte b : getPayload()) {
            if (b <= 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Gets this message's UTF-8 payload as a {@link String}.
     *
     * @return this message's UTF-8 payload as a {@link String}, returning the empty string if the
     *     payload is empty.
     */
    public final String getPayloadAsString() {
        if (getPayloadLength() == 0) {
            return "";
        }
        return new String(getPayload(), StandardCharsets.UTF_8);
    }

    /**
     * Constructs a short diagnostic string describing this message.
     *
     * @return a short diagnostic string describing this message
     */
    public final String toShortString() {
        StringBuilder sb = new StringBuilder();
        sb.append("<");
        sb.append(Code.toString(getCode()));
        sb.append(" ");

        if (isInbound()) {
            if (isMulticast()) {
                sb.append("IN-MCAST");
            } else {
                sb.append("IN");
            }
        } else {
            if (isMulticast()) {
                sb.append("OUT-MCAST");
            } else {
                sb.append("OUT");
            }
        }

        sb.append(" ");
        sb.append(Type.toString(getType()));

        if (getMid() != MID_NONE) {
            sb.append(" MID:");
            sb.append(getMid());
        }

        if (!isEmpty()) {
            if (!hasEmptyToken()) {
                sb.append(" TOK:");
                sb.append(getToken());
            }
        }

        if (hasOptions()) {
            URI uri = getOptionSet().getUri();
            if (uri != null && uri.getPath() != null) {
                //noinspection ConstantConditions
                sb.append(" ").append(uri.getPath());
            }
        }

        sb.append(">");
        return sb.toString();
    }

    /**
     * Constructs a comprehensive diagnostic string describing all details of this message.
     *
     * @return a long diagnostic string describing this message
     */
    @Override
    public final String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("<");
        sb.append(Code.toString(getCode()));
        sb.append(" ");
        if (isInbound()) {
            if (isMulticast()) {
                sb.append("IN-MCAST");
            } else {
                sb.append("IN");
            }
        } else {
            if (isMulticast()) {
                sb.append("OUT-MCAST");
            } else {
                sb.append("OUT");
            }
        }

        sb.append(" ");
        sb.append(Type.toString(getType()));

        if (getMid() != MID_NONE) {
            sb.append(" MID:");
            sb.append(getMid());
        }

        if (!hasEmptyToken()) {
            sb.append(" TOK:");
            sb.append(getToken());
        }

        if (!isEmpty()) {
            sb.append(" ");
            sb.append(Code.toNumericString(getCode()));
        }

        if (getRemoteSocketAddress() != null) {
            sb.append(String.format(" RADDR:\"%s\"", getRemoteSocketAddress()));
        }

        if (hasOptions()) {
            sb.append(" OPTS:");
            sb.append(getOptionSet());
        }

        if (hasPayload()) {
            //noinspection ConstantConditions
            if (getOptionSet().hasContentFormat()
                    && (ContentFormat.APPLICATION_CBOR == getOptionSet().getContentFormat())) {
                sb.append(" CBOR:");
                // CBOR will not parse correctly if this is
                // a block transfer message.

                if (!getOptionSet().hasBlock2()) {
                    try {
                        CborObject obj = CborObject.createFromCborByteArray(getPayload());
                        sb.append(obj.toString());

                    } catch (CborParseException x) {
                        sb.append(" ");
                        sb.append(x.toString());
                    }
                } else {
                    sb.append("partial-data");
                }

            } else if (isPayloadAscii()) {
                sb.append(" TEXT:\"");
                int count = 0;
                for (byte b : getPayload()) {
                    if (count++ > 48) {
                        sb.append("...");
                        break;
                    }
                    if (b > 31) {
                        switch (b) {
                            case '\"':
                                sb.append("\"");
                                break;
                            default:
                                sb.append((char) b);
                                break;
                        }
                    }
                }
                sb.append("\"");
            } else {
                sb.append(" BINARY-PAYLOAD-");
                sb.append(getPayloadLength());
                sb.append("-BYTES");
            }
        }

        sb.append(">");
        return sb.toString();
    }
}
