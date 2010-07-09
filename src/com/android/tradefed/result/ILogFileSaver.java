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

}
