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

import com.android.ddmlib.IDevice;
import com.android.ddmlib.Log;
import com.android.ddmlib.testrunner.IRemoteAndroidTestRunner;
import com.android.ddmlib.testrunner.ITestRunListener;
import com.android.ddmlib.testrunner.RemoteAndroidTestRunner;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.ddmlib.testrunner.ITestRunListener.TestFailure;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.result.CollectingTestListener;
import com.android.tradefed.testtype.TestTimeoutListener.ITimeoutCallback;

import java.util.ArrayList;
import java.util.Collection;

import junit.framework.TestResult;

/**
 * A Test that runs an instrumentation test package on given device.
 */
public class InstrumentationTest implements IDeviceTest, IRemoteTest, ITimeoutCallback {

    static final String TIMED_OUT_MSG = "timed out: test did not complete in %d ms";
    private static final String LOG_TAG = "InstrumentationTest";

    @Option(name = "package", shortName = 'p',
            description="The manifest package name of the Android test application to run")
    private String mPackageName = null;

    @Option(name = "class", shortName = 'c',
            description="The test class name to run")
    private String mTestClassName = null;

    @Option(name = "method", shortName = 'm',
            description="The test method name to run.")
    private String mTestMethodName = null;

    @Option(name = "timeout",
            description="Aborts the test run if any test takes longer than the specified number of "
            + " milliseconds ")
    private long mTestTimeout = 10 * 60 * 1000;  // default to 10 minutes

    @Option(name = "rerun",
            description="Rerun non-executed tests individually if test run fails to complete")
    private boolean mIsRerunMode = true;

    private ITestDevice mDevice = null;

    private IRemoteAndroidTestRunner mRunner;
    private Collection<ITestRunListener> mListeners;

    /**
     * {@inheritDoc}
     */
    public void setDevice(ITestDevice device) {
        mDevice = device;
    }

    /**
     * Set the Android manifest package to run.
     */
    void setPackageName(String packageName) {
        mPackageName = packageName;
    }

    /**
     * Optionally, set the test class name to run.
     */
    void setClassName(String testClassName) {
        mTestClassName = testClassName;
    }

    /**
     * Optionally, set the test method to run.
     */
    void setMethodName(String testMethodName) {
        mTestMethodName = testMethodName;
    }

    /**
     * Get the class name to run.
     */
    String getClassName() {
        return mTestClassName;
    }

    /**
     * Get the test method to run.
     */
    String getMethodName() {
        return mTestMethodName;
    }

    /**
     * Optionally, set the maximum time for each test.
     */
    void setTestTimeout(long timeout) {
        mTestTimeout = timeout;
    }

    /**
     * Return <code>true</code> if rerun mode is on.
     */
    boolean isRerunMode() {
        return mIsRerunMode;
    }

    /**
     * Optionally, set the rerun mode.
     */
    void setRerunMode(boolean rerun) {
        mIsRerunMode = rerun;
    }

    /**
     * Get the test timeout in ms.
     */
    long getTestTimeout() {
        return mTestTimeout;
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
    public int countTestCases() {
        // TODO: not sure we even want to support this
        // a possible implementation is to issue a adb shell am instrument -e count command when
        // this is first called and cache the result
        throw new UnsupportedOperationException();
    }

    /**
     * @return the {@link IRemoteAndroidTestRunner} to use.
     */
    IRemoteAndroidTestRunner createRemoteAndroidTestRunner(String packageName, IDevice device) {
        return new RemoteAndroidTestRunner(packageName, device);
    }

    /**
     * {@inheritDoc}
     */
    public void run(final ITestRunListener listener) {
        if (mPackageName == null) {
            throw new IllegalArgumentException("package name has not been set");
        }
        if (mDevice == null) {
            throw new IllegalArgumentException("Device has not been set");
        }

        mRunner = createRemoteAndroidTestRunner(mPackageName,
                mDevice.getIDevice());
        if (mTestClassName != null) {
            if (mTestMethodName != null) {
                mRunner.setMethodName(mTestClassName, mTestMethodName);
            } else {
                mRunner.setClassName(mTestClassName);
            }
        }

        Collection<TestIdentifier> expectedTests = collectTestsToRun(mRunner);

        mListeners = new ArrayList<ITestRunListener>();
        mListeners.add(listener);

        if (mTestTimeout >= 0) {
            mListeners.add(new TestTimeoutListener(mTestTimeout, this));
        }
        if (expectedTests != null) {
            runWithRerun(listener, expectedTests);
        } else {
            mRunner.run(mListeners);
        }
    }

    /**
     * Execute the test run, but re-run incomplete tests individually if run fails to complete.
     *
     * @param listener {@link ITestRunListener}
     * @param expectedTests the full set of expected tests in this run.
     */
    private void runWithRerun(final ITestRunListener listener,
            Collection<TestIdentifier> expectedTests) {
        CollectingTestListener testTracker = new CollectingTestListener();
        mListeners.add(testTracker);
        mRunner.run(mListeners);
        if (testTracker.isRunFailure() || !testTracker.isRunComplete()) {
            // get the delta incomplete tests
            expectedTests.removeAll(testTracker.getTests());
            InstrumentationListTest testRerunner = new InstrumentationListTest(mPackageName,
                    expectedTests);
            testRerunner.setDevice(getDevice());
            testRerunner.setTestTimeout(getTestTimeout());
            testRerunner.run(listener);
        }
    }

    /**
     * Collect the list of tests that should be executed by this test run.
     * <p/>
     * This will be done by executing the test run in 'logOnly' mode, and recording the list of
     * tests.
     *
     * @param runner the {@link IRemoteAndroidTestRunner} to use to run the tests.
     * @return a {@link Collection} of {@link TestIdentifier}s that represent all tests to be
     * executed by this run
     */
    private Collection<TestIdentifier> collectTestsToRun(IRemoteAndroidTestRunner runner) {
        if (isRerunMode()) {
            runner.setLogOnly(true);
            CollectingTestListener listener = new CollectingTestListener();
            runner.run(listener);
            runner.setLogOnly(false);
            if (!listener.isRunFailure() && listener.isRunComplete()) {
                return listener.getTests();
            }
            Log.w(LOG_TAG, String.format("Failed to collect tests to run for %s",
                    mPackageName));
            // TODO: collect device logcat ?
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    public void testTimeout(TestIdentifier test) {
        mRunner.cancel();
        final String msg = String.format(TIMED_OUT_MSG, mTestTimeout);
        for (ITestRunListener listener : mListeners) {
            listener.testFailed(TestFailure.ERROR, test, msg);
            listener.testRunFailed(msg);
        }
    }

    /**
     * unsupported
     */
    public void run(TestResult result) {
        throw new UnsupportedOperationException();
    }
}
