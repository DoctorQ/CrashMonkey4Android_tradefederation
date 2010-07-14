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

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.ddmlib.testrunner.ITestRunListener.TestFailure;
import com.android.tradefed.targetsetup.BuildInfo;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

import junit.framework.TestCase;

/**
 * Unit tests for {@link XmlResultReporter}.
 */
public class XmlResultReporterTest extends TestCase {

    private XmlResultReporter mResultReporter;
    private ByteArrayOutputStream mOutputStream;

    /**
     * {@inheritDoc}
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mOutputStream = new ByteArrayOutputStream();
        mResultReporter = new XmlResultReporter() {
            @Override
            OutputStream createOutputResultStream(File reportDir) throws IOException {
                return mOutputStream;
            }

            @Override
            String getTimestamp() {
                return "ignore";
            }
        };
        // TODO: use mock file dir instead
        File reportDir = File.createTempFile("foo", "txt");
        reportDir.delete();
        mResultReporter.setReportDir(reportDir);
    }

    /**
     * A simple test to ensure expected output is generated for test run with no tests.
     */
    public void testEmptyGeneration() {
        final String expectedOutput = "<?xml version='1.0' encoding='UTF-8' ?>" +
            "<testsuite name=\"todo\" tests=\"0\" failures=\"0\" errors=\"0\" time=\"1\" " +
            "timestamp=\"ignore\" hostname=\"localhost\"> " +
            "<properties />" +
            "</testsuite>";
        mResultReporter.invocationStarted(new BuildInfo());
        mResultReporter.invocationEnded(1);
        assertEquals(expectedOutput, getOutput());
    }

    /**
     * A simple test to ensure expected output is generated for test run with a single passed test.
     */
    public void testSinglePass() {
        final TestIdentifier testId = new TestIdentifier("FooTest", "testFoo");
        mResultReporter.invocationStarted(new BuildInfo());
        mResultReporter.testStarted(testId);
        mResultReporter.testEnded(testId);
        mResultReporter.invocationEnded(1);
        String output =  getOutput();
        // TODO: consider doing xml based compare
        assertTrue(output.contains("tests=\"1\" failures=\"0\" errors=\"0\""));
        final String testCaseTag = String.format("<testcase name=\"%s\" classname=\"%s\"",
                testId.getTestName(), testId.getClassName());
        assertTrue(output.contains(testCaseTag));
    }

    /**
     * A simple test to ensure expected output is generated for test run with a single failed test.
     */
    public void testSingleFail() {
        final TestIdentifier testId = new TestIdentifier("FooTest", "testFoo");
        final String trace = "this is a trace";
        mResultReporter.invocationStarted(new BuildInfo());
        mResultReporter.testStarted(testId);
        mResultReporter.testFailed(TestFailure.FAILURE, testId, trace);
        mResultReporter.testEnded(testId);
        mResultReporter.invocationEnded(1);
        String output =  getOutput();
        // TODO: consider doing xml based compare
        assertTrue(output.contains("tests=\"1\" failures=\"1\" errors=\"0\""));
        final String testCaseTag = String.format("<testcase name=\"%s\" classname=\"%s\"",
                testId.getTestName(), testId.getClassName());
        assertTrue(output.contains(testCaseTag));
        final String failureTag = String.format("<failure>%s</failure>", trace);
        assertTrue(output.contains(failureTag));
    }

    /**
     * Gets the output produced, stripping it of extraneous whitespace characters.
     */
    private String getOutput() {
        String output = mOutputStream.toString();
        // ignore newlines and tabs whitespace
        output = output.replaceAll("[\\r\\n\\t]", "");
        // replace two ws chars with one
        return output.replaceAll("  ", " ");
    }
}
