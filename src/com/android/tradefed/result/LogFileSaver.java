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

import com.android.ddmlib.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * A helper for {@link ITestInvocationListener}'s that will save log data to a unique file in
 * a specified directory.
 */
public class LogFileSaver {

    private static final String LOG_TAG = "LogFileSaver";
    private final File mRootDir;

    public LogFileSaver(File rootDir) {
        mRootDir = rootDir;
    }

    /**
     * Save the log data to a file
     *
     * @param dataName a {@link String} descriptive name of the data. e.g. "deviceLogcat"
     * @param dataType the {@link LogDataType} of the file.
     * @param dataStream the {@link InputStream} of the data.
     * @return the file of the generated data
     * @throws IOException if log file could not be generated
     */
    public File saveLogData(String dataName, LogDataType dataType, InputStream dataStream)
            throws IOException {
        BufferedInputStream bufInput = null;
        OutputStream outStream = null;
        try {
            File logFile = File.createTempFile(dataName, "." + dataType.getFileExt(), mRootDir);
            bufInput = new BufferedInputStream(dataStream);
            outStream = new BufferedOutputStream(new FileOutputStream(logFile));
            int inputByte = -1;
            while ((inputByte = bufInput.read()) != -1) {
                outStream.write(inputByte);
            }
            Log.i(LOG_TAG, String.format("Saved log file %s", logFile.getAbsolutePath()));
            return logFile;
        } finally {
            if (bufInput != null) {
                try {
                    bufInput.close();
                } catch (IOException e) {
                    // ignore
                }
            }
            if (outStream != null) {
                try {
                    outStream.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }
}
