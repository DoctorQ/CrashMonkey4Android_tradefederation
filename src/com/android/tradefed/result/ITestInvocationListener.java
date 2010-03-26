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
import com.android.tradefed.targetsetup.IBuildInfo;

import java.io.File;

/**
 * Listener for test results from the test invocation.
 * <p/>
 * A test invocation can itself include multiple test runs, so the sequence of calls would be
 *  - invocationStarted(BuildInfo)
 *  - testRunStarted
 *  - testStarted
 *  - [testFailed]
 *  - testEnded
 *  ....
 *  - testRunEnded
 *  ....
 *  - testRunStarted
 *  ...
 *  - testRunEnded
 *  - invocationEnded()
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
     * An alternate {@link #testRunStarted(int)} that provides a name for the test run.
     *
     * @param name {@link String} name of the test run, unique per invocation
     */
    public void testRunStarted(String name, int numTests);

    /**
     * An alternate {@link #testRunFailed(long)} that provides a log of the test run.
     *
     * @param elapsedTime reported elapsed time, in milliseconds
     * @param name {@link String} name of the test run, unique per invocation
     */
    public void testRunFailed(String errorMessage, File log);

    /**
     * Reports the end of the test invocation.
     *
     * @param buildInfo information about the build being tested
     */
    public void invocationEnded();

}
