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

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;

/**
 * Helper methods for performing http requests.
 */
public interface IHttpHelper {

    @SuppressWarnings("serial")
    /**
     * Thrown if server response is much larger than expected.
     */
    static class DataSizeException extends Exception {
    }

    public static final int MAX_DATA_SIZE = 64 * 1024;

    /**
     * Fetches the document at the given URL, with the given URL parameters and returns it as a
     * {@link String}.
     * <p/>
     * Because remote contents are loaded into memory, this method should only be used for
     * relatively small data sizes.
     * <p/>
     * References:
     * Java URL Connection:
     * http://java.sun.com/docs/books/tutorial/networking/urls/readingWriting.html
     * Java URL Reader: http://java.sun.com/docs/books/tutorial/networking/urls/readingURL.html
     * Java set Proxy: http://java.sun.com/docs/books/tutorial/networking/urls/_setProxy.html
     *
     * @param urlString the base url
     * @param params the {@link Map} of the parameter name-value pairs to include in the request
     * @return the {@link String} remote contents
     * @throws IOException if failed to retrieve data
     * @throws DataSizeException if retrieved data is > {@link MAX_DATA_SIZE}
     */
    public String fetchUrl(String urlString, Map<String, String> params) throws IOException,
            DataSizeException;

    /**
     * Perform a request at the given URL, ignoring response.
     *
     * @param urlString the base url
     * @param params the {@link Map} of the parameter name-value pairs to include in the request
     * @throws IOException if failed to make connection
     */
    public void doPost(String urlString, Map<String, String> params) throws IOException;

    /**
     * Build the full encoded URL request string.
     *
     * @param baseUrl the base URL
     * @param paramMap the URL parameters
     * @return the constructed URL
     * @throws IllegalArgumentException if an exception occurs encoding the parameters.
     */
    public String buildUrl(String baseUrl, Map<String, String> paramMap);

    /**
     * Creates a connection to given URL for posting xml data.
     *
     * @param url the {@link URL} to connect to.
     * @param method the http request method to use
     * @return the {@link HttpURLConnection}
     * @throws IOException if failed to make connection
     */
    public HttpURLConnection createXmlConnection(URL url, String method) throws IOException;

}
