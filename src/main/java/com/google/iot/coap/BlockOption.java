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

import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;
import org.checkerframework.checker.nullness.qual.Nullable;

@AutoValue
abstract class BlockOption {
    static final int SZX_RESERVED = 7;
    static final int NUM_MAX = ((1 << 20) - 1);
    static final int MORE_FLAG = (1 << 3);

    public static BlockOption create(int num, boolean m, int szx) {
        return new AutoValue_BlockOption(num, m, szx);
    }

    public static BlockOption create() {
        return new AutoValue_BlockOption(0, true, 6);
    }

    public static BlockOption create(int x) {
        Preconditions.checkArgument(x >= 0);
        return new AutoValue_BlockOption((x >> 4), ((x & MORE_FLAG) == MORE_FLAG), (x & 7));
    }

    public abstract int getBlockNumber();

    public abstract boolean getMoreFlag();

    public abstract int getSizeExponent();

    public @Nullable BlockOption getNextBlockOption() {
        if (isLastBlock()) {
            return null;
        }

        return create(getBlockNumber() + 1, true, getSizeExponent());
    }

    public @Nullable BlockOption getSmallerBlockOption() {
        if (getSizeExponent() == 0) {
            return null;
        }

        return create(getBlockNumber() * 2, true, getSizeExponent() - 1);
    }

    public BlockOption getMoreBlockOption() {
        if (!isLastBlock()) {
            return this;
        }
        return create(getBlockNumber(), true, getSizeExponent());
    }

    public BlockOption getLastBlockOption() {
        if (isLastBlock()) {
            return this;
        }
        return create(getBlockNumber(), false, getSizeExponent());
    }

    public boolean isValid() {
        if ((getSizeExponent() < 0) || (getSizeExponent() >= SZX_RESERVED)) {
            return false;
        }

        return (getBlockNumber() >= 0) && (getBlockNumber() < NUM_MAX);
    }

    public int getBlockSize() {
        if (getSizeExponent() >= SZX_RESERVED) {
            return 0;
        }
        return (1 << (getSizeExponent() + 4));
    }

    public int getBlockOffset() {
        return getBlockNumber() * getBlockSize();
    }

    public boolean isLastBlock() {
        return !getMoreFlag();
    }

    public int toInteger() {
        return (getBlockNumber() << 4) + (getMoreFlag() ? MORE_FLAG : 0) + getSizeExponent();
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();

        sb.append(getBlockNumber());
        sb.append("/");
        sb.append(getMoreFlag() ? "1" : "0");
        sb.append("/");
        sb.append(getBlockSize());

        if (!isValid()) {
            sb.append("(!)");
        }

        return sb.toString();
    }
}
