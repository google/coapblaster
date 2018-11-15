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

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

@SuppressWarnings("ConstantConditions")
class BlockOptionTest {

    @Test
    void getBlockNumber() {
        assertEquals(BlockOption.create(5).getBlockNumber(), 0);
        assertEquals(BlockOption.create(16).getBlockNumber(), 1);
        assertEquals(BlockOption.create(32).getBlockNumber(), 2);
    }

    @Test
    void getSizeExponent() {
        assertEquals(BlockOption.create(5).getSizeExponent(), 5);
        assertEquals(BlockOption.create(16).getSizeExponent(), 0);
        assertEquals(BlockOption.create(32).getSizeExponent(), 0);
        assertEquals(BlockOption.create(7).getSizeExponent(), 7);
    }

    @Test
    void getNextBlockOption() {
        BlockOption bo = BlockOption.create(0, true, 6);

        assertEquals(bo.getBlockNumber(), 0);

        bo = bo.getNextBlockOption();
        assertEquals(bo.getBlockNumber(), 1);

        bo = bo.getNextBlockOption();
        assertEquals(bo.getBlockNumber(), 2);

        bo = bo.getNextBlockOption();
        assertEquals(bo.getBlockNumber(), 3);
    }

    @Test
    void getSmallerBlockOption() {
        BlockOption bo = BlockOption.create(0, true, 6);

        bo = bo.getNextBlockOption();
        bo = bo.getNextBlockOption();
        assertEquals(bo.getBlockNumber(), 2);

        bo = bo.getSmallerBlockOption();
        assertEquals(bo.getBlockNumber(), 4);
        assertEquals(bo.getSizeExponent(), 5);

        bo = bo.getNextBlockOption();
        assertEquals(bo.getBlockNumber(), 5);

        bo = bo.getSmallerBlockOption();
        assertEquals(bo.getBlockNumber(), 10);
        assertEquals(bo.getSizeExponent(), 4);
    }

    @Test
    void getLastBlockOption() {
        BlockOption bo = BlockOption.create(0, true, 6);

        assertFalse(bo.isLastBlock());
        assertEquals(0, bo.getBlockNumber());
        assertEquals(6, bo.getSizeExponent());

        bo = bo.getLastBlockOption();

        assertTrue(bo.isLastBlock());
        assertEquals(0, bo.getBlockNumber());
        assertEquals(6, bo.getSizeExponent());
    }

    @Test
    void isValid() {
        assertTrue(BlockOption.create(0).isValid());
        assertTrue(BlockOption.create(5).isValid());
        assertFalse(BlockOption.create(7).isValid());
        assertTrue(BlockOption.create(8).isValid());
        assertTrue(BlockOption.create(0, false, 3).isValid());
        assertFalse(BlockOption.create(0, false, 30).isValid());
        assertFalse(BlockOption.create(0, false, -30).isValid());
        assertFalse(BlockOption.create(-1, false, 0).isValid());
    }

    @Test
    void getBlockSize() {
        assertEquals(BlockOption.create(0).getBlockSize(), 16);
        assertEquals(BlockOption.create(1).getBlockSize(), 32);
        assertEquals(BlockOption.create(2).getBlockSize(), 64);
        assertEquals(BlockOption.create(3).getBlockSize(), 128);
        assertEquals(BlockOption.create(4).getBlockSize(), 256);
        assertEquals(BlockOption.create(5).getBlockSize(), 512);
        assertEquals(BlockOption.create(6).getBlockSize(), 1024);
        assertEquals(BlockOption.create(7).getBlockSize(), 0);
        assertEquals(BlockOption.create(8).getBlockSize(), 16);
    }

    @Test
    void getBlockOffset() {
        BlockOption bo = BlockOption.create(0, true, 6);

        assertEquals(0 * 1024, bo.getBlockOffset());

        bo = bo.getNextBlockOption();
        assertEquals(1 * 1024, bo.getBlockOffset());

        bo = bo.getNextBlockOption();
        assertEquals(2 * 1024, bo.getBlockOffset());

        bo = bo.getNextBlockOption();
        assertEquals(3 * 1024, bo.getBlockOffset());

        bo = bo.getSmallerBlockOption();
        assertEquals(3 * 1024, bo.getBlockOffset());
    }

    @Test
    void getMoreFlag() {
        assertFalse(BlockOption.create(0).getMoreFlag());
        assertTrue(BlockOption.create(8).getMoreFlag());
    }

    @Test
    void isLastBlock() {
        assertTrue(BlockOption.create(0).isLastBlock());
        assertFalse(BlockOption.create(8).isLastBlock());
    }

    @Test
    void testToInteger() {
        assertEquals(4801235, BlockOption.create(4801235).toInteger());
        assertEquals(7837, BlockOption.create(7837).toInteger());
        assertEquals(365827, BlockOption.create(365827).toInteger());
    }

    @Test
    void testToString() {
        assertEquals("0/1/1024", BlockOption.create(0, true, 6).toString());
        assertEquals("4/0/128", BlockOption.create(4, false, 3).toString());
    }
}
