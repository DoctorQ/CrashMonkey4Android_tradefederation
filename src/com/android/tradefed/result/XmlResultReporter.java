/*
 * Copyright (C) 2009 The Android Open Source Project
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
import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.config.Option;
import com.android.tradefed.targetsetup.IBuildInfo;

import org.kxml2.io.KXmlSerializer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.TimeZone;

/**
 * Writes JUnit results to an XML files in a format consistent with
 * Ant's XMLJUnitResultFormatter.
 * <p/>
 * Unlike Ant's formatter, this class does not report the execution time of
 * tests.
 * <p/>
 * Collects all test info in memory, then dumps to file when invocation is complete.
 * <p/>
 * Ported from dalvik runner XmlReportPrinter.
 * <p/>
 * Result files will be stored in path constructed via [--output-file-path]/[build_id]
 */
public class XmlResultReporter extends CollectingTestListener {

    private static final String LOG_TAG = "XmlReportReporter";

    private static final String TEST_RESULT_FILE_SUFFIX = ".xml";
    private static final String TEST_RESULT_FILE_PREFIX = "test_result_";

    private static final String TESTSUITE = "testsuite";
    private static final String TESTCASE = "testcase";
    private static final String ERROR = "error";
    private static final String FAILURE = "failure";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_TIME = "time";
    private static final String ATTR_ERRORS = "errors";
    private static final String ATTR_FAILURES = "failures";
    private static final String ATTR_TESTS = "tests";
    //private static final String ATTR_TYPE = "type";
    //private static final String ATTR_MESSAGE = "message";
    private static final String PROPERTIES = "properties";
    private static final String ATTR_CLASSNAME = "classname";
    private static final String TIMESTAMP = "timestamp";
    private static final String HOSTNAME = "hostname";

    /** the XML namespace */
    private static final String ns = null;

    private static final String REPORT_DIR_NAME = "output-file-path";
    @Option(name=REPORT_DIR_NAME, description="root file system path to directory to store xml " +
            "test results and associated logs")
    private File mReportDir = new File(System.getProperty("java.io.tmpdir"));

    private IBuildInfo mBuildInfo = null;

