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
import com.android.ddmlib.testrunner.IRemoteAndroidTestRunner.TestSize;
import com.android.ddmlib.testrunner.ITestRunListener.TestFailure;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.result.CollectingTestListener;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.testtype.TestTimeoutListener.ITimeoutCallback;
import com.android.tradefed.util.RunUtil;
import com.android.tradefed.util.RunUtil.IRunnableResult;

import java.util.ArrayList;
import java.util.Collection;

import junit.framework.TestResult;

/**
 * A Test that runs an instrumentation test package on given device.
 */
public class InstrumentationTest implements IDeviceTest, IRemoteTest, ITimeoutCallback {

    private static final String LOG_TAG = "InstrumentationTest";

    /** max number of attempts to collect list of tests in package */
    private static final int COLLECT_TESTS_ATTEMPTS = 3;

    /** time in ms between collect list of tests attempts */
    private static final int COLLECT_TESTS_POLL_INTERVAL = 5 * 1000;

    /** max time in ms to allow for single  collect list of tests attempt */
    private static final int COLLECT_TESTS_OP_TIMEOUT = 2 * 60 * 1000;

    /** name of saved device logcat file */
    private static final String DEVICE_LOGCAT_NAME = "%s_logcat_";

    static final String TIMED_OUT_MSG = "timed out: test did not complete in %d ms";

    @Option(name = "package", shortName = 'p',
            description="The manifest package name of the Android test application to run")
    private String mPackageName = null;

    @Option(name = "runner",
            description="The instrumentation test runner class name to use")
    private String mRunnerName = "android.test.InstrumentationTestRunner";

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

    @Option(name = "size",
            description="Restrict test to a specific test size")
    private String mTestSize = null;

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
    public void setPackageName(String packageName) {
        mPackageName = packageName;
    }

    /**
     * Optionally, set the Android instrumentation runner to use.
     */
    public void setRunnerName(String runnerName) {
        mRunnerName = runnerName;
    }

    /**
     * Optionally, set the test class name to run.
     */
    public void setClassName(String testClassName) {
        mTestClassName = testClassName;
    }

    /**
     * Optionally, set the test method to run.
     */
    public void setMethodName(String testMethodName) {
        mTestMethodName = testMethodName;
    }

    /**
     * Optionally, set the test size to run.
     */
    public void setTestSize(String size) {
        mTestSize = size;
    }

