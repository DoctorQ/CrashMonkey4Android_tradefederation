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

package com.android.tradefed.testtype;

import com.android.ddmlib.Log;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.ResultForwarder;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * A Test that runs a set of individual instrumentation tests.
 */
class InstrumentationListTest implements IDeviceTest, IRemoteTest {

    private static final String LOG_TAG = "InstrumentationListTest";

    /** number of attempts to make if test fails to run */
    static final int FAILED_RUN_TEST_ATTEMPTS = 2;

    /** the Android package name of test application */
    private final String mPackageName;
    /** the Android InstrumentationTestRunner class name to use */
    private final String mRunnerName;

    /** the set of tests to run */
    private final Collection<TestIdentifier> mTests;
    /** Aborts the test run if any test takes longer than the specified number of milliseconds */
    private int mTestTimeout = 10 * 60 * 1000;  // default to 10 minutes
    private ITestDevice mDevice = null;
    private String mRunName = null;
    private Map<String, String> mInstrArgMap = new HashMap<String, String>();

    /**
     * Creates a {@link InstrumentationListTest}.
     *
     * @param packageName the Android manifest package of test application
     * @param runnerName the Instrumentation runner to use
     * @param testsToRun a {@link Collection} of tests to run. Note this {@link Collection} will be
     * used as is (ie a reference to the testsToRun object will be kept).
     */
    InstrumentationListTest(String packageName, String runnerName,
            Collection<TestIdentifier> testsToRun) {
        mPackageName = packageName;
        mRunnerName = runnerName;
        mTests = testsToRun;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setDevice(ITestDevice device) {
        mDevice = device;
    }

    /**
     * Optionally, set the maximum time for each test.
     */
    void setTestTimeout(int timeout) {
        mTestTimeout = timeout;
    }

    /**
     * Optionally, set a custom run name.
     */
    public void setRunName(String runName) {
        mRunName  = runName;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ITestDevice getDevice() {
        return mDevice;
    }

    /**
     * @return the {@link InstrumentationTest} to use. Exposed for unit testing.
     */
    InstrumentationTest createInstrumentationTest() {
        return new InstrumentationTest();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void run(final ITestInvocationListener listener) throws DeviceNotAvailableException {
        if (mDevice == null) {
            throw new IllegalArgumentException("Device has not been set");
        }
        // reuse the InstrumentationTest class to perform actual test run
        for (TestIdentifier testToRun : mTests) {
            InstrumentationTest runner = createInstrumentationTest();
            runner.setDevice(mDevice);
            runner.setPackageName(mPackageName);
            runner.setRunnerName(mRunnerName);
            runner.setClassName(testToRun.getClassName());
            runner.setMethodName(testToRun.getTestName());
            runner.setTestTimeout(mTestTimeout);
            // no need to rerun when executing tests one by one
            runner.setRerunMode(false);
            runner.setRunName(mRunName);
            for (Map.Entry<String, String> entry : mInstrArgMap.entrySet()) {
                runner.addInstrumentationArg(entry.getKey(), entry.getValue());
            }
            runTest(runner, listener, testToRun);
        }
    }

    private void runTest(InstrumentationTest runner, ITestInvocationListener listener,
            TestIdentifier testToRun) throws DeviceNotAvailableException {
        // use a listener filter, to track if the test failed to run
        TestTrackingListener trackingListener = new TestTrackingListener(listener, testToRun);
        for (int i=1; i <= FAILED_RUN_TEST_ATTEMPTS; i++) {
            runner.run(trackingListener);
            if (trackingListener.didTestRun()) {
                return;
            } else {
                CLog.w("Expected test %s did not run on attempt %d of %d", testToRun, i,
                        FAILED_RUN_TEST_ATTEMPTS);
            }
        }
        trackingListener.markTestAsFailed();
    }

    private static class TestTrackingListener extends ResultForwarder {

        private String mRunErrorMsg = null;
        private final TestIdentifier mExpectedTest;
        private boolean mDidTestRun = false;
        private String mRunName;

        public TestTrackingListener(ITestInvocationListener listener,
                TestIdentifier testToRun) {
            super(listener);
            mExpectedTest = testToRun;
        }

        @Override
        public void testRunStarted(String runName, int testCount) {
            super.testRunStarted(runName, testCount);
            mRunName = runName;
        }


        @Override
        public void testRunFailed(String errorMessage) {
            super.testRunFailed(errorMessage);
            mRunErrorMsg  = errorMessage;
        }

        @Override
        public void testEnded(TestIdentifier test, Map<String, String> testMetrics) {
            super.testEnded(test, testMetrics);
            if (mExpectedTest.equals(test)) {
                mDidTestRun  = true;
            } else {
                // weird, should never happen
                Log.w(LOG_TAG, String.format("Expected test %s, but got test %s", mExpectedTest,
                        test));
            }
        }

        public boolean didTestRun() {
            return mDidTestRun;
        }

        @SuppressWarnings("unchecked")
        public void markTestAsFailed() {
            super.testRunStarted(mRunName, 1);
            super.testStarted(mExpectedTest);
            super.testFailed(TestFailure.ERROR, mExpectedTest, String.format(
                    "Test failed to run. Test run failed due to : %s", mRunErrorMsg));
            if (mRunErrorMsg != null) {
                super.testRunFailed(mRunErrorMsg);
            }
            super.testEnded(mExpectedTest, Collections.EMPTY_MAP);
            super.testRunEnded(0, Collections.EMPTY_MAP);
        }
    }

    void addInstrumentationArgs(Map<String, String> instrArgMap) {
        mInstrArgMap.putAll(instrArgMap);
    }
}
