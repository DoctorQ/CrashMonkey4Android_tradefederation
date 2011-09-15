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
import com.android.ddmlib.testrunner.IRemoteAndroidTestRunner.TestSize;
import com.android.ddmlib.testrunner.RemoteAndroidTestRunner;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.Option.Importance;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.CollectingTestListener;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.ResultForwarder;
import com.android.tradefed.result.TestRunResult;
import com.android.tradefed.util.StringEscapeUtils;

import java.io.File;
import java.util.Collection;

/**
 * A Test that runs an instrumentation test package on given device.
 */
@OptionClass(alias = "instrumentation")
public class InstrumentationTest implements IDeviceTest, IResumableTest {

    private static final String LOG_TAG = "InstrumentationTest";

    /** max number of attempts to collect list of tests in package */
    private static final int COLLECT_TESTS_ATTEMPTS = 3;

    static final String DELAY_MSEC_ARG = "delay_msec";

    @Option(name = "package", shortName = 'p',
            description="The manifest package name of the Android test application to run.",
            importance = Importance.IF_UNSET)
    private String mPackageName = null;

    @Option(name = "runner",
            description="The instrumentation test runner class name to use.")
    private String mRunnerName = "android.test.InstrumentationTestRunner";

    @Option(name = "class", shortName = 'c',
            description="The test class name to run.")
    private String mTestClassName = null;

    @Option(name = "method", shortName = 'm',
            description="The test method name to run.")
    private String mTestMethodName = null;

    @Option(name = "test-package",
            description="Only run tests within this specific java package. " +
            "Will be ignored if --class is set.")
    private String mTestPackageName = null;

    @Option(name = "timeout",
            description="Aborts the test run if any test takes longer than the specified number of "
            + "milliseconds. For no timeout, set to 0.")
    private int mTestTimeout = 10 * 60 * 1000;  // default to 10 minutes

    @Option(name = "size",
            description="Restrict test to a specific test size.")
    private String mTestSize = null;

    @Option(name = "rerun",
            description = "Rerun unexecuted tests individually on same device if test run " +
            "fails to complete.")
    private boolean mIsRerunMode = true;

    @Option(name = "resume",
            description = "Schedule unexecuted tests for resumption on another device " +
            "if first device becomes unavailable.")
    private boolean mIsResumeMode = false;

    @Option(name = "log-delay",
            description="Delay in msec between each test when collecting test information.")
    private int mTestDelay = 15;

    @Option(name = "install-file",
            description="Optional file path to apk file that contains the tests.")
    private File mInstallFile = null;

    @Option(name = "run-name",
            description="Optional custom test run name to pass to listener. " +
            "If unspecified, will use package name.")
    private String mRunName = null;

    private ITestDevice mDevice = null;

    private IRemoteAndroidTestRunner mRunner;

    private Collection<TestIdentifier> mRemainingTests = null;

    private String mCoverageTarget = null;

    /**
     * Max time in ms to allow for the 'max time to shell output response' when collecting tests.
     * TODO: currently the collect tests command may take a long time to even start, so this is set
     * to a overly generous value
     */
    private int mCollectTestsShellTimeout = 2 * 60 * 1000;

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
        mTestMethodName = StringEscapeUtils.escapeShell(testMethodName);
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
     * Get the custom test run name that will be provided to listener
     */
    public String getRunName() {
        return mRunName;
    }

