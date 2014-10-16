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

package com.android.tradefed.result;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Saves logs as files.
 */
public interface ILogFileSaver {

    /**
     * Get the directory used to store files.
     *
     * @return the {@link File} directory
     */
    public File getFileDir();

    /**
     * Save the log data to a file
     *
     * @param dataName a {@link String} descriptive name of the data. e.g. "device_logcat"
     * @param dataType the {@link LogDataType} of the file.
     * @param dataStream the {@link InputStream} of the data.
     * @return the file of the generated data
     * @throws IOException if log file could not be generated
     */
    public File saveLogData(String dataName, LogDataType dataType, InputStream dataStream)
            throws IOException;

    /**
     * Save and compress, if necessary, the log data to a zip file
     *
     * @param dataName a {@link String} descriptive name of the data. e.g. "device_logcat"
     * @param dataType the {@link LogDataType} of the file. Log data which is already compressed,
     *            (ie {@link LogDataType#isCompressed()} is <code>true</code>) will not be zipped.
     * @param dataStream the {@link InputStream} of the data.
     * @return the file of the generated data
     * @throws IOException if log file could not be generated
     */
    public File saveAndZipLogData(String dataName, LogDataType dataType, InputStream dataStream)
            throws IOException;

    /**
     * Creates an empty file for storing compressed log data.
     *
     * @param dataName a {@link String} descriptive name of the data to be stored. e.g.
     *            "device_logcat"
     * @param origDataType the type of {@link LogDataType} to be stored
     * @param compressedType the {@link LogDataType} representing the compression type. ie one of
     *            {@link LogDataType#GZIP} or {@link LogDataType#ZIP}
     * @return a {@link File}
     * @throws IOException if log file could not be created
     */
    public File createCompressedLogFile(String dataName, LogDataType origDataType,
            LogDataType compressedType) throws IOException;

    /**
     * Creates a output stream to write GZIP-compressed data to a file
     *
     * @param dataFile the {@link File} to write to
     * @return the {@link OutputStream} to compress and write data to the file. Callers must close
     *         this stream when complete
     * @throws IOException if stream could not be generated
     */
    public OutputStream createGZipLogStream(File dataFile) throws IOException;

    /**
     * Helper method to create an input stream to read contents of given log file.
     * <p/>
     * TODO: consider moving this method elsewhere. Placed here for now so it easier for current
     * users of this class to mock.
     *
     * @param logFile the {@link File} to read from
     * @return a buffered {@link InputStream} to read file data. Callers must close
     *         this stream when complete
     * @throws IOException if stream could not be generated
     */
    public InputStream createInputStreamFromFile(File logFile) throws IOException;

}
