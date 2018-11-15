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
import java.net.SocketAddress;
import java.util.Arrays;
import java.util.Objects;
import org.checkerframework.checker.nullness.qual.Nullable;

final class MessageImpl extends MutableMessage {

    private int mType = Type.CON;
    private int mCode = Code.EMPTY;
    private Token mToken = Token.emptyToken();
    private OptionSet mOptionSet = null;
    private int mMid = MID_NONE;
    private volatile byte[] mPayload = null;
    private SocketAddress mRemoteSocketAddress;
    private SocketAddress mLocalSocketAddress;
    private boolean mIsInbound = false;

    private boolean mIsImmutable = false;

    MessageImpl() {}

    MessageImpl(Message msg) {
        mType = msg.getType();
        mCode = msg.getCode();
        mToken = msg.getToken();
        mIsInbound = msg.isInbound();
        if (msg.hasOptions()) {
            mOptionSet = msg.getOptionSet().copy();
        }
        mMid = msg.getMid();
        if (msg.hasPayload()) {
            byte[] payload = msg.getPayload();
            mPayload = Arrays.copyOf(payload, payload.length);
        }
        mRemoteSocketAddress = msg.getRemoteSocketAddress();
        mLocalSocketAddress = msg.getLocalSocketAddress();
    }

    Message makeImmutable() {
        mIsImmutable = true;
        if (mOptionSet != null) {
            mOptionSet = mOptionSet.makeImmutable();
        }
        return this;
    }

    private void throwIfImmutable() {
        if (mIsImmutable) {
            throw new IllegalStateException("Attempting to mutate an immutable message");
        }
    }

    @Override
    public Message copy() {
        if (mIsImmutable) {
            return this;
        }
        return super.copy();
    }

    @Override
    public boolean isInbound() {
        return mIsInbound;
    }

    @Override
    @CanIgnoreReturnValue
    public MutableMessage setInbound(boolean x) {
        throwIfImmutable();
        mIsInbound = x;
        return this;
    }

    @Override
    public Token getToken() {
        return mToken;
    }

    @Override
    public MutableMessage setToken(Token token) {
        throwIfImmutable();
        mToken = token;
        return this;
    }

    @Override
    public int getMid() {
        return mMid;
    }

    @Override
    @CanIgnoreReturnValue
    public MutableMessage setMid(int mid) {
        throwIfImmutable();
        mMid = mid;
        return this;
    }

    @Override
    public OptionSet getOptionSet() {
        if (mOptionSet == null) {
            mOptionSet = new OptionSet();
        }
        return mOptionSet;
    }

    @Override
    @CanIgnoreReturnValue
    public MutableMessage setOptionSet(OptionSet options) {
        throwIfImmutable();
        mOptionSet = Objects.requireNonNull(options);
        return this;
    }

    @Override
    public boolean hasOptions() {
        return (mOptionSet != null) && (mOptionSet.size() > 0);
    }

    @Override
    @CanIgnoreReturnValue
    public MutableMessage addOption(int number, int value) {
        throwIfImmutable();
        getOptionSet().addOption(new Option(number, value));
        return this;
    }

    @Override
    @CanIgnoreReturnValue
    public MutableMessage addOption(int number, String value) {
        throwIfImmutable();
        getOptionSet().addOption(new Option(number, value));
        return this;
    }

    @Override
    @CanIgnoreReturnValue
    public MutableMessage addOption(int number, byte[] value) {
        throwIfImmutable();
        getOptionSet().addOption(new Option(number, value));
        return this;
    }

    @Override
    @CanIgnoreReturnValue
    public MutableMessage addOption(Option option) {
        throwIfImmutable();
        getOptionSet().addOption(option);
        return this;
    }

    @Override
    @CanIgnoreReturnValue
    public MutableMessage addOptions(OptionSet options) {
        throwIfImmutable();
        getOptionSet().addOptions(options);
        return this;
    }

    @Override
    @CanIgnoreReturnValue
    public MutableMessage clearOptions() {
        throwIfImmutable();
        if (hasOptions()) {
            getOptionSet().clear();
        }
        return this;
    }

    @Override
    public int getType() {
        return mType;
    }

    @Override
    @CanIgnoreReturnValue
    public MutableMessage setType(int type) {
        throwIfImmutable();
        if (!Type.isValid(type)) {
            throw new IllegalArgumentException("Invalid message type " + type);
        }
        mType = type;
        return this;
    }

    @Override
    public int getCode() {
        return mCode;
    }

    @Override
    @CanIgnoreReturnValue
    public MutableMessage setCode(int code) {
        throwIfImmutable();
        if (!Code.isValid(code)) {
            throw new IllegalArgumentException("Invalid message code " + code);
        }
        mCode = code;
        return this;
    }

    @Override
    public byte[] getPayload() {
        return mPayload;
    }

    @Override
    @CanIgnoreReturnValue
    public MutableMessage setPayload(byte[] payload) {
        throwIfImmutable();
        mPayload = payload;
        return this;
    }

    @Override
    public int getPayloadLength() {
        if (mPayload == null) {
            return 0;
        }
        return mPayload.length;
    }

    @Override
    public SocketAddress getRemoteSocketAddress() {
        return mRemoteSocketAddress;
    }

    @Override
    @CanIgnoreReturnValue
    public MutableMessage setRemoteSocketAddress(@Nullable SocketAddress socketAddress) {
        throwIfImmutable();
        mRemoteSocketAddress = socketAddress;
        return this;
    }

    @Override
    public SocketAddress getLocalSocketAddress() {
        return mLocalSocketAddress;
    }

    @Override
    @CanIgnoreReturnValue
    public MutableMessage setLocalSocketAddress(@Nullable SocketAddress socketAddress) {
        throwIfImmutable();
        mLocalSocketAddress = socketAddress;
        return this;
    }
}
