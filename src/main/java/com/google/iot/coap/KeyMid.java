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

import org.checkerframework.checker.nullness.qual.Nullable;

import java.net.SocketAddress;
import java.util.Objects;

/** Class for associating messages to transactions using the message id field. */
final class KeyMid {
    private int mMid;
    private final @Nullable SocketAddress mSocketAddress;
    private int mHash;
    private final boolean mIsMulticast;

    public KeyMid(int mid, @Nullable SocketAddress socketAddress) {
        mSocketAddress = socketAddress;
        mIsMulticast = Utils.isSocketAddressMulticast(socketAddress);
        setMid(mid);
    }

    public void setMid(int mid) {
        mMid = mid;
        mHash = Integer.hashCode(mid);
        // Note that the socket address explicitly isn't included in the hash
        // to ensure that multicast comparisons work properly.
    }

    KeyMid(Message msg) {
        this(msg.getMid(), msg.getRemoteSocketAddress());
    }

    public boolean isMulticast() {
        return mIsMulticast;
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

        KeyMid rhs = (KeyMid) obj;

        if (mMid != rhs.mMid) {
            return false;
        }

        if (mIsMulticast || rhs.mIsMulticast) {
            return true;
        }

        return Objects.equals(mSocketAddress, rhs.mSocketAddress);
    }

    @Override
    public String toString() {
        return "{KeyMid " + mMid + " " + mSocketAddress + "}";
    }
}
