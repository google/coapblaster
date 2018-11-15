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

import java.net.SocketAddress;
import java.util.Objects;

/**
 * Class for associating messages to transactions using the token field. TODO: This is a candidate
 * for deletion.
 */
final class KeyToken {
    private Token mToken;
    private final SocketAddress mSocketAddress;
    private int mHash;
    private final boolean mIsMulticast;

    KeyToken(Token token, SocketAddress socketAddress) {
        mSocketAddress = socketAddress;
        mIsMulticast = Utils.isSocketAddressMulticast(socketAddress);
        setToken(token);
    }

    KeyToken(Message msg) {
        this(msg.getToken(), msg.getRemoteSocketAddress());
    }

    public void setToken(Token token) {
        mToken = token;
        mHash = Objects.hashCode(token) * 1337;
    }

    public Token getToken() {
        return mToken;
    }

    @Override
    public int hashCode() {
        return mHash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (obj == null) {
            return false;
        }

        if (obj.getClass() != getClass()) {
            return false;
        }

        KeyToken rhs = (KeyToken) obj;

        if (!Objects.equals(mToken, rhs.mToken)) {
            return false;
        }

        if (mIsMulticast || rhs.mIsMulticast) {
            return true;
        }

        return Objects.equals(mSocketAddress, rhs.mSocketAddress);
    }

    @Override
    public String toString() {
        return "{KeyToken " + mToken + " " + mSocketAddress + "}";
    }
}
