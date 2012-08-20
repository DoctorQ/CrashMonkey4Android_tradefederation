/*
 * Copyright (C) 2012 The Android Open Source Project
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

import com.android.tradefed.build.AppDeviceBuildInfo;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;

import junit.framework.Assert;

import java.io.File;
import java.io.IOException;
import java.util.zip.ZipFile;

/**
 * A {@link ITestInvocationListener} that will generate code coverage reports.
 * <p/>
 * Used in conjunction with {@link CodeCoverageTest}. This assumes that emma.jar
 * is in the classpath.
 */
@OptionClass(alias = "code-coverage-reporter")
public class CodeCoverageReporter extends StubTestInvocationListener {
    @Option(name = "coverage-metadata-file", description =
            "The path of the Emma coverage meta data file used to generate the report.",
            mandatory = true)
    private String mCoverageMetaFilePath = null;

    @Option(name = "coverage-output-path", description =
            "The location where to store the html coverage reports.",
            mandatory = true)
    private String mReportRootPath = null;

    static private int REPORT_GENERATION_TIMEOUT_MS = 3 * 60 * 1000;

    private IBuildInfo mBuildInfo;
    private ILogFileSaver mLogFileSaver;

    private File mLocalTmpDir = null;
    private File mCoverageFile = null;
    private File mCoverageMetaFile = null;
    private File mReportOutputPath = null;

    /**
     * {@inheritDoc}
     */
    @Override
    public void testLog(String dataName, LogDataType dataType, InputStreamSource dataStream) {
        if (dataType.equals(LogDataType.COVERAGE)) {
            mCoverageFile = saveLogAsFile(dataName, dataType, dataStream);
        }
    }

    private File saveLogAsFile(String dataName, LogDataType dataType,
            InputStreamSource dataStream) {
        try {
            File logFile = mLogFileSaver.saveLogData(dataName, dataType,
                    dataStream.createInputStream());
            return logFile;
        } catch (IOException e) {
            CLog.e(e);
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void invocationStarted(IBuildInfo buildInfo) {
        mBuildInfo = buildInfo;

        // Append build and branch information to output directory.
        mReportOutputPath = generateReportLocation(mReportRootPath);

        // We want to save all other files in the same directory as the report.
        mLogFileSaver = new LogFileSaver(mReportOutputPath);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void invocationEnded(long elapsedTime) {
        // Generate report
        generateReport();
    }

    private void generateReport() {
        fetchAppropriateMetaDataFile();

        generateCoverageReport(mCoverageFile, mCoverageMetaFile);

        // Cleanup residual files.
        if (mCoverageFile != null) {
            FileUtil.recursiveDelete(mCoverageFile);
        }
        if (mLocalTmpDir != null) {
            FileUtil.recursiveDelete(mLocalTmpDir);
        }
    }

    private void fetchAppropriateMetaDataFile() {
        File coverageZipFile = mBuildInfo.getFile("coverage");
        Assert.assertNotNull("Failed to get the coverage metadata zipfile.", coverageZipFile);

        // Unzip files and keep the one we want.
        try {
            mLocalTmpDir = FileUtil.createTempDir("emma-meta");
            ZipFile zipFile = new ZipFile(coverageZipFile);
            FileUtil.extractZip(zipFile, mLocalTmpDir);
            File coverageMetaFile = new File(mLocalTmpDir, mCoverageMetaFilePath);
            if (coverageMetaFile.exists()) {
                mCoverageMetaFile = coverageMetaFile;
            }
        } catch (IOException e) {
            CLog.e(e);
        }
    }

    private File generateReportLocation(String rootPath) {
        String branchName = mBuildInfo.getBuildBranch();
        String buildId = mBuildInfo.getBuildId();
        File branchPath = new File(rootPath, branchName);
        File buildIdPath = new File(branchPath, buildId);
        return buildIdPath;
    }

    private void generateCoverageReport(File coverageFile, File metaFile) {
        Assert.assertNotNull("Could not find a valid coverage file.", coverageFile);
        Assert.assertNotNull("Could not find a valid meta data coverage file.", coverageFile);
        // Assume emma.jar is in the path.
        String cmd = String.format("java -cp emma.jar emma report -r html -in %s -in %s " +
                "-Dreport.html.out.file=%s/index.html",
                coverageFile.getAbsolutePath(),
                metaFile.getAbsolutePath(), mReportOutputPath.getAbsolutePath());
        IRunUtil run_util = RunUtil.getDefault();
        CommandResult result = run_util.runTimedCmd(REPORT_GENERATION_TIMEOUT_MS, cmd);
        CLog.d(result.getStdout());
    }
}
