/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

import com.android.tradefed.result.ByteArrayInputStreamSource;
import com.android.tradefed.result.InputStreamSource;

import junit.framework.TestCase;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * Unit tests for the {@link StreamUtil} utility class
 */
public class StreamUtilTest extends TestCase {

    /**
     * Verify that {@link StreamUtil#getByteArrayListFromSource} works as expected.
     */
    public void testGetByteArrayListFromSource() throws Exception {
        final String contents = "this is a string";
        final byte[] contentBytes = contents.getBytes();
        final InputStreamSource source = new ByteArrayInputStreamSource(contentBytes);
        final InputStream stream = source.createInputStream();
        final ByteArrayList output = StreamUtil.getByteArrayListFromStream(stream);
        final byte[] outputBytes = output.getContents();

        assertEquals(contentBytes.length, outputBytes.length);
        for (int i = 0; i < contentBytes.length; ++i) {
            assertEquals(contentBytes[i], outputBytes[i]);
        }
    }

    /**
     * Verify that {@link StreamUtil#getByteArrayListFromStream} works as expected.
     */
    public void testGetByteArrayListFromStream() throws Exception {
        final String contents = "this is a string";
        final byte[] contentBytes = contents.getBytes();
        final ByteArrayList output = StreamUtil.getByteArrayListFromStream(
                new ByteArrayInputStream(contentBytes));
        final byte[] outputBytes = output.getContents();

        assertEquals(contentBytes.length, outputBytes.length);
        for (int i = 0; i < contentBytes.length; ++i) {
            assertEquals(contentBytes[i], outputBytes[i]);
        }
    }

    /**
     * Verify that {@link StreamUtil#getStringFromSource} works as expected.
     */
    public void testGetStringFromSource() throws Exception {
        final String contents = "this is a string";
        final InputStreamSource source = new ByteArrayInputStreamSource(contents.getBytes());
        final InputStream stream = source.createInputStream();
        final String output = StreamUtil.getStringFromStream(stream);

        assertEquals(contents, output);
    }

    /**
     * Verify that {@link StreamUtil#getStringFromStream} works as expected.
     */
    public void testGetStringFromStream() throws Exception {
        final String contents = "this is a string";
        final String output = StreamUtil.getStringFromStream(
                new ByteArrayInputStream(contents.getBytes()));
        assertEquals(contents, output);
    }
}

