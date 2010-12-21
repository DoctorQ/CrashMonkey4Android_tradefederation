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
import com.android.tradefed.result.TestResult.TestStatus;
import com.android.tradefed.targetsetup.IBuildInfo;

import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A {@link ITestInvocationListener} that will collect all test results.
 * <p/>
 * Although the data structures used in this object are thread-safe, the
 * {@link ITestInvocationListener} callbacks must be called in the correct order.
 */
public class CollectingTestListener implements ITestInvocationListener {

    // Stores the test results
    // Uses a synchronized map to make thread safe.
    // Uses a LinkedHashmap to have predictable iteration order
    private Map<String, TestRunResult> mRunResultsMap =
        Collections.synchronizedMap(new LinkedHashMap<String, TestRunResult>());
    private TestRunResult mCurrentResults = null;

    // cached test constants
    private int mNumPassedTests = 0;
    private int mNumFailedTests = 0;
    private int mNumErrorTests = 0;

    /**
     * {@inheritDoc}
     */
    public void invocationStarted(IBuildInfo buildInfo) {
        // ignore
    }

    /**
     * {@inheritDoc}
     */
    public void testRunStarted(String name, int numTests) {
        if (mRunResultsMap.containsKey(name)) {
            // rerun of previous run. Add test results to it
            mCurrentResults = mRunResultsMap.get(name);
        } else {
            // new run
            mCurrentResults = new TestRunResult(name);
            mRunResultsMap.put(name, mCurrentResults);
        }
        mCurrentResults.setRunComplete(false);
        mCurrentResults.setRunFailureError(null);
    }

    /**
     * {@inheritDoc}
     */
    public void testStarted(TestIdentifier test) {
        // ignore
    }

    /**
     * {@inheritDoc}
     */
    public void testEnded(TestIdentifier test, Map<String, String> testMetrics) {
        if (mCurrentResults == null) {
            throw new IllegalStateException("testEnded called before testRunStarted");
        }
        // only record test pass if failure not already recorded
        if (mCurrentResults.addResult(test, new TestResult(TestStatus.PASSED))) {
            mNumPassedTests++;
        }
        mCurrentResults.getTestResults().get(test).setMetrics(testMetrics);
    }

    /**
     * {@inheritDoc}
     */
    public void testFailed(TestFailure status, TestIdentifier test, String trace) {
        if (mCurrentResults == null) {
            throw new IllegalStateException("testFailed called before testRunStarted");
        }
        if (status.equals(TestFailure.ERROR)) {
            if (mCurrentResults.addResult(test, new TestResult(TestStatus.ERROR, trace))) {
                mNumErrorTests++;
            }
        } else {
            if (mCurrentResults.addResult(test, new TestResult(TestStatus.FAILURE, trace))) {
                mNumFailedTests++;
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public void testRunEnded(long elapsedTime, Map<String, String> runMetrics) {
        if (mCurrentResults == null) {
            throw new IllegalStateException("testRunEnded called before testRunStarted");
        }
        mCurrentResults.setRunComplete(true);
        mCurrentResults.addMetrics(runMetrics);
        mCurrentResults.addElapsedTime(elapsedTime);
    }

    /**
     * {@inheritDoc}
     */
    public void testRunFailed(String errorMessage) {
        if (mCurrentResults == null) {
            throw new IllegalStateException("testRunFailed called before testRunStarted");
        }
        mCurrentResults.setRunFailureError(errorMessage);

    }

    /**
     * {@inheritDoc}
     */
    public void testRunStopped(long elapsedTime) {
        if (mCurrentResults == null) {
            throw new IllegalStateException("testRunStopped called before testRunStarted");
        }
        mCurrentResults.setRunComplete(true);
        mCurrentResults.addElapsedTime(elapsedTime);
    }

    /**
     * Gets the results for the current test run.
     * <p/>
     * Its intended to be called once test run is complete (ie {@link #testRunEnded(long, Map)} has
     * been called). Calling this method before the run has even started
     * (ie before {@link #testRunStarted(String, int)} has been called) is invalid, and will
     * produce a {@link IllegalStateException}.
     *
     * @return the {@link TestRunResult} representing data collected during last test run
     * @throws IllegalStateException if no test run data has been collected. This can occur if this
     *             method is called before {@link #testRunStarted(String, int))} has been called.
     */
    public TestRunResult getCurrentRunResults() {
        if (mCurrentResults == null) {
            throw new IllegalStateException("no current results");
        }
        return mCurrentResults;
    }

    /**
     * Gets the results for all test runs.
     */
    public Collection<TestRunResult> getRunResults() {
        return mRunResultsMap.values();
    }

    /**
     * Gets the total number of tests for all runs.
     */
    public int getNumTotalTests() {
        return getNumFailedTests() + getNumErrorTests() + getNumPassedTests();
    }

    /**
     * Gets the total number of failed tests for all runs.
     */
    public int getNumFailedTests() {
        return mNumFailedTests;
    }

    /**
     * Gets the total number of error tests for all runs.
     */
    public int getNumErrorTests() {
        return mNumErrorTests;
    }

    /**
     * Gets the total number of passed tests for all runs.
     */
    public int getNumPassedTests() {
        return mNumPassedTests;
    }

    /**
     * @returns true if invocation had any failed or error tests.
     */
    public boolean hasFailedTests() {
        return getNumErrorTests() > 0 || getNumFailedTests() > 0;
    }

    /**
     * {@inheritDoc}
     */
    public void invocationEnded(long elapsedTime) {
        // ignore
    }

    /**
     * {@inheritDoc}
     */
    public void invocationFailed(Throwable cause) {
        // ignore
    }

    /**
     * {@inheritDoc}
     */
    public TestSummary getSummary() {
        // ignore
        return null;
    }

    /**
     * {@inheritDoc}
     */
    public void testLog(String dataName, LogDataType dataType, InputStream dataStream) {
        // ignore
    }
}
