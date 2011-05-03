/*
 * Copyright (C) 2010 The Android Open Source Project
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

import com.android.tradefed.util.ByteArrayList;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;

/**
 * Utility class for managing input streams.
 */
public class StreamUtil {

    /**
     * Retrieves a {@link String} from a character stream.
     *
     * @param stream the {@link InputStream}
     * @return the {@link String} containing the stream contents
     * @throws IOException if failure occurred reading the stream
     */
    public static String getStringFromStream(InputStream stream) throws IOException {
        Reader ir = new BufferedReader(new InputStreamReader(stream));
        int irChar = -1;
        StringBuilder builder = new StringBuilder();
        while ((irChar = ir.read()) != -1) {
            builder.append((char)irChar);
        }
        return builder.toString();
    }

    /**
     * Retrieves a {@link ByteArrayList} from a byte stream.
     *
     * @param stream the {@link InputStream}
     * @return a {@link ByteArrayList} containing the stream contents
     * @throws IOException if failure occurred reading the stream
     */
    public static ByteArrayList getByteArrayListFromStream(InputStream stream) throws IOException {
        InputStream is = new BufferedInputStream(stream);
        int inputByte = -1;
        ByteArrayList list = new ByteArrayList();
        while ((inputByte = is.read()) != -1) {
            list.add((byte)inputByte);
        }
        list.trimToSize();
        return list;
    }

    /**
     * Copies contents of origStream to destStream.
     * <p/>
     * Recommended to provide a buffered stream for input and output
     *
     * @param inStream the {@link InputStream}
     * @param outStream the {@link OutputStream}
     * @throws IOException
     */
    public static void copyStreams(InputStream inStream, OutputStream outStream)
            throws IOException {
        int data = -1;
        while ((data = inStream.read()) != -1) {
            outStream.write(data);
        }
    }

    /**
     * Closes given input stream.
     *
     * @param inStream the {@link InputStream}. No action taken if inStream is null.
     */
    public static void closeStream(InputStream inStream) {
        if (inStream != null) {
            try {
                inStream.close();
            } catch (IOException e) {
                // ignore
            }
        }
    }

    /**
     * Closes given output stream.
     *
     * @param outStream the {@link OutputStream}. No action taken if outStream is null.
     */
    public static void closeStream(OutputStream outStream) {
        if (outStream != null) {
            try {
                outStream.close();
            } catch (IOException e) {
                // ignore
            }
        }
    }

    /**
     * Attempts to flush the given output stream, and then closes it.
     *
     * @param outStream the {@link OutputStream}. No action taken if outStream is null.
     */
    public static void flushAndCloseStream(OutputStream outStream) {
        if (outStream != null) {
            try {
                outStream.flush();
            } catch (IOException e) {
                // ignore
            }
            try {
                outStream.close();
            } catch (IOException e) {
                // ignore
            }
        }
    }
}
