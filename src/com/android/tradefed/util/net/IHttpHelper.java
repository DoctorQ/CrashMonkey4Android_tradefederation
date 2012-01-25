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

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

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
     * Build the full encoded URL request string.
     *
     * @param url the base URL
     * @param paramMap the URL parameters
     * @return the constructed URL
     * @throws IllegalArgumentException if an exception occurs encoding the parameters.
     */
    public String buildUrl(String url, MultiMap<String, String> paramMap);

    /**
     * Build the encoded parameter string.
     *
     * @param paramMap the URL parameters
     * @return the encoded parameter string
     * @throws IllegalArgumentException if an exception occurs encoding the parameters.
     */
    public String buildParameters(MultiMap<String, String> paramMap);

    /**
     * Performs a GET HTTP request method for a given URL and returns it as a {@link String}.
     * <p>
     * Because remote contents are loaded into memory, this method should only be used for
     * relatively small data sizes.
     * </p><p>
     * References:
     * </p><ul>
     * <li>Java URL Connection:
     * <a href="http://java.sun.com/docs/books/tutorial/networking/urls/readingWriting.html">
     * http://java.sun.com/docs/books/tutorial/networking/urls/readingWriting.html</a></li>
     * <li>Java URL Reader:
     * <a href="http://java.sun.com/docs/books/tutorial/networking/urls/readingURL.html">
     * http://java.sun.com/docs/books/tutorial/networking/urls/readingURL.html</a></li>
     * <li>Java set Proxy:
     * <a href="http://java.sun.com/docs/books/tutorial/networking/urls/_setProxy.html">
     * http://java.sun.com/docs/books/tutorial/networking/urls/_setProxy.html</a></li>
     * </ul>
     *
     * @param url the URL
     * @return the {@link String} remote contents
     * @throws IOException if failed to retrieve data
     * @throws DataSizeException if retrieved data is > {@link #MAX_DATA_SIZE}
     */
    public String doGet(String url) throws IOException, DataSizeException;

    /**
     * Performs a GET for a given URL, with the given URL parameters ignoring the result.
     *
     * @see #doGet(String)
     * @param url the URL
     * @throws IOException if failed to retrieve data
     */
    public void doGetIgnore(String url) throws IOException;

    /**
     * Create a to given url.
     *
     * @param url the {@link URL} to connect to.
     * @param method the HTTP request method. For example, GET or POST.
     * @param contentType the content type. For example, "text/html".
     * @return The HttpURLConnection
     * @throws IOException if an IOException occurs.
     */
    public HttpURLConnection createConnection(URL url, String method, String contentType)
            throws IOException;

    /**
     * Creates a connection to given URL for passing xml data.
     *
     * @param url the {@link URL} to connect to.
     * @param method the HTTP request method. For example, GET or POST.
     * @return the {@link HttpURLConnection}
     * @throws IOException if failed to make connection
     */
    public HttpURLConnection createXmlConnection(URL url, String method) throws IOException;

    /**
     * Creates a connection to given URL for passing json data.
     *
     * @param url the {@link URL} to connect to.
     * @param method the HTTP request method. For example, GET or POST.
     * @return the {@link HttpURLConnection}
     * @throws IOException if failed to make connection
     */
    public HttpURLConnection createJsonConnection(URL url, String method) throws IOException;
}
