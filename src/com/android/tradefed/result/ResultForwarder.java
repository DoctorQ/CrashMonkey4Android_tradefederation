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
import java.util.List;
import java.util.Map;

/**
 * A {@link ITestInvocationListener} that forwards invocation results to a list of other listeners.
 */
public class ResultForwarder implements ITestInvocationListener {

    private final List<ITestInvocationListener> mListeners;

    /**
     * Create a {@link ResultForwarder}.
     *
     * @param listeners the real {@link ITestInvocationListener} to forward results to
     * @param testPackage the {@link ITestPackageDef} that defines the expected tests
     */
    public ResultForwarder(List<ITestInvocationListener> listeners) {
        mListeners = listeners;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void invocationStarted(IBuildInfo buildInfo) {
        for (ITestInvocationListener listener : mListeners) {
            listener.invocationStarted(buildInfo);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void invocationFailed(Throwable cause) {
        for (ITestInvocationListener listener : mListeners) {
            listener.invocationFailed(cause);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void invocationEnded(long elapsedTime) {
        InvocationSummaryHelper.reportInvocationEnded(mListeners, elapsedTime);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TestSummary getSummary() {
        // should never be called
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testLog(String dataName, LogDataType dataType, InputStream dataStream) {
        for (ITestInvocationListener listener : mListeners) {
            listener.testLog(dataName, dataType, dataStream);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testRunStarted(String runName, int testCount) {
        for (ITestInvocationListener listener : mListeners) {
            listener.testRunStarted(runName, testCount);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testRunFailed(String errorMessage) {
        for (ITestInvocationListener listener : mListeners) {
            listener.testRunFailed(errorMessage);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testRunStopped(long elapsedTime) {
        for (ITestInvocationListener listener : mListeners) {
            listener.testRunStopped(elapsedTime);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testRunEnded(long elapsedTime, Map<String, String> runMetrics) {
        for (ITestInvocationListener listener : mListeners) {
            listener.testRunEnded(elapsedTime, runMetrics);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testStarted(TestIdentifier test) {
        for (ITestInvocationListener listener : mListeners) {
            listener.testStarted(test);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testFailed(TestFailure status, TestIdentifier test, String trace) {
        for (ITestInvocationListener listener : mListeners) {
            listener.testFailed(status, test, trace);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testEnded(TestIdentifier test, Map<String, String> testMetrics) {
        for (ITestInvocationListener listener : mListeners) {
            listener.testEnded(test, testMetrics);
        }
    }
}
