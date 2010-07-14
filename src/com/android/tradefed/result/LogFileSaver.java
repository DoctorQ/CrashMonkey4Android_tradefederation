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
import com.android.tradefed.targetsetup.IBuildInfo;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * A helper for {@link ITestInvocationListener}'s that will save log data to a file, in an unique
 * directory constructed from build id under test.
 */
public class LogFileSaver implements ILogFileSaver {

    private static final String LOG_TAG = "LogFileSaver";
    private File mRootDir;

    /**
     * Creates a {@link LogFileSaver}.
     * <p/>
     * Construct a unique file system directory in rootDir/build_id/uniqueDir
     *
     * @param buildInfo the {@link IBuildInfo}
     * @param rootDir the root file system path
     */
    public LogFileSaver(IBuildInfo buildInfo, File rootDir) {
        File buildDir = createBuildDir(buildInfo, rootDir);
        // now create unique directory within the buildDir
        try {
            mRootDir = File.createTempFile("inv_", "", buildDir);
            mRootDir.delete();
            mRootDir.mkdirs();
        } catch (IOException e) {
            Log.e(LOG_TAG, String.format("Unable to create unique directory in %s",
                    buildDir.getAbsolutePath()));
            Log.e(LOG_TAG, e);
            mRootDir = buildDir;
        }
        Log.i(LOG_TAG, String.format("Using log file directory %s", mRootDir.getAbsolutePath()));
    }

    /**
     * {@inheritDoc}
     */
    public File getFileDir() {
        return mRootDir;
    }

    /**
     * Attempt to create a folder to store log's for given build id.
     *
     * @param buildInfo the {@link IBuildInfo}
     * @param rootDir the root file system path to create directory from
     * @return a {@link File} pointing to the directory to store log files in
     */
    private File createBuildDir(IBuildInfo buildInfo, File rootDir) {
        File buildReportDir = new File(rootDir, Integer.toString(buildInfo.getBuildId()));
        // if buildReportDir already exists and is a directory - use it.
        if (buildReportDir.exists()) {
            if (buildReportDir.isDirectory()) {
                return buildReportDir;
            } else {
                Log.w(LOG_TAG, String.format("Cannot create build-specific output dir %s. File " +
                        "already exists.", buildReportDir.getAbsolutePath()));
            }
        } else {
            if (buildReportDir.mkdirs()) {
                // TODO: make parent directories writable too
                buildReportDir.setWritable(true);
                buildReportDir.setReadable(true);
                return buildReportDir;
            } else {
                Log.w(LOG_TAG, String.format("Cannot create build-specific output dir %s. Failed" +
                        " to create directory.", buildReportDir.getAbsolutePath()));
            }
        }
        return rootDir;
    }

    /**
     * {@inheritDoc}
     */
    public File saveLogData(String dataName, LogDataType dataType, InputStream dataStream)
            throws IOException {
        BufferedInputStream bufInput = null;
        OutputStream outStream = null;
        try {
            // add underscore to end of data name to make generated name more readable
            File logFile = File.createTempFile(dataName + "_", "." + dataType.getFileExt(),
                    mRootDir);
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

    /**
     * {@inheritDoc}
     */
    public File saveAndZipLogData(String dataName, LogDataType dataType, InputStream dataStream)
            throws IOException {
        BufferedInputStream bufInput = null;
        ZipOutputStream outStream = null;
        try {
            // add underscore to end of data name to make generated name more readable
            File logFile = File.createTempFile(dataName + "_", "." + LogDataType.ZIP.getFileExt(),
                    mRootDir);
            bufInput = new BufferedInputStream(dataStream);
            outStream = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(
                    logFile)));
            outStream.putNextEntry(new ZipEntry(dataName + "." + dataType.getFileExt()));
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
                    outStream.closeEntry();
                    outStream.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }
}