    /**
     * Get the Android manifest package to run.
     */
    public String getPackageName() {
        return mPackageName;
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
     * Get the test size to run. Returns <code>null</code> if no size has been set.
     */
    String getTestSize() {
        return mTestSize;
    }

    /**
     * Optionally, set the maximum time for each test.
     */
    public void setTestTimeout(long timeout) {
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
    public void setRerunMode(boolean rerun) {
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
    IRemoteAndroidTestRunner createRemoteAndroidTestRunner(String packageName, String runnerName,
            IDevice device) {
        return new RemoteAndroidTestRunner(packageName, runnerName, device);
    }

    /**
     * {@inheritDoc}
     */
    public void run(final ITestInvocationListener listener) throws DeviceNotAvailableException {
        if (mPackageName == null) {
            throw new IllegalArgumentException("package name has not been set");
        }
        if (mDevice == null) {
            throw new IllegalArgumentException("Device has not been set");
        }

        mRunner = createRemoteAndroidTestRunner(mPackageName, mRunnerName,
                mDevice.getIDevice());
        if (mTestClassName != null) {
            if (mTestMethodName != null) {
                mRunner.setMethodName(mTestClassName, mTestMethodName);
            } else {
                mRunner.setClassName(mTestClassName);
            }
        }
        if (mTestSize != null) {
            mRunner.setTestSize(TestSize.getTestSize(mTestSize));
        }

        try {
            doTestRun(listener);
        } finally {
            listener.testRunLog(String.format(DEVICE_LOGCAT_NAME, mPackageName), LogDataType.TEXT,
                    mDevice.getLogcat());
        }
    }

    /**
     * Execute test run.
     *
     * @param listener the test result listener
     * @throws DeviceNotAvailableException if device stops communicating
     */
    private void doTestRun(final ITestInvocationListener listener)
            throws DeviceNotAvailableException {
        Collection<TestIdentifier> expectedTests = collectTestsToRun(mRunner);

        mListeners = new ArrayList<ITestRunListener>();
        mListeners.add(listener);

        if (mTestTimeout >= 0) {
            mListeners.add(new TestTimeoutListener(mTestTimeout, this));
        }

        if (expectedTests != null) {
            runWithRerun(listener, expectedTests);
        } else {
            mDevice.runInstrumentationTests(mRunner, mListeners);
        }
    }

    /**
     * Execute the test run, but re-run incomplete tests individually if run fails to complete.
     *
     * @param listener {@link ITestRunListener}
     * @param expectedTests the full set of expected tests in this run.
     */
    private void runWithRerun(final ITestInvocationListener listener,
            Collection<TestIdentifier> expectedTests) throws DeviceNotAvailableException {
        CollectingTestListener testTracker = new CollectingTestListener();
        mListeners.add(testTracker);
        mDevice.runInstrumentationTests(mRunner, mListeners);
        if (testTracker.isRunFailure() || !testTracker.isRunComplete()) {
            // get the delta incomplete tests
            expectedTests.removeAll(testTracker.getTests());
            InstrumentationListTest testRerunner = new InstrumentationListTest(mPackageName,
                    mRunnerName, expectedTests);
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
     * @throws DeviceNotAvailableException
     */
    private Collection<TestIdentifier> collectTestsToRun(final IRemoteAndroidTestRunner runner)
            throws DeviceNotAvailableException {
        if (isRerunMode()) {
            Log.d(LOG_TAG, String.format("Collecting test info for %s on device %s",
                    mPackageName, mDevice.getSerialNumber()));
            runner.setLogOnly(true);
            // try to collect tests multiple times, in case device is temporarily not available
            // on first attempt
            CollectingTestsRunnable collectRunnable = new CollectingTestsRunnable(mDevice,
                    mRunner);
            boolean result = RunUtil.runTimedRetry(COLLECT_TESTS_OP_TIMEOUT,
                    COLLECT_TESTS_POLL_INTERVAL, COLLECT_TESTS_ATTEMPTS, collectRunnable);
            runner.setLogOnly(false);
            if (result) {
                return collectRunnable.getTests();
            } else if (collectRunnable.getException() != null) {
                throw collectRunnable.getException();
            } else {
                Log.w(LOG_TAG, String.format("Failed to collect tests to run for %s on device %s",
                        mPackageName, mDevice.getSerialNumber()));
            }
        }
        return null;
    }

    /**
     * Collects list of tests to be executed by this test run.
     * Wrapped as a {@link IRunnableResult} so this command can be re-attempted.
     */
    private static class CollectingTestsRunnable implements IRunnableResult {
        private final IRemoteAndroidTestRunner mRunner;
        private final ITestDevice mDevice;
        private Collection<TestIdentifier> mTests;
        private DeviceNotAvailableException mException;

        public CollectingTestsRunnable(ITestDevice device, IRemoteAndroidTestRunner runner) {
            mRunner = runner;
            mDevice = device;
            mTests = null;
            mException = null;
        }

        public boolean run() {
            CollectingTestListener listener = new CollectingTestListener();
            Collection<ITestRunListener> listeners = new ArrayList<ITestRunListener>(1);
            listeners.add(listener);
            try {
                mDevice.runInstrumentationTests(mRunner, listeners);
                mTests = listener.getTests();
                return !listener.isRunFailure() && listener.isRunComplete();
            } catch (DeviceNotAvailableException e) {
                // TODO: should throw this immediately if it occurs, rather than continuing to
                // retry
                mException = e;
            }
            return false;
        }

        /**
         * Gets the collected tests. Must be called after {@link run}.
         */
        public Collection<TestIdentifier> getTests() {
            return new ArrayList<TestIdentifier>(mTests);
        }

        public DeviceNotAvailableException getException() {
            return mException;
        }
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