    /**
     * Set the custom test run name that will be provided to listener
     */
    public void setRunName(String runName) {
        mRunName = runName;
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
     * Get the test java package to run.
     */
    String getTestPackageName() {
        return mTestPackageName;
    }

    /**
     * Sets the test package filter.
     * <p/>
     * If non-null, only tests within the given java package will be executed.
     * <p/>
     * Will be ignored if a non-null value has been provided to {@link #setClassName(String)}
     */
    public void setTestPackageName(String testPackageName) {
        mTestPackageName = testPackageName;
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
    public void setTestTimeout(int timeout) {
        mTestTimeout = timeout;
    }

    /**
     * Set the coverage target of this test.
     * <p/>
     * Currently unused. This method is just present so coverageTarget can be later retrieved via
     * {@link #getCoverageTarget()}
     */
    public void setCoverageTarget(String coverageTarget) {
        mCoverageTarget = coverageTarget;
    }

    /**
     * Get the coverageTarget previously set via {@link #setCoverageTarget(String)}.
     */
    public String getCoverageTarget() {
        return mCoverageTarget;
    }

    /**
     * Return <code>true</code> if rerun mode is on.
     */
    boolean isRerunMode() {
        return mIsRerunMode;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isResumable() {
        // hack to not resume if tests were never run
        // TODO: fix this properly in TestInvocation
        if (mRemainingTests == null) {
            return false;
        }
        return mIsResumeMode;
    }

    /**
     * Optionally, set the rerun mode.
     */
    public void setRerunMode(boolean rerun) {
        mIsRerunMode = rerun;
    }

    /**
     * Optionally, set the resume mode.
     */
    public void setResumeMode(boolean resume) {
        mIsResumeMode = resume;
    }

    /**
     * Get the test timeout in ms.
     */
    int getTestTimeout() {
        return mTestTimeout;
    }

    /**
     * Get the delay in ms between each test when collecting test info.
     */
    long getTestDelay() {
        return mTestDelay;
    }

    /**
     * Set the optional file to install that contains the tests.
     *
     * @param installFile the installable {@link File}
     */
    public void setInstallFile(File installFile) {
        mInstallFile = installFile;
    }

    /**
     * {@inheritDoc}
     */
    public ITestDevice getDevice() {
        return mDevice;
    }
    /**
     * Set the max time in ms to allow for the 'max time to shell output response' when collecting
     * tests.
     * <p/>
     * Exposed for testing.
     */
    public void setCollectsTestsShellTimeout(int timeout) {
        mCollectTestsShellTimeout = timeout;
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
    @Override
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
        } else if (mTestPackageName != null) {
            mRunner.setTestPackageName(mTestPackageName);
        }
        if (mTestSize != null) {
            mRunner.setTestSize(TestSize.getTestSize(mTestSize));
        }
        mRunner.setMaxtimeToOutputResponse((int)mTestTimeout);
        if (mRunName != null) {
            mRunner.setRunName(mRunName);
        }

        if (mInstallFile != null) {
            mDevice.installPackage(mInstallFile, true);
            doTestRun(listener);
            mDevice.uninstallPackage(mPackageName);
        } else {
            doTestRun(listener);
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

        if (mRemainingTests != null) {
            // have remaining tests! This must be a rerun - rerun them individually
            rerunTests(listener);
            return;
        }
        mRemainingTests = collectTestsToRun(mRunner);
        if (mRemainingTests == null) {
            mDevice.runInstrumentationTests(mRunner, listener);
        } else if (mRemainingTests.size() != 0) {
            runWithRerun(listener, mRemainingTests);
        } else {
            Log.i(LOG_TAG, String.format("No tests expected for %s, skipping", mPackageName));
        }
    }

    /**
     * Execute the test run, but re-run incomplete tests individually if run fails to complete.
     *
     * @param listener the {@link ITestInvocationListener}
     * @param expectedTests the full set of expected tests in this run.
     */
    private void runWithRerun(final ITestInvocationListener listener,
            Collection<TestIdentifier> expectedTests) throws DeviceNotAvailableException {
        CollectingTestListener testTracker = new CollectingTestListener();
        mRemainingTests = expectedTests;
        try {
            mDevice.runInstrumentationTests(mRunner, new ResultForwarder(listener, testTracker));
        } finally {
            calculateRemainingTests(mRemainingTests, testTracker);
        }
        rerunTests(listener);
    }

    /**
     * Rerun any <var>mRemainingTests</var> one by one
     *
     * @param listener the {@link ITestInvocationListener}
     * @throws DeviceNotAvailableException
     */
    private void rerunTests(final ITestInvocationListener listener)
            throws DeviceNotAvailableException {
        if (mRemainingTests.size() > 0) {
            InstrumentationListTest testRerunner = new InstrumentationListTest(mPackageName,
                    mRunnerName, mRemainingTests);
            testRerunner.setDevice(getDevice());
            testRerunner.setTestTimeout(getTestTimeout());
            CollectingTestListener testTracker = new CollectingTestListener();
            try {
                testRerunner.run(new ResultForwarder(listener, testTracker));
            } finally {
                calculateRemainingTests(mRemainingTests, testTracker);
            }
        }
    }

    /**
     * Remove the set of tests collected by testTracker from the set of expectedTests
     *
     * @param expectedTests
     * @param testTracker
     */
    private void calculateRemainingTests(Collection<TestIdentifier> expectedTests,
            CollectingTestListener testTracker) {
        expectedTests.removeAll(testTracker.getCurrentRunResults().getTests());
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
            // the collecting test command can fail for large volumes of test bug 1750602. insert a
            // small delay between each test to prevent this
            if (mTestDelay > 0) {
                runner.addInstrumentationArg(DELAY_MSEC_ARG, Integer.toString(mTestDelay));
            }
            // use a shorter timeout when collecting tests
            runner.setMaxtimeToOutputResponse(mCollectTestsShellTimeout);
            // try to collect tests multiple times, in case device is temporarily not available
            // on first attempt
            Collection<TestIdentifier>  tests = collectTestsAndRetry(runner);
            runner.setLogOnly(false);
            runner.setMaxtimeToOutputResponse(mTestTimeout);
            runner.removeInstrumentationArg(DELAY_MSEC_ARG);
            return tests;
        }
        return null;
    }

    /**
     * Performs the actual work of collecting tests, making multiple attempts if necessary
     * @param runner
     * @return the collection of tests, or <code>null</code> if tests could not be collected
     * @throws DeviceNotAvailableException if communication with the device was lost
     */
    private Collection<TestIdentifier> collectTestsAndRetry(final IRemoteAndroidTestRunner runner)
            throws DeviceNotAvailableException {
        boolean communicationFailure = false;
        for (int i=0; i < COLLECT_TESTS_ATTEMPTS; i++) {
            CollectingTestListener listener = new CollectingTestListener();
            boolean instrResult = mDevice.runInstrumentationTests(runner, listener);
            TestRunResult runResults = listener.getCurrentRunResults();
            if (!instrResult || !runResults.isRunComplete()) {
                // communication failure with device, retry
                Log.w(LOG_TAG, String.format(
                        "No results when collecting tests to run for %s on device %s. Retrying",
                        mPackageName, mDevice.getSerialNumber()));
                communicationFailure = true;
            } else if (runResults.isRunFailure()) {
                // not a communication failure, but run still failed.
                // TODO: should retry be attempted
                CLog.w("Run failure %s when collecting tests to run for %s on device %s.",
                        runResults.getRunFailureMessage(), mPackageName,
                        mDevice.getSerialNumber());
                return null;
            } else {
                // success!
                return runResults.getTests();
            }
        }
        if (communicationFailure) {
            // TODO: find a better way to handle this
            // throwing DeviceUnresponsiveException is not always ideal because a misbehaving
            // instrumentation can hang, even though device is responsive. Would be nice to have
            // a louder signal for this situation though than just logging an error
//            throw new DeviceUnresponsiveException(String.format(
//                    "Communication failure when attempting to collect tests %s on device %s",
//                    mPackageName, mDevice.getSerialNumber()));
            CLog.w("Ignoring repeated communication failure when collecting tests %s for device %s",
                    mPackageName, mDevice.getSerialNumber());
        }
        CLog.e("Failed to collect tests to run for %s on device %s.",
                mPackageName, mDevice.getSerialNumber());
        return null;
    }
}
