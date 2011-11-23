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

package com.android.tradefed.util.net;

import com.android.tradefed.util.MultiMap;
import com.android.tradefed.util.net.IHttpHelper.DataSizeException;

import junit.framework.TestCase;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

/**
 * Unit tests for {@link HttpHelper}.
 */
public class HttpHelperTest extends TestCase {

    private static final String TEST_URL_STRING = "http://foo";
    private HttpHelper mHelper;

    /**
     * {@inheritDoc}
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mHelper = new HttpHelper();
    }

    /**
     * Test {@link HttpHelper#buildParameters(MultiMap)}.
     */
    public void testBuildParams() {
        MultiMap<String, String> paramMap = new MultiMap<String, String>();
        paramMap.put("key", "value");
        assertEquals("key=value", mHelper.buildParameters(paramMap));

        paramMap.clear();
        paramMap.put("key1", "value1");
        paramMap.put("key2", "value2");
        String params = mHelper.buildParameters(paramMap);
        assertTrue(params.contains("key1=value1"));
        assertTrue(params.contains("key2=value2"));
        assertTrue(params.contains("&"));

        paramMap.clear();
        paramMap.put("key", "value1");
        paramMap.put("key", "value2");
        assertEquals("key=value1&key=value2", mHelper.buildParameters(paramMap));

        paramMap.clear();
        paramMap.put("key+f?o=o;", "value");
        assertEquals("key%2Bf%3Fo%3Do%3B=value", mHelper.buildParameters(paramMap));
    }

    /**
     * Test {@link HttpHelper#buildUrl(String, MultiMap)} with simple parameters.
     */
    public void testBuildUrl() {
        assertEquals("http://foo", mHelper.buildUrl(TEST_URL_STRING, null));

        MultiMap<String, String> paramMap = new MultiMap<String, String>();
        assertEquals("http://foo", mHelper.buildUrl(TEST_URL_STRING, paramMap));

        paramMap.put("key", "value");
        assertEquals("http://foo?key=value", mHelper.buildUrl(TEST_URL_STRING, paramMap));
    }

    /**
     * Normal case test for {@link HttpHelper#doGet(String)}
     */
    public void testDoGet() throws IOException, DataSizeException {
        final String testString = "this is some data";
        final ByteArrayInputStream mockStream = new ByteArrayInputStream(testString.getBytes());
        HttpHelper helper = new HttpHelper() {
            @Override
            InputStream getRemoteUrlStream(URL url) {
                return mockStream;
            }
        };
        assertEquals(testString, helper.doGet(TEST_URL_STRING));
    }

    /**
     * Test that {@link HttpHelper#doGet(String)} throws {@link DataSizeException} when the
     * remote stream returns too much data.
     */
    public void testDoGet_datasize() throws IOException {
        // test with 64K + 1
        final byte[] bigData = new byte[IHttpHelper.MAX_DATA_SIZE + 1];
        final ByteArrayInputStream mockStream = new ByteArrayInputStream(bigData);
        HttpHelper helper = new HttpHelper() {
            @Override
            InputStream getRemoteUrlStream(URL url) {
                return mockStream;
            }
        };
        try {
            helper.doGet(TEST_URL_STRING);
            fail("DataSizeException not thrown");
        } catch (DataSizeException e) {
            // expected
        }
    }
}
