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
import com.android.tradefed.targetsetup.IBuildInfo;

import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * A thread-safe {@link ITestInvocationListener} that will collect all test results.
 */
public class CollectingTestListener implements ITestInvocationListener {

    public enum TestStatus {
        /** Test error */
        ERROR,
        /** Test failed. */
        FAILURE,
        /** Test passed */
        PASSED
    }

    /**
     * Container for a result of a single test.
     */
    public static class TestResult {
        private final TestStatus mStatus;
        private final String mStackTrace;

        TestResult(TestStatus status, String trace) {
            mStatus = status;
            mStackTrace = trace;
        }

        TestResult(TestStatus status) {
            this(status, null);
        }

        /**
         * Get the {@link TestStatus} result of the test.
         */
        public TestStatus getStatus() {
            return mStatus;
        }

        /**
         * Get the associated {@link String} stack trace. Should be <code>null</code> if
         * {@link #getStatus()} is {@link TestStatus.PASSED}.
         */
        public String getStackTrace() {
            return mStackTrace;
        }
    }

    // Stores the test results
    // Uses a synchronized map to make thread safe.
    // Uses a LinkedHashmap to have predictable iteration order
    private Map<TestIdentifier, TestResult> mTestResults =
        Collections.synchronizedMap(new LinkedHashMap<TestIdentifier, TestResult>());
    private boolean mIsRunComplete = false;
    private boolean mIsRunFailed = false;

    /**
     * {@inheritDoc}
     */
    public void testStarted(TestIdentifier test) {
        // ignore
    }

    /**
     * {@inheritDoc}
     */
    public void testEnded(TestIdentifier test) {
        // only record test pass if failure not already recorded
        if (!mTestResults.containsKey(test)) {
            mTestResults.put(test, new TestResult(TestStatus.PASSED));
        }
    }

    /**
     * {@inheritDoc}
     */
    public void testFailed(TestFailure status, TestIdentifier test, String trace) {
        if (status.equals(TestFailure.ERROR)) {
            mTestResults.put(test, new TestResult(TestStatus.ERROR, trace));
        } else {
            mTestResults.put(test, new TestResult(TestStatus.FAILURE, trace));
        }
    }

    /**
     * {@inheritDoc}
     */
    public synchronized void testRunEnded(long elapsedTime) {
        mIsRunComplete = true;
    }

    /**
     * {@inheritDoc}
     */
    public synchronized void testRunFailed(String errorMessage) {
        mIsRunComplete = true;
        mIsRunFailed = true;
    }

    /**
     * {@inheritDoc}
     */
    public void testRunStarted(int testCount) {
        // ignore
    }

    /**
     * {@inheritDoc}
     */
    public void testRunStopped(long elapsedTime) {
        mIsRunComplete = true;
    }

    /**
     * Return true if test run has completed.
     */
    public synchronized boolean isRunComplete() {
        return mIsRunComplete;
    }

    /**
     * Return true if test run failed before completing.
     */
    public synchronized  boolean isRunFailure() {
        return mIsRunFailed;
    }

    /**
     * Gets the map of test results.
     */
    public Map<TestIdentifier, TestResult> getTestResults() {
        return mTestResults;
    }

    /**
     * Gets the set of tests executed.
     */
    public Set<TestIdentifier> getTests() {
        return mTestResults.keySet();
    }

    /**
     * Gets the number of passed tests.
     */
    public int getNumPassedTests() {
        return getNumTestsWithStatus(TestStatus.PASSED);
    }

    /**
     * Gets the number of failed tests.
     */
    public int getNumFailedTests() {
        return getNumTestsWithStatus(TestStatus.FAILURE);
    }

    /**
     * Gets the number of test with {@link TestStatus.ERROR} status.
     */
    public int getNumErrorTests() {
        return getNumTestsWithStatus(TestStatus.ERROR);
    }

    public boolean hasFailedTests() {
        return getNumErrorTests() > 0 || getNumFailedTests() > 0;
    }

    private int getNumTestsWithStatus(TestStatus status) {
        // TODO: consider caching these values
        int count = 0;
        for (TestResult result : getTestResults().values()) {
            if (status.equals(result.getStatus())) {
                count++;
            }
        }
        return count;
    }

    /**
     * {@inheritDoc}
     */
    public void invocationEnded() {
        // ignore
    }

    /**
     * {@inheritDoc}
     */
    public void invocationFailed(String message, Throwable cause) {
        // ignore
    }

    /**
     * {@inheritDoc}
     */
    public void invocationStarted(IBuildInfo buildInfo) {
        // ignore
    }

    /**
     * {@inheritDoc}
     */
    public void testLog(String dataName, LogDataType dataType, InputStream dataStream) {
        // ignore
    }

    /**
     * {@inheritDoc}
     */
    public void testRunStarted(String name, int numTests) {
        // ignore
    }
}
