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

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
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
    @Option(name = "coverage-metadata-file-path", description =
            "The path of the Emma coverage meta data file used to generate the report.")
    private String mCoverageMetaFilePath = null;

    @Option(name = "coverage-output-path", description =
            "The location where to store the html coverage reports.",
            mandatory = true)
    private String mReportRootPath = null;

    @Option(name = "coverage-metadata-label", description =
            "The label of the Emma coverage meta data zip file inside the IBuildInfo.")
    private String mCoverageMetaZipFileName = "emma_meta.zip";

    static private int REPORT_GENERATION_TIMEOUT_MS = 3 * 60 * 1000;

    static public String XML_REPORT_NAME = "report.xml";

    private IBuildInfo mBuildInfo;
    private ILogFileSaver mLogFileSaver;

    private File mLocalTmpDir = null;
    private File mCoverageFile = null;
    private File mCoverageMetaFile = null;
    private File mXMLReportFile = null;
    private File mReportOutputPath = null;

    public void setMetaZipFilePath(String filePath) {
        mCoverageMetaFilePath = filePath;
    }

    public void setReportRootPath(String rootPath) {
        mReportRootPath = rootPath;
    }

    public void setMetaZipFileName(String filename) {
        mCoverageMetaZipFileName = filename;
    }

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

    public File getXMLReportFile() {
        return mXMLReportFile;
    }

    public File getReportOutputPath() {
        return mReportOutputPath;
    }

    public File getHTMLReportFile() {
        return new File(mReportOutputPath, "index.html");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void invocationStarted(IBuildInfo buildInfo) {
        mBuildInfo = buildInfo;

        // Append build and branch information to output directory.
        mReportOutputPath = generateReportLocation(mReportRootPath);
        CLog.d("ReportOutputPath: %s", mReportOutputPath);

        mXMLReportFile = new File(mReportOutputPath, XML_REPORT_NAME);
        CLog.d("ReportOutputPath: %s", mXMLReportFile);

        // We want to save all other files in the same directory as the report.
        mLogFileSaver = new LogFileSaver(mReportOutputPath);

        CLog.d("ReportOutputPath %s", mReportOutputPath.getAbsolutePath());
        CLog.d("LogfileSaver file dir %s", mLogFileSaver.getFileDir().getAbsolutePath());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void invocationEnded(long elapsedTime) {
        // Generate report
        generateReport();
    }

    public void generateReport() {
        CLog.d("Generating report for code coverage");
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
        File coverageZipFile = mBuildInfo.getFile(mCoverageMetaZipFileName);
        CLog.d("Coverage zip file: %s", coverageZipFile.getAbsolutePath());

        Assert.assertNotNull("Failed to get the coverage metadata zipfile.", coverageZipFile);
        try {
            mLocalTmpDir = FileUtil.createTempDir("emma-meta");
            ZipFile zipFile = new ZipFile(coverageZipFile);
            FileUtil.extractZip(zipFile, mLocalTmpDir);
            File coverageMetaFile;
            if (mCoverageMetaFilePath == null) {
                coverageMetaFile = FileUtil.findFile(mLocalTmpDir, "coverage.em");
            } else {
                coverageMetaFile = new File(mLocalTmpDir, mCoverageMetaFilePath);
            }
            if (coverageMetaFile.exists()) {
                mCoverageMetaFile = coverageMetaFile;
                CLog.d("Coverage meta data file %s", mCoverageMetaFile.getAbsolutePath());
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
        FileUtil.mkdirsRWX(buildIdPath);
        return buildIdPath;
    }

    private void generateCoverageReport(File coverageFile, File metaFile) {
        Assert.assertNotNull("Could not find a valid coverage file.", coverageFile);
        Assert.assertNotNull("Could not find a valid meta data coverage file.", metaFile);
        // Assume emma.jar is in the path.
        String cmd[] = {
                "java", "-cp", "emma.jar", "emma", "report", "-r", "html", "-r", "xml",
                "-in", coverageFile.getAbsolutePath(), "-in", metaFile.getAbsolutePath(),
                "-Dreport.html.out.file=" + mReportOutputPath.getAbsolutePath() + "/index.html",
                "-Dreport.xml.out.file=" + mReportOutputPath.getAbsolutePath() + "/report.xml"
        };
        IRunUtil runUtil = RunUtil.getDefault();
        CommandResult result = runUtil.runTimedCmd(REPORT_GENERATION_TIMEOUT_MS, cmd);
        if (!result.getStatus().equals(CommandStatus.SUCCESS)) {
            CLog.d("Failed to generate coverage report for %s.", coverageFile.getAbsolutePath());
        }
    }
}
