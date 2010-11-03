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
import java.util.HashMap;
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
    private Map<String, String> mRunMetrics = new HashMap<String, String>();
    private boolean mIsRunComplete = false;
    private boolean mIsRunFailed = false;
    private long mElapsedTime = 0;
    private int mNumFailedTests = 0;
    private int mNumErrorTests = 0;
    private int mNumPassedTests = 0;

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
     * Adds test run metrics.
     * <p/>
     * Note: this will replace any currently stored metrics with the same key.
     * TODO: find a way to combine metrics instead?
     */
    public void addMetrics(Map<String, String> runMetrics) {
        mRunMetrics.putAll(runMetrics);
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

    /**
     * Gets the number of passed tests for this run.
     */
    public int getNumPassedTests() {
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
        return mNumFailedTests;
    }

    /**
     * Gets the number of error tests in this run.
     */
    public int getNumErrorTests() {
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

    /**
     * Adds a test result.
     *
     * @param test
     * @param testResult
     * @return true if result was added. false if test result had already existed
     */
    public boolean addResult(TestIdentifier test, TestResult testResult) {
        if (!mTestResults.containsKey(test)) {
            mTestResults.put(test, testResult);
            switch (testResult.getStatus()) {
                case ERROR:
                    mNumErrorTests++;
                    break;
                case FAILURE:
                    mNumFailedTests++;
                    break;
                case PASSED:
                    mNumPassedTests++;
                    break;
            }
            return true;
        }
        return false;
    }
}
