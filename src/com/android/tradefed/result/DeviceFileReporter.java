/*
 * Copyright (C) 2011 The Android Open Source Project
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

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.FileUtil;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * A utility class that checks the device for files and sends them to
 * {@link ITestInvocationListener#testLog(String, LogDataType, InputStreamSource)} if found.
 */
public class DeviceFileReporter {
    private final Map<String, LogDataType> mFilePatterns = new LinkedHashMap<String, LogDataType>();
    private final ITestInvocationListener mListener;
    private final ITestDevice mDevice;

    private LogDataType mDefaultFileType = LogDataType.UNKNOWN;

    /**
     * Initialize a new DeviceFileReporter with the provided {@link ITestDevice}
     */
    public DeviceFileReporter(ITestDevice device, ITestInvocationListener listener) {
        // Do a null check here, since otherwise that error would be asynchronous
        if (device == null || listener == null) {
            throw new NullPointerException();
        }
        mDevice = device;
        mListener = listener;
    }

    /**
     * Add patterns with the log data type set to the default.
     *
     * @param patterns a varargs array of {@link String} filename glob patterns. Should be absolute.
     * @see #setDefaultLogDataType
     */
    public void addPatterns(String... patterns) {
        addPatterns(Arrays.asList(patterns));
    }

    /**
     * Add patterns with the log data type set to the default.
     *
     * @param patterns a {@link List} of {@link String} filename glob patterns. Should be absolute.
     * @see #setDefaultLogDataType
     */
    public void addPatterns(List<String> patterns) {
        for (String pat : patterns) {
            mFilePatterns.put(pat, mDefaultFileType);
        }
    }

    /**
     * Add patterns with the respective log data types
     *
     * @param patterns a {@link Map} of {@link String} filename glob patterns to their respective
     *        {@link LogDataType}s.  The globs should be absolute.
     * @see #setDefaultLogDataType
     */
    public void addPatterns(Map<String, LogDataType> patterns) {
        mFilePatterns.putAll(patterns);
    }

    /**
     * Set the default log data type set for patterns that don't have an associated type.
     *
     * @param type the {@link LogDataType}
     * @see addPatterns(List<String>)
     */
    public void setDefaultLogDataType(LogDataType type) {
        if (type == null) {
            throw new NullPointerException();
        }
        mDefaultFileType = type;
    }

    /**
     * Actually search the filesystem for the specified patterns and send them to
     * {@link ITestInvocationListener#testLog} if found
     */
    public List<String> run() throws DeviceNotAvailableException {
        List<String> filenames = new LinkedList<String>();
        for (Map.Entry<String, LogDataType> pat : mFilePatterns.entrySet()) {
            final String searchCmd = String.format("ls '%s'", pat.getKey());
            final String fileList = mDevice.executeShellCommand(searchCmd);
            for (String filename : fileList.split("\r\n")) {
                if (filename.isEmpty() || filename.endsWith(": No such file or directory")) {
                    continue;
                }
                File file = null;
                InputStreamSource iss = null;
                try {
                    CLog.v("Trying to pull file %s from device %s", filename,
                            mDevice.getSerialNumber());
                    file = mDevice.pullFile(filename);
                    CLog.v("Local file %s has size %d", file, file.length());
                    iss = createIssForFile(file);
                    mListener.testLog(filename, pat.getValue(), iss);
                    filenames.add(filename);
                } catch (IOException e) {
                    CLog.w("Failed to log file %s: %s", filename, e.getMessage());
                } finally {
                    if (iss != null) {
                        iss.cancel();
                        iss = null;
                    }
                    FileUtil.deleteFile(file);
                }
            }
        }
        return filenames;
    }

    /**
     * Create an {@link InputStreamSource} for a file
     * <p />
     * Exposed for unit testing
     */
    InputStreamSource createIssForFile(File file) throws IOException {
        InputStream bufStr = new BufferedInputStream(new FileInputStream(file));
        return new SnapshotInputStreamSource(bufStr);
    }
}

