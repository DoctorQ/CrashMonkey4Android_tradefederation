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

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.result.ITestInvocationListener;

import java.util.Collection;
import java.util.List;

/**
 * A Test that runs a set of individual instrumentation tests.
 */
class InstrumentationListTest extends AbstractRemoteTest implements IDeviceTest, IRemoteTest {

    /** the Android package name of test application */
    private final String mPackageName;
    /** the Android InstrumentationTestRunner class name to use */
    private final String mRunnerName;

    /** the set of tests to run */
    private final Collection<TestIdentifier> mTests;
    /** Aborts the test run if any test takes longer than the specified number of milliseconds */
    private int mTestTimeout = 10 * 60 * 1000;  // default to 10 minutes
    private ITestDevice mDevice = null;

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
     * {@inheritDoc}
     */
    public ITestDevice getDevice() {
        return mDevice;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int countTestCases() {
        return mTests.size();
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
    public void run(final List<ITestInvocationListener> listeners) throws DeviceNotAvailableException {
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
            runner.run(listeners);
        }
        // TODO: capture log here ?
    }
}
