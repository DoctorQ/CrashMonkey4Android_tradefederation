/*
 * Copyright (C) 2011 The Android Open Source Project
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
package com.android.wireless.tests;

import com.android.ddmlib.testrunner.IRemoteAndroidTestRunner;
import com.android.ddmlib.testrunner.RemoteAndroidTestRunner;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.BugreportCollector;
import com.android.tradefed.result.CollectingTestListener;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;

import junit.framework.Assert;

import java.util.HashMap;
import java.util.Map;

/**
 * Run radio startup stress test. The test stresses the radio by run-time reboot a device
 * for multiple times. In each iteration, voice and/or data connection is verified.
 */
public class RadioStressTest implements IRemoteTest, IDeviceTest {
    private ITestDevice mTestDevice = null;
    private static String mTestName = "RadioStartupStress";
    /* Maxium time to wait for SETUP_DATA_CALL */
    private static int MAX_DATA_SETUP_TIME = 5 * 60 * 1000;
    /* Time to wait for framework to bootup */
    private static final int BOOTING_TIME = 2 * 60 * 1000;

    // Define instrumentation test package and runner.
    private static final String TEST_PACKAGE_NAME = "com.android.phonetests";
    private static final String TEST_RUNNER_NAME = ".PhoneInstrumentationStressTestRunner";
    private static final String TEST_CLASS_NAME =
        "com.android.phonetests.stress.telephony.TelephonyStress";
    public static final String TEST_METHOD = "testSingleCallPowerUsage";

    // Define metrics for result report
    private static final String mMetricsName = "RadioStartupStress";

    private RadioHelper mRadioHelper;
    @Option(name="iteration",
            description="The number of times to run the tests")
    private int mIteration = 100;

    @Option(name="call-duration",
            description="The time of a call to be held in the test (in seconds)")
    private String mCallDuration = "5";

    @Option(name="phone-number",
            description="The phone number used for outgoing call test")
    private String mPhoneNumber = null;

    @Option(name="voice",
            description="to verify the voice call")
    private boolean mVoiceVerificationFlag = true;

    /**
     * Run radio startup stress test, capture bugreport if the test failed.
     * Report results to dashboard after the test
     */
    @Override
    public void run(ITestInvocationListener listener)
            throws DeviceNotAvailableException {
        CLog.d("input options: mIteration(%s), mCallDuration(%s), mPhoneNumber(%s), "
               + "mVoiceVerificationFlag(%s)", mIteration, mCallDuration, mPhoneNumber,
               mVoiceVerificationFlag);
        Assert.assertNotNull(mTestDevice);
        Assert.assertNotNull(mPhoneNumber);
        mRadioHelper = new RadioHelper(mTestDevice);
        Assert.assertTrue("Activation failed", mRadioHelper.radioActivation());

        int mSuccessRun = 0;
        for (int i = 0; i < mIteration; i++) {
            // reset device before rebooting
            CLog.d("Radio startup test iteration : %d, success runs: %d", i, mSuccessRun);
            mRadioHelper.resetBootComplete();

            // run-time reboot device
            mTestDevice.executeShellCommand("stop");
            mTestDevice.executeShellCommand("start");

            Assert.assertTrue("Device failed to reboot",
                    mRadioHelper.waitForBootComplete());
            mTestDevice.waitForDeviceAvailable();

            // Setup up device
            mTestDevice.enableAdbRoot();
            mTestDevice.postBootSetup();
            mTestDevice.clearErrorDialogs();

            // Wait 2 minutes for the device to fully booting up
            getRunUtil().sleep(BOOTING_TIME);

            // verify voice connection
            if (mVoiceVerificationFlag) {
                boolean voiceRes = verifyVoiceConnection(listener);
                getRunUtil().sleep(MAX_DATA_SETUP_TIME);
                boolean dataRes = verifyDataConnection();
                if (voiceRes && dataRes) {
                    mSuccessRun++;
                }
                if (!dataRes) {
                    // Take a bugreport if data verification failed. A bugreport should be captured
                    // if voice verification failed in the instrumentation test.
                    getBugReport(listener, i);
                }
            } else {
                if (verifyDataConnection()) {
                    mSuccessRun++;
                } else {
                    getBugReport(listener, i);
                }
            }
        }

        CLog.d("success runs out of total %d runs: %d", mIteration, mSuccessRun);

        Map<String, String> runMetrics = new HashMap<String, String>(1);
        runMetrics.put("iteration", String.valueOf(mSuccessRun));
        reportMetrics(mMetricsName, runMetrics, listener);
    }

    /**
     * Capture a bugreport
     * @param listener is the TestInvocationListener
     * @param iteration is the index of the test run
     * @throws DeviceNotAvailableException
     */
    private void getBugReport(ITestInvocationListener listener, int iteration)
            throws DeviceNotAvailableException {
        // take a bug report, it is possible the system crashed
        InputStreamSource bugreport = mTestDevice.getBugreport();
        listener.testLog(String.format("bugreport_%d.txt", iteration), LogDataType.TEXT, bugreport);
        bugreport.cancel();
    }

    private boolean verifyVoiceConnection(ITestInvocationListener listener)
            throws DeviceNotAvailableException {
        CLog.d("Verify voice connection started");
        IRemoteAndroidTestRunner runner = new RemoteAndroidTestRunner(TEST_PACKAGE_NAME,
                TEST_RUNNER_NAME, mTestDevice.getIDevice());
        runner.setClassName(TEST_CLASS_NAME);
        runner.setMethodName(TEST_CLASS_NAME, TEST_METHOD);
        runner.addInstrumentationArg("callduration", mCallDuration);
        runner.addInstrumentationArg("phonenumber", mPhoneNumber);
        runner.addInstrumentationArg("repeatcount", "1");

        // Add bugreport listener for failed test
        BugreportCollector bugListener = new
            BugreportCollector(listener, mTestDevice);
        bugListener.addPredicate(BugreportCollector.AFTER_FAILED_TESTCASES);
        bugListener.setDescriptiveName(mTestName);
        CollectingTestListener collectListener = new CollectingTestListener();

        mTestDevice.runInstrumentationTests(runner, bugListener, collectListener);
        if (collectListener.hasFailedTests()) {
            CLog.d("Voice call failed.");
            return false;
        }
        return true;
    }

    private boolean verifyDataConnection() throws DeviceNotAvailableException {
        // Try three times, just in case the data setup takes a little more time
        // After 10 minutes, fail the test
        long startTime = System.currentTimeMillis();
        while ((System.currentTimeMillis() - startTime) < MAX_DATA_SETUP_TIME) {
            if (mRadioHelper.pingTest()) {
                return true;
            } else {
                getRunUtil().sleep(30 * 1000);
            }
        }
        return false;
    }

    /**
     * Report run metrics by creating an empty test run to stick them in
     * <p />
     * Exposed for unit testing
     */
    private void reportMetrics(String metricsName, Map<String, String> metrics,
            ITestInvocationListener listener) {
        // Create an empty testRun to report the parsed runMetrics
        CLog.d("About to report metrics to %s: %s", metricsName, metrics);
        listener.testRunStarted(metricsName, 0);
        listener.testRunEnded(0, metrics);
    }

    @Override
    public void setDevice(ITestDevice testDevice) {
        mTestDevice = testDevice;
    }

    @Override
    public ITestDevice getDevice() {
        return mTestDevice;
    }

    /**
     * Gets the {@link IRunUtil} instance to use.
     */
    IRunUtil getRunUtil() {
        return RunUtil.getDefault();
    }
}