    /**
     * {@inheritDoc}
     */
    @Override
    public void invocationEnded() {
        if (mReportDir != null) {
            generateReport(mReportDir);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void invocationFailed(String message, Throwable e) {
        if (mReportDir != null) {
            generateReport(mReportDir);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void invocationStarted(IBuildInfo buildInfo) {
        if (mReportDir == null) {
            throw new IllegalArgumentException(String.format("missing %s", REPORT_DIR_NAME));
        }
        mBuildInfo = buildInfo;
        File buildReportDir = new File(mReportDir, Integer.toString(mBuildInfo.getBuildId()));
        // if buildReportDir already exists and is a directory - use it.
        if (buildReportDir.exists()) {
            if (buildReportDir.isDirectory()) {
                mReportDir = buildReportDir;
            } else {
                Log.w(LOG_TAG, String.format("Cannot create build-specific output dir %s. File " +
                        "already exists.", buildReportDir.getAbsolutePath()));
            }
        } else {
            if (buildReportDir.mkdir()) {
                mReportDir = buildReportDir;
            } else {
                Log.w(LOG_TAG, String.format("Cannot create build-specific output dir %s. Failed" +
                        " to create directory.", buildReportDir.getAbsolutePath()));
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testRunStarted(String name, int numTests) {
        // ignore for now
    }

    /**
     * Creates a report file and populates it with the report data from the completed tests.
     */
    private void generateReport(File reportDir) {
        String timestamp = getTimestamp();

        OutputStream stream = null;
        try {
            stream = createOutputResultStream(reportDir);
            KXmlSerializer serializer = new KXmlSerializer();
            serializer.setOutput(stream, "UTF-8");
            serializer.startDocument("UTF-8", null);
            serializer.setFeature(
                    "http://xmlpull.org/v1/doc/features.html#indent-output", true);
            // TODO: insert build info
            printTestResults(serializer, timestamp);
            serializer.endDocument();
            String msg = String.format("XML test result file generated at %s. Total tests %d, " +
                    "Failed %d, Error %d", reportDir.getAbsolutePath(), getTestResults().size(),
                    getNumFailedTests(), getNumErrorTests());
            System.out.println(msg);
            Log.i(LOG_TAG, msg);
        } catch (IOException e) {
            Log.e(LOG_TAG, "Failed to generate report data");
            // TODO: consider throwing exception
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    /**
     * Return the current timestamp as a {@link String}.
     */
    String getTimestamp() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        TimeZone gmt = TimeZone.getTimeZone("UTC");
        dateFormat.setTimeZone(gmt);
        dateFormat.setLenient(true);
        String timestamp = dateFormat.format(new Date());
        return timestamp;
    }

    /**
     * Creates the output stream to use for test results. Exposed for mocking.
     */
    OutputStream createOutputResultStream(File reportDir) throws IOException {
        File reportFile = File.createTempFile(TEST_RESULT_FILE_PREFIX, TEST_RESULT_FILE_SUFFIX,
                reportDir);
        Log.i(LOG_TAG, String.format("Created xml report file at %s",
                reportFile.getAbsolutePath()));
        return new FileOutputStream(reportFile);
    }

    void printTestResults(KXmlSerializer serializer, String timestamp) throws IOException {
        Map<TestIdentifier, TestResult>  results = getTestResults();
        serializer.startTag(ns, TESTSUITE);
        serializer.attribute(ns, ATTR_NAME, "todo");
        serializer.attribute(ns, ATTR_TESTS, Integer.toString(results.size()));
        serializer.attribute(ns, ATTR_FAILURES, Integer.toString(getNumFailedTests()));
        serializer.attribute(ns, ATTR_ERRORS, Integer.toString(getNumErrorTests()));
        // TODO: support this for single test run
        serializer.attribute(ns, ATTR_TIME, "0");
        serializer.attribute(ns, TIMESTAMP, timestamp);
        serializer.attribute(ns, HOSTNAME, "localhost");
        serializer.startTag(ns, PROPERTIES);
        serializer.endTag(ns, PROPERTIES);

        for (TestIdentifier test : results.keySet()) {
            print(serializer, test, results.get(test));
        }

        serializer.endTag(ns, TESTSUITE);
    }

    void print(KXmlSerializer serializer, TestIdentifier testId, TestResult testResult)
            throws IOException {

        serializer.startTag(ns, TESTCASE);
        serializer.attribute(ns, ATTR_NAME, testId.getTestName());
        serializer.attribute(ns, ATTR_CLASSNAME, testId.getClassName());
        serializer.attribute(ns, ATTR_TIME, "0");

        if (!TestStatus.PASSED.equals(testResult.getStatus())) {
            String result = testResult.getStatus().equals(TestStatus.FAILURE) ? FAILURE : ERROR;
            serializer.startTag(ns, result);
            // TODO: get message of stack trace ?
//            String msg = testResult.getStackTrace();
//            if (msg != null && msg.length() > 0) {
//                serializer.attribute(ns, ATTR_MESSAGE, msg);
//            }
           // TODO: get class name of stackTrace exception
            //serializer.attribute(ns, ATTR_TYPE, testId.getClassName());
            String stackText = sanitize(testResult.getStackTrace());
            serializer.text(stackText);
            serializer.endTag(ns, result);
        }

        serializer.endTag(ns, TESTCASE);
     }

    /**
     * Returns the text in a format that is safe for use in an XML document.
     */
    private String sanitize(String text) {
        return text.replace("\0", "<\\0>");
    }

    /**
     * Sets the report file to use. Exposed for mocking.
     */
    void setReportDir(File file) {
        mReportDir = file;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testRunLog(String dataName, LogDataType dataType, InputStream dataStream) {
        File logFile;
        try {
            logFile = new LogFileSaver(mReportDir).saveLogData(dataName, dataType, dataStream);
            Log.i(LOG_TAG, String.format("Saved log file %s", logFile.getAbsolutePath()));
        } catch (IOException e) {
            Log.e(LOG_TAG, "Failed to save log data");
            Log.e(LOG_TAG, e);
        }
    }
}