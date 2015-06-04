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

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.command.FatalHostError;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.StreamUtil;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;


/**
 * 保存log的工具类
 * A helper for {@link ITestInvocationListener}'s that will save log data to a file
 */
public class LogFileSaver implements ILogFileSaver {

    private static final int BUFFER_SIZE = 64 * 1024;
    private File mRootDir;

    /**
     * Creates a {@link LogFileSaver}.
     * <p/>
     * Construct a unique file system directory in rootDir/branch/build_id/testTag/uniqueDir
     * <p/>
     * If directory creation fails, will use a temp directory.
     *
     * @param buildInfo the {@link IBuildInfo}
     * @param rootDir the root file system path
     * @param logRetentionDays If provided a '.retention' file will be written to log directory
     *            containing a timestamp equal to current time + logRetentionDays. External cleanup
     *            scripts can use this file to determine when to delete log directories.
     */
    public LogFileSaver(IBuildInfo buildInfo, File rootDir, Integer logRetentionDays) {
        File buildDir = createBuildDir(buildInfo, rootDir);
        // now create unique directory within the buildDir
        try {
            mRootDir = FileUtil.createTempDir("inv_", buildDir);
            if (logRetentionDays != null && logRetentionDays > 0) {
                new RetentionFileSaver().writeRetentionFile(mRootDir, logRetentionDays);
            }
        } catch (IOException e) {
            CLog.e("Unable to create unique directory in %s. Attempting to use tmp dir instead",
                    buildDir.getAbsolutePath());
            CLog.e(e);
            // try to create one in a tmp location instead
            mRootDir = createTempDir();
        }
        CLog.d("Using log file directory %s", mRootDir.getAbsolutePath());
    }

    private File createTempDir() {
        try {
            return FileUtil.createTempDir("inv_");
        } catch (IOException e) {
            // uh oh, this can't be good, abort tradefed
            throw new FatalHostError("Cannot create tmp directory.", e);
        }
    }

    /**
     * Creates a {@link LogFileSaver}.
     * <p/>
     * Construct a unique file system directory in rootDir/branch/build_id/uniqueDir
     *
     * @param buildInfo the {@link IBuildInfo}
     * @param rootDir the root file system path
     */
    public LogFileSaver(IBuildInfo buildInfo, File rootDir) {
        this(buildInfo, rootDir, null);
    }

    /**
     * An alternate {@link LogFileSaver} constructor that will just use given directory as the
     * log storage directory.
     *
     * @param rootDir
     */
    public LogFileSaver(File rootDir) {
        mRootDir = rootDir;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public File getFileDir() {
        return mRootDir;
    }

    /**
     * Attempt to create a folder to store log's for given build info.
     *
     * @param buildInfo the {@link IBuildInfo}
     * @param rootDir the root file system path to create directory from
     * @return a {@link File} pointing to the directory to store log files in
     */
    private File createBuildDir(IBuildInfo buildInfo, File rootDir) {
        File buildReportDir;
        ArrayList<String> pathSegments = new ArrayList<String>();
        if (buildInfo.getBuildBranch() != null) {
            pathSegments.add(buildInfo.getBuildBranch());
        }
        pathSegments.add(buildInfo.getBuildId());
        pathSegments.add(buildInfo.getTestTag());
        buildReportDir = FileUtil.getFileForPath(rootDir, pathSegments.toArray(new String[] {}));

        // if buildReportDir already exists and is a directory - use it.
        if (buildReportDir.exists()) {
            if (buildReportDir.isDirectory()) {
                return buildReportDir;
            } else {
                CLog.w("Cannot create build-specific output dir %s. File already exists.",
                        buildReportDir.getAbsolutePath());
            }
        } else {
            if (FileUtil.mkdirsRWX(buildReportDir)) {
                return buildReportDir;
            } else {
                CLog.w("Cannot create build-specific output dir %s. Failed to create directory.",
                        buildReportDir.getAbsolutePath());
            }
        }
        return rootDir;
    }

    /**
     * A helper function that translates a string into something that can be used as a filename
     */
    private static String sanitizeFilename(String name) {
        return name.replace(File.separatorChar, '_');
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public File saveLogData(String dataName, LogDataType dataType, InputStream dataStream)
            throws IOException {
        final String saneDataName = sanitizeFilename(dataName);
        // add underscore to end of data name to make generated name more readable
        File logFile = FileUtil.createTempFile(saneDataName + "_", "." + dataType.getFileExt(),
                mRootDir);
        FileUtil.writeToFile(dataStream, logFile);
        CLog.d("Saved log file %s", logFile.getAbsolutePath());
        return logFile;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public File saveAndZipLogData(String dataName, LogDataType dataType, InputStream dataStream)
            throws IOException {
        if (dataType.isCompressed()) {
            CLog.d("Log data for %s is already compressed, skipping compression", dataName);
            return saveLogData(dataName, dataType, dataStream);
        }
        BufferedInputStream bufInput = null;
        ZipOutputStream outStream = null;
        try {
            final String saneDataName = sanitizeFilename(dataName);
            // add underscore to end of data name to make generated name more readable
            File logFile = FileUtil.createTempFile(saneDataName + "_", "."
                    + LogDataType.ZIP.getFileExt(), mRootDir);
            bufInput = new BufferedInputStream(dataStream);
            outStream = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(
                    logFile), BUFFER_SIZE));
            outStream.putNextEntry(new ZipEntry(saneDataName + "." + dataType.getFileExt()));
            StreamUtil.copyStreams(bufInput, outStream);
            CLog.d("Saved log file %s", logFile.getAbsolutePath());
            return logFile;
        } finally {
            StreamUtil.close(bufInput);
            StreamUtil.closeZipStream(outStream);

        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public File createCompressedLogFile(String dataName, LogDataType origDataType,
            LogDataType compressedType) throws IOException {
        // add underscore to end of data name to make generated name more readable
        return FileUtil.createTempFile(dataName + "_",
                String.format(".%s.%s", origDataType.getFileExt(), LogDataType.GZIP.getFileExt()),
                mRootDir);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public OutputStream createGZipLogStream(File logFile) throws IOException {
        return new BufferedOutputStream(new GZIPOutputStream(new FileOutputStream(
                logFile)), BUFFER_SIZE);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public InputStream createInputStreamFromFile(File logFile) throws IOException {
        return new BufferedInputStream(new FileInputStream(logFile), BUFFER_SIZE);
    }
}
