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

/**
 * Stub implementation of {@link ITestRunListener}
 */
public class StubTestListener implements ITestRunListener {

    /**
     * {@inheritDoc}
     */
    public void testEnded(TestIdentifier test) {
        // ignore
    }

    /**
     * {@inheritDoc}
     */
    public void testFailed(TestFailure status, TestIdentifier test, String trace) {
        // ignore
    }

    /**
     * {@inheritDoc}
     */
    public void testRunEnded(long elapsedTime) {
        // ignore
    }

    /**
     * {@inheritDoc}
     */
    public void testRunFailed(String errorMessage) {
        // ignore
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
        // ignore
    }

    /**
     * {@inheritDoc}
     */
    public void testStarted(TestIdentifier test) {
        // ignore
    }
}
