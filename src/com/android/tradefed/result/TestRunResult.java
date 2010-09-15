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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Holds results from a single test run
 */
public class TestRunResult {
    private final String mTestRunName;
    // Uses a synchronized map to make thread safe. 7
    // Uses a LinkedHashmap to have predictable iteration order
    private Map<TestIdentifier, TestResult> mTestResults =
        Collections.synchronizedMap(new LinkedHashMap<TestIdentifier, TestResult>());
    private Map<String, String> mRunMetrics = null;
    private boolean mIsRunComplete = false;
    private boolean mIsRunFailed = false;
    private long mElapsedTime = 0;
    private Integer mNumFailedTests = null;
    private Integer mNumErrorTests = null;
    private Integer mNumPassedTests = null;

    /**
     * Create a {@link TestRunResult}.
     *
     * @param runName
     */
    public TestRunResult(String runName) {
        mTestRunName = runName;
    }

    /**
     * @return the test run name
     */
    public String getName() {
        return mTestRunName;
    }

    /**
     * Gets a map of the test results.
     * @return
     */
    public Map<TestIdentifier, TestResult> getTestResults() {
        return mTestResults;
    }

    /**
     * Set the test run metrics.
     * <p/>
     * Note: this will replace the currently stored metrics.
     * TODO: find a way to combine metrics instead?
     */
    public void setMetrics(Map<String, String> runMetrics) {
        mRunMetrics = runMetrics;
    }

    /**
     * @return a {@link Map} of the test test run metrics.
     */
    public Map<String, String> getRunMetrics() {
        return mRunMetrics;
    }

    /**
     * Gets the set of tests executed.
     */
    public Set<TestIdentifier> getTests() {
        return mTestResults.keySet();
    }

    /**
     * @return <code>true</code> if test run failed.
     */
    public boolean isRunFailure() {
        return mIsRunFailed;
    }

    /**
     * @return <code>true</code> if test run finished.
     */
    public boolean isRunComplete() {
        return mIsRunComplete;
    }

    void setRunComplete(boolean runComplete) {
        mIsRunComplete = runComplete;
    }

    void setRunFailed(boolean runFailed) {
        mIsRunFailed = runFailed;
    }

    void addElapsedTime(long elapsedTime) {
        mElapsedTime+= elapsedTime;
    }

    private synchronized boolean areTestCountsCalculated() {
        return mNumFailedTests != null;
    }

    private synchronized void calculateTestCounts() {
        mNumFailedTests = 0;
        mNumErrorTests = 0;
        mNumPassedTests = 0;
        for (TestResult result : getTestResults().values()) {
            switch (result.getStatus()) {
                case PASSED: {
                    mNumPassedTests++;
                    break;
                }
                case FAILURE: {
                    mNumFailedTests++;
                    break;
                }
                case ERROR: {
                    mNumErrorTests++;
                    break;
                }
            }
        }
    }

    /**
     * Gets the number of passed tests for this run.
     */
    public int getNumPassedTests() {
        if (!areTestCountsCalculated()) {
            calculateTestCounts();
        }
        return mNumPassedTests;
    }

    /**
     * Gets the number of tests in this run.
     */
    public int getNumTests() {
        return mTestResults.size();
    }

    /**
     * Gets the number of failed tests in this run.
     */
    public int getNumFailedTests() {
        if (!areTestCountsCalculated()) {
            calculateTestCounts();
        }
        return mNumFailedTests;
    }

    /**
     * Gets the number of error tests in this run.
     */
    public int getNumErrorTests() {
        if (!areTestCountsCalculated()) {
            calculateTestCounts();
        }
        return mNumErrorTests;
    }

    /**
     * @return <code>true</code> if test run had any failed or error tests.
     */
    public boolean hasFailedTests() {
        return getNumErrorTests() > 0 || getNumFailedTests() > 0;
    }

    /**
     * @return
     */
    public long getElapsedTime() {
        return mElapsedTime;
    }
}
