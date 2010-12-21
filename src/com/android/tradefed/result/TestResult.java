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

import java.util.Map;

/**
 * Container for a result of a single test.
 */
public class TestResult {

    public enum TestStatus {
        /** Test error */
        ERROR,
        /** Test failed. */
        FAILURE,
        /** Test passed */
        PASSED
    }

    private final TestStatus mStatus;
    private final String mStackTrace;
    private Map<String, String> mMetrics;

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

    /**
     * Get the associated test metrics.
     */
    public Map<String, String> getMetrics() {
        return mMetrics;
    }

    /**
     * Set the test metrics, overriding any previous values.
     */
    public void setMetrics(Map<String, String> metrics) {
        mMetrics = metrics;
    }
}
