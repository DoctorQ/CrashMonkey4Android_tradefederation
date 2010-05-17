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
package com.android.tradefed.invoker;

import com.android.ddmlib.Log;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.ILeveledLogOutput;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.JUnitToInvocationResultForwarder;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.targetsetup.IBuildInfo;
import com.android.tradefed.targetsetup.IBuildProvider;
import com.android.tradefed.targetsetup.ITargetPreparer;
import com.android.tradefed.targetsetup.TargetSetupError;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;

import junit.framework.Test;
import junit.framework.TestResult;

/**
 * Default implementation of {@link ITestInvocation}.
 * <p/>
 * Loads major objects based on {@link IConfiguration}
 *   - retrieves build
 *   - prepares target
 *   - runs tests
 *   - reports results
 */
public class TestInvocation implements ITestInvocation {

    private static final String LOG_TAG = "TestInvocation";
    static final String TRADEFED_LOG_NAME = "tradefed_log_";

    /**
     * Constructs a {@link TestInvocation}
     */
    public TestInvocation() {
    }

    /**
     * {@inheritDoc}
     */
    public void invoke(ITestDevice device, IConfiguration config) {
        ITestInvocationListener listener = null;
        ILeveledLogOutput logger = null;
        try {
            logger = config.getLogOutput();
            IBuildProvider buildProvider = config.getBuildProvider();
            ITargetPreparer preparer = config.getTargetPreparer();
            Test test = config.getTest();
            listener = config.getTestInvocationListener();
            IBuildInfo info = buildProvider.getBuild();
            preparer.setUp(device, info);
            runTests(device, info, test, listener);
        } catch (TargetSetupError e) {
            handleError(listener, e);
        } catch (IllegalArgumentException e) {
            handleError(listener, e);
        } catch (ConfigurationException e) {
            handleError(listener, e);
        } catch (DeviceNotAvailableException e) {
            handleError(listener, e);
        } catch (Throwable e) {
            Log.e(LOG_TAG, "Uncaught exception!");
            handleError(listener, e);
        }
        if (logger != null && listener != null) {
            listener.testRunLog(TRADEFED_LOG_NAME, LogDataType.TEXT, logger.getLog());
        }
    }

    /**
     * Handle an exception that occurred during test run
     * @param listener
     */
    private void handleError(ITestInvocationListener listener, Throwable e) {
        Log.e(LOG_TAG, e);
        if (listener != null) {
            listener.invocationFailed(e.getMessage(), e);
        }
    }

    /**
     * Runs the test.
     *
     * @param device the {@link ITestDevice} to run tests on
     * @param buildInfo the {@link BuildInfo} describing the build target
     * @param test the {@link Test} to run
     * @param listener the {@link ITestInvocationListener} that listens for test results in real
     * time
     * @throws DeviceNotAvailableException
     */
    private void runTests(ITestDevice device, IBuildInfo buildInfo, Test test,
            ITestInvocationListener listener) throws DeviceNotAvailableException {
        if (test instanceof IDeviceTest) {
            ((IDeviceTest)test).setDevice(device);
        }
        listener.invocationStarted(buildInfo);
        if (test instanceof IRemoteTest) {
            // run as a remote test, so results are forwarded directly to TestInvocationListener
            ((IRemoteTest) test).run(listener);
        } else {
            listener.testRunStarted(test.countTestCases());
            long startTime = System.currentTimeMillis();
            // forward the JUnit results to the invocation listener
            JUnitToInvocationResultForwarder resultForwarder =
                new JUnitToInvocationResultForwarder(listener);
            TestResult result = new TestResult();
            result.addListener(resultForwarder);
            test.run(result);
            listener.testRunEnded(System.currentTimeMillis() - startTime);
        }
        listener.invocationEnded();
    }
}
