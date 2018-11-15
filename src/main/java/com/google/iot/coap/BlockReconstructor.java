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

import java.nio.BufferOverflowException;
import java.util.Arrays;

/**
 * Reconstructs data blocks in accordance with RFC7959.
 *
 * @see <a href="https://tools.ietf.org/html/rfc7959">RFC7959</a>
 */
class BlockReconstructor {
    private static final int MAX_SIZE = 65535 * 2;

    private byte[] mBuffer = new byte[1024];
    private int mBufferSize = 0;
    private BlockOption mNextBlockOption = BlockOption.create();
    private boolean mIsFinished = false;

    public BlockReconstructor() {}

    private void increaseBufferSize() {
        mBuffer = Arrays.copyOf(mBuffer, mBuffer.length * 2);
    }

    public boolean isFinished() {
        return mIsFinished;
    }

    public byte[] copyData() {
        return Arrays.copyOf(mBuffer, mBufferSize);
    }

    public long getDataSize() {
        return mBufferSize;
    }

    public BlockOption getNextWantedBlock() {
        return mNextBlockOption;
    }

    /**
     * @return true if the block has been fully reconstructed, false if more blocks are needed.
     * @throws BufferOverflowException if accumulated data has grown larger than {@link #MAX_SIZE}.
     */
    public boolean feedBlock(BlockOption blockOption, byte[] blockData) {
        if (mIsFinished || mNextBlockOption == null) {
            return true;
        }

        if (blockOption.getBlockOffset() != mNextBlockOption.getBlockOffset()) {
            // This isn't the block we were expecting.
            return false;
        }

        if (blockOption.getBlockSize() < blockData.length) {
            // Extra data?
            return false;
        }

        if (blockOption.getMoreFlag() && blockOption.getBlockSize() > blockData.length) {
            // Not enough data?
            return false;
        }

        if (mBufferSize + blockData.length > MAX_SIZE) {
            throw new BufferOverflowException();
        }

        while (mBuffer.length - mBufferSize < blockData.length) {
            increaseBufferSize();
        }

        for (byte b : blockData) {
            mBuffer[mBufferSize++] = b;
        }

        mNextBlockOption = blockOption.getNextBlockOption();

        mIsFinished = blockOption.isLastBlock();

        return mIsFinished;
    }
}
