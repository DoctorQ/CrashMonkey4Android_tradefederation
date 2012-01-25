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

import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.MultiMap;
import com.android.tradefed.util.StreamUtil;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

/**
 * Contains helper methods for making http requests
 */
public class HttpHelper implements IHttpHelper {

    /**
     * {@inheritDoc}
     */
    @Override
    public String buildUrl(String baseUrl, MultiMap<String, String> paramMap) {
        StringBuilder urlBuilder = new StringBuilder(baseUrl);
        if (paramMap != null && !paramMap.isEmpty()) {
            urlBuilder.append("?");
            urlBuilder.append(buildParameters(paramMap));
        }
        return urlBuilder.toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String buildParameters(MultiMap<String, String> paramMap) {
        StringBuilder urlBuilder = new StringBuilder("");
        boolean first = true;
        for (String key : paramMap.keySet()) {
            for (String value : paramMap.get(key)) {
                if (!first) {
                    urlBuilder.append("&");
                } else {
                    first = false;
                }
                try {
                    urlBuilder.append(URLEncoder.encode(key, "UTF-8"));
                    urlBuilder.append("=");
                    urlBuilder.append(URLEncoder.encode(value, "UTF-8"));
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
    public String doGet(String url) throws IOException, DataSizeException {
        CLog.d("Performing GET request for %s", url);
        InputStream remote = null;
        byte[] bufResult = new byte[MAX_DATA_SIZE];
        int currBufPos = 0;

        try {
            remote = getRemoteUrlStream(new URL(url));
            int bytesRead;
            // read data from stream into temporary buffer
            while ((bytesRead = remote.read(bufResult, currBufPos,
                    bufResult.length - currBufPos)) != -1) {
                currBufPos += bytesRead;
                if (currBufPos >= bufResult.length) {
                    throw new DataSizeException();
                }
            }

            return new String(bufResult, 0, currBufPos);
        } finally {
            StreamUtil.closeStream(remote);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void doGetIgnore(String url) throws IOException {
        CLog.d("Performing GET request for %s. Ignoring result.", url);
        InputStream remote = null;
        try {
            remote = getRemoteUrlStream(new URL(url));
        } finally {
            StreamUtil.closeStream(remote);
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
    public HttpURLConnection createConnection(URL url, String method, String contentType)
            throws IOException {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod(method);
        if (contentType != null) {
            connection.setRequestProperty("Content-Type", contentType);
        }
        connection.setDoInput(true);
        connection.setDoOutput(true);
        return connection;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public HttpURLConnection createXmlConnection(URL url, String method) throws IOException {
        return createConnection(url, method, "text/xml");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public HttpURLConnection createJsonConnection(URL url, String method) throws IOException {
        return createConnection(url, method, "text/json");
    }
}
