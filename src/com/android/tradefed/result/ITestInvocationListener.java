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
import com.android.tradefed.build.IBuildInfo;

import java.io.InputStream;

/**
 * Listener for test results from the test invocation.
 * <p/>
 * A test invocation can itself include multiple test runs, so the sequence of calls will be
 *  - invocationStarted(BuildInfo)
 *  - testRunStarted
 *  - testStarted
 *  - [testFailed]
 *  - testEnded
 *  ....
 *  - testRunEnded
 *  - [testRunLog+]
 *  ....
 *  - testRunStarted
 *  ...
 *  - testRunEnded
 *  - [testRunLog+]
 *  - [invocationFailed]
 *  - invocationEnded
 *  - getSummary
 * <p/>
 * Note that this is re-using the {@link com.android.ddmlib.testrunner.ITestRunListener}
 * because it's a generic interface. The results being reported are not necessarily device specific.
 */
public interface ITestInvocationListener extends ITestRunListener {

    /**
     * Reports the start of the test invocation.
     *
     * @param buildInfo information about the build being tested
     */
    public void invocationStarted(IBuildInfo buildInfo);

    /**
     * Provides the associated log or debug data from the test invocation.
     * <p/>
     * Must be called before {@link #invocationFailed(String, Throwable)} or
     * {@link #invocationEnded()}
     *
     * @param dataName a {@link String} descriptive name of the data. e.g. "device_logcat". Note
     *            dataName may not be unique per invocation. ie implementers must be able to handle
     *            multiple calls with same dataName
     * @param dataType the {@link LogDataType} of the data
     * @param dataStream the {@link InputStreamSource} of the data. Implementers should call
     *        createInputStream to start reading the data, and ensure to close the resulting
     *        InputStream when complete. Callers should ensure the source of the data remains
     *        present and accessible until the testLog method completes.
     */
    public void testLog(String dataName, LogDataType dataType, InputStreamSource dataStream);

    /**
     * Reports that the invocation has terminated, whether successfully or due to some error
     * condition.
     *
     * @param elapsedTime the elapsed time of the invocation in ms
     */
    public void invocationEnded(long elapsedTime);

    /**
     * Reports an incomplete invocation due to some error condition.
     *
     * @param cause the {@link Throwable} cause of the failure
     */
    public void invocationFailed(Throwable cause);

    /**
     * Allows the InvocationListener to return a summary.
     *
     * @return A {@link TestSummary} summarizing the run, or null
     */
    public TestSummary getSummary();

}
