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

import com.android.ddmlib.testrunner.ITestRunListener;
import com.android.ddmlib.testrunner.TestIdentifier;

import java.util.Hashtable;
import java.util.Map;
import java.util.Set;

/**
 * A thread-safe {@link ITestRunListener} that will collect all test results.
 */
public class CollectingTestListener implements ITestRunListener {

    enum TestStatus {
        /** Test failed. */
        FAILURE,
        /** Test passed */
        PASSED
    }

    // stores the test results. Hashtable is thread-safe so no need for extra synchronized blocks.
    private Map<TestIdentifier, TestStatus> mTestResults =
        new Hashtable<TestIdentifier, TestStatus>();
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
            mTestResults.put(test, TestStatus.PASSED);
        }
    }

    /**
     * {@inheritDoc}
     */
    public void testFailed(TestFailure status, TestIdentifier test, String trace) {
        mTestResults.put(test, TestStatus.FAILURE);
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
    public Map<TestIdentifier, TestStatus> getTestResults() {
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
        int passed = 0;
        for (TestStatus status : getTestResults().values()) {
            if (TestStatus.PASSED.equals(status)) {
                passed++;
            }
        }
        return passed;
    }
}
