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

import com.android.ddmlib.Log.LogLevel;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.log.LogUtil.CLog;

import junit.textui.ResultPrinter;

import java.io.File;
import java.io.IOException;
import java.util.Map;

/**
 * A test result reporter that forwards results to the JUnit text result printer.
 */
@OptionClass(alias = "stdout")
public class TextResultReporter extends InvocationToJUnitResultForwarder
    implements ITestInvocationListener {

    private ILogFileSaver mLogFileSaver;

    private static final String REPORT_DIR_NAME = "output-file-path";
    @Option(name = REPORT_DIR_NAME, description =
            "root file system path to directory to store logs. Ignored if --save-logs is set.")
    private File mReportDir = new File(System.getProperty("java.io.tmpdir"));

    @Option(name = "save-logs", description = "save any logs to local disk.")
    private boolean mSaveLogs = true;

    /**
     * Creates a {@link TextResultReporter}.
     */
    public TextResultReporter() {
        super(new ResultPrinter(System.out));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void invocationStarted(IBuildInfo buildInfo) {
        super.invocationStarted(buildInfo);
        mLogFileSaver = new LogFileSaver(buildInfo, mReportDir);
    }

    /**
     * Overrides parent to explicitly print out failures. The ResultPrinter relies on the runner
     * calling "print" at end of test run to do this.
     * {@inheritDoc}
     */
    @Override
    public void testFailed(TestFailure status, TestIdentifier testId, String trace) {
        ResultPrinter printer = (ResultPrinter)getJUnitListener();
        printer.getWriter().format("\nTest %s: %s \n stack: %s ", status, testId, trace);
    }

    /**
     * Overrides parent to explicitly print out test metrics.
     */
    @Override
    public void testEnded(TestIdentifier testId, Map<String, String> metrics) {
        super.testEnded(testId, metrics);
        if (!metrics.isEmpty()) {
            ResultPrinter printer = (ResultPrinter)getJUnitListener();
            printer.getWriter().format("\n%s metrics: %s\n", testId, metrics);
        }
    }

    /**
     * Overrides parent to explicitly print out metrics.
     */
    @Override
    public void testRunEnded(long elapsedTime, Map<String, String> metrics) {
        super.testRunEnded(elapsedTime, metrics);
        if (!metrics.isEmpty()) {
            ResultPrinter printer = (ResultPrinter)getJUnitListener();
            printer.getWriter().format("\nMetrics: %s\n", metrics);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testLog(String dataName, LogDataType dataType, InputStreamSource dataStream) {
        if (mSaveLogs) {
            try {
                File logFile = mLogFileSaver.saveLogData(dataName, dataType,
                        dataStream.createInputStream());
                CLog.logAndDisplay(LogLevel.INFO, "Saved %s log to %s", dataName,
                        logFile.getAbsolutePath());
            } catch (IOException e) {
                CLog.e("Failed to save log data");
                CLog.e(e);
            }
        }
    }
}
