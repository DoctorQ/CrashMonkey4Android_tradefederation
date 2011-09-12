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

import com.android.tradefed.util.net.IHttpHelper.DataSizeException;

import junit.framework.TestCase;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;

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
     * Test {@link HttpHelper#buildUrl(String, Map)} with a simple parameter.
     */
    public void testBuildUrl() {
        String actualUrl = mHelper.buildUrl(TEST_URL_STRING, buildParamMap("key", "value"));
        assertEquals("http://foo?key=value", actualUrl);
    }

    /**
     * Test {@link HttpHelper#buildUrl(String, Map)} with a two parameters.
     */
    public void testBuildUrl_twoPairs() {
        String actualUrl = mHelper.buildUrl(TEST_URL_STRING, buildParamMap("key", "value", "key2",
                "value2"));
        assertEquals("http://foo?key=value&key2=value2", actualUrl);
    }

    /**
     * Test {@link HttpHelper#buildUrl(String, Map)} with a parameter that needs encoding.
     */
    public void testBuildUrl_encode() {
        String actualUrl = mHelper.buildUrl(TEST_URL_STRING, buildParamMap("key+f?o=o;", "value"));
        assertEquals("http://foo?key%2Bf%3Fo%3Do%3B=value", actualUrl);
    }

    /**
     * Normal case test for {@link HttpHelper#fetchUrl(String)}
     */
    public void testFetchUrl() throws IOException, DataSizeException {
        final String testString = "this is some data";
        final ByteArrayInputStream mockStream = new ByteArrayInputStream(testString.getBytes());
        HttpHelper helper = new HttpHelper() {
            @Override
            InputStream getRemoteUrlStream(URL url) throws IOException {
                return mockStream;
            }
        };
        assertEquals(testString, helper.fetchUrl(TEST_URL_STRING));
    }

    /**
     * Test that {@link HttpHelper#fetchUrl(String)} throws {@link DataSizeException} when the
     * remote stream returns too much data.
     */
    public void testFetchUrl_datasize() throws IOException {
        // test with 64K + 1
        final byte[] bigData = new byte[IHttpHelper.MAX_DATA_SIZE + 1];
        final ByteArrayInputStream mockStream = new ByteArrayInputStream(bigData);
        HttpHelper helper = new HttpHelper() {
            @Override
            InputStream getRemoteUrlStream(URL url) throws IOException {
                return mockStream;
            }
        };
        try {
            helper.fetchUrl(TEST_URL_STRING);
            fail("DataSizeException not thrown");
        } catch (DataSizeException e) {
            // expected
        }
    }

    private Map<String, String> buildParamMap(String... values) {
        Map<String, String> paramMap = new LinkedHashMap<String, String>();
        for (int i=1; i < values.length; i+=2) {
            paramMap.put(values[i-1], values[i]);
        }
        return paramMap;
    }
}
