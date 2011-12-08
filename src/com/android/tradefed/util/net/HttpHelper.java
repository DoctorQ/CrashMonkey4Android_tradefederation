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

import com.android.ddmlib.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Map;

/**
 * Contains helper methods for making http requests
 */
public class HttpHelper implements IHttpHelper {

    private static final String LOG_TAG = "HttpHelper";

    /**
     * {@inheritDoc}
     */
    @Override
    public String buildUrl(String baseUrl, Map<String, String> paramMap) {
        StringBuilder urlBuilder = new StringBuilder(baseUrl);
        if (!paramMap.isEmpty()) {
            urlBuilder.append("?");
            boolean first = true;
            for (Map.Entry<String, String> paramPair : paramMap.entrySet()) {
                if (!first) {
                    urlBuilder.append("&");
                } else {
                    first = false;
                }
                try {
                    urlBuilder.append(URLEncoder.encode(paramPair.getKey(), "UTF-8"));
                    urlBuilder.append("=");
                    urlBuilder.append(URLEncoder.encode(paramPair.getValue(), "UTF-8"));
                } catch (UnsupportedEncodingException e) {
                    throw new IllegalArgumentException(e);
                }
            }
        }
        return urlBuilder.toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String fetchUrl(String urlString, Map<String, String> params) throws IOException,
            DataSizeException {
        return fetchUrl(buildUrl(urlString, params));
    }

    /**
     * Perform a http post request, ignoring response.
     *
     * @param urlString
     * @param params
     * @throws IOException
     */
    @Override
    public void doPost(String baseUrlString, Map<String, String> params) throws IOException {
        String urlString = buildUrl(baseUrlString, params);
        URL url = new URL(urlString);
        Log.d(LOG_TAG, String.format("Posting to url %s", urlString));
        InputStream stream = getRemoteUrlStream(url);
        // close without reading
        stream.close();
    }

    /**
     * Fetches the document at the given URL and returns it as a string.
     *
     * @see {@link #fetchUrl(String, Map)}
     *
     * @param urlString the full URL request {@link String} including parameters
     */
    String fetchUrl(String urlString) throws IOException, DataSizeException {
        Log.d(LOG_TAG, String.format("Fetching url %s", urlString));
        URL url = new URL(urlString);
        InputStream remoteStream = null;
        byte[] bufResult = new byte[MAX_DATA_SIZE];
        int currBufPos = 0;

        try {
            remoteStream = getRemoteUrlStream(url);
            int bytesRead;
            // read data from stream into temporary buffer
            while ((bytesRead = remoteStream.read(bufResult, currBufPos,
                    bufResult.length - currBufPos)) != -1) {
                currBufPos += bytesRead;
                if (currBufPos >= bufResult.length) {
                    throw new DataSizeException();
                }
            }

            return new String(bufResult, 0, currBufPos);

        } finally {
            if (remoteStream != null) {
                try {
                    remoteStream.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }

    /**
     * Factory method for opening an input stream to a remote url. Exposed so unit tests can mock
     * out the stream used.
     *
     * @param url the {@link URL}
     * @return the {@link InputStream}
     * @throws IOException if stream could not be opened.
     */
    InputStream getRemoteUrlStream(URL url) throws IOException {
        return url.openStream();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public HttpURLConnection createXmlConnection(URL url, String method) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod(method);
        connection.setRequestProperty("Content-Type", "text/xml");
        connection.setDoInput(true);
        connection.setDoOutput(true);
        return connection;
    }
}
