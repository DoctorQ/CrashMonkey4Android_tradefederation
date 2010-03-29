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
 */
public class XmlResultReporter extends CollectingTestListener implements ITestInvocationListener {

    @Option(name="report-file", description="path file to xml test result report")
    private File mReportFile = null;

    private IBuildInfo mBuildInfo = null;

    private static final String LOG_TAG = "XmlReportReporter";

    private static final String TESTSUITE = "testsuite";
    private static final String TESTCASE = "testcase";
    private static final String ERROR = "error";
    private static final String FAILURE = "failure";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_TIME = "time";
    private static final String ATTR_ERRORS = "errors";
    private static final String ATTR_FAILURES = "failures";
    private static final String ATTR_TESTS = "tests";
    private static final String ATTR_TYPE = "type";
    private static final String ATTR_MESSAGE = "message";
    private static final String PROPERTIES = "properties";
    private static final String ATTR_CLASSNAME = "classname";
    private static final String TIMESTAMP = "timestamp";
    private static final String HOSTNAME = "hostname";

    /** the XML namespace */
    private static final String ns = null;

    /**
     * {@inheritDoc}
     */
    public void invocationEnded() {
        generateReport(mReportFile);
    }

    /**
     * {@inheritDoc}
     */
    public void invocationFailed(String message, Throwable e) {
        generateReport(mReportFile);
    }

    /**
     * {@inheritDoc}
     */
    public void invocationStarted(IBuildInfo buildInfo) {
        if (mReportFile == null) {
            throw new IllegalArgumentException("missing report-file");
        }
        mBuildInfo = buildInfo;
    }

    /**
     * {@inheritDoc}
     */
    public void testRunFailed(String errorMessage, File log) {
        Log.i(LOG_TAG, String.format("Run failed: %s", errorMessage));
    }

    /**
     * {@inheritDoc}
     */
    public void testRunStarted(String name, int numTests) {
        // ignore for now
    }

    /**
     * Populates the report file with the report data from the completed tests.
     */
    private void generateReport(File reportFile) {
        String timestamp = getTimestamp();

        OutputStream stream = null;
        try {
            stream = createOutputStream(reportFile);
            KXmlSerializer serializer = new KXmlSerializer();
            serializer.setOutput(stream, "UTF-8");
            serializer.startDocument("UTF-8", null);
            serializer.setFeature(
                    "http://xmlpull.org/v1/doc/features.html#indent-output", true);
            // TODO: insert build info
            printTestResults(serializer, timestamp);
            serializer.endDocument();
            String msg = String.format("XML test result file generated at %s. Total tests %d, " +
                    "Failed %d, Error %d", reportFile.getAbsolutePath(), getTestResults().size(),
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
     * Creates the output stream to use. Exposed for mocking.
     */
    OutputStream createOutputStream(File reportFile) throws IOException {
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
    void setReportFile(File file) {
        mReportFile = file;
    }
}