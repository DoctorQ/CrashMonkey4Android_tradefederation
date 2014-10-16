/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache
 * License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tradefed.util;

import junit.framework.TestCase;

import java.io.IOException;

/**
 * Unit tests for {@link FixedByteArrayOutputStream}.
 */
public class FixedByteArrayOutputStreamTest extends TestCase {

    private static final byte BUF_SIZE = 30;
    private FixedByteArrayOutputStream mOutStream;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mOutStream = new FixedByteArrayOutputStream(BUF_SIZE);
    }

    /**
     * Test the stream works when data written is less than buffer size.
     */
    public void testLessThanBuffer() throws IOException {
        final byte[] data = getData(BUF_SIZE - 2);
        mOutStream.write(data);
        byte[] readData = readData(mOutStream);
        assertEquals(BUF_SIZE - 2, readData.length);
        assertEquals(0, readData[0]);
        assertEquals(BUF_SIZE - 2 - 1, readData[readData.length - 1]);
    }

    /**
     * Test the stream works when data written is exactly equal to buffer size.
     */
    public void testEqualsBuffer() throws IOException {
        final byte[] data = getData(BUF_SIZE);
        mOutStream.write(data);
        byte[] readData = readData(mOutStream);
        assertEquals(BUF_SIZE, readData.length);
        assertEquals(0, readData[0]);
        assertEquals(BUF_SIZE - 1, readData[readData.length - 1]);
    }

    /**
     * Test the stream works when data written is 1 greater than buffer size.
     */
    public void testBufferPlusOne() throws IOException {
        final byte[] data = getData(BUF_SIZE+1);
        mOutStream.write(data);
        byte[] readData = readData(mOutStream);
        assertEquals(BUF_SIZE, readData.length);
        assertEquals(1, readData[0]);
        assertEquals(BUF_SIZE, readData[readData.length - 1]);
    }

    /**
     * Test the stream works when data written is much greater than buffer size.
     */
    public void testBufferPlusPlus() throws IOException {
        final byte[] data = getData(BUF_SIZE * 2 + 10);
        mOutStream.write(data);
        byte[] readData = readData(mOutStream);
        assertEquals(BUF_SIZE, readData.length);
        assertEquals(BUF_SIZE * 2 + 10 - 1, readData[readData.length - 1]);
    }

    /**
     * Reads a byte array from the FixedByteArrayOutputStream.
     */
    private byte[] readData(FixedByteArrayOutputStream outStream) throws IOException {
        return StreamUtil.getByteArrayListFromStream(outStream.getData()).getContents();
    }

    private byte[] getData(int size) {
        assertTrue("size must be a byte value", size > 0 && size < 128);
        byte[] data = new byte[size];

        // fill data with values
        for (byte i = 0; i < data.length; i++) {
            data[i] = i;
        }
        return data;
    }
}
