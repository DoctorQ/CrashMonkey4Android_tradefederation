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
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.StreamUtil;

import junit.framework.Assert;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Run radio outgoing call stress test. The test stresses the voice connection when making
 * outgoing calls, number of failures will be collected and reported.
 */
public class TelephonyTest implements IRemoteTest, IDeviceTest {
    private ITestDevice mTestDevice = null;
    private static String mTestName = "TelephonyTest";
    private static final String mOutputFile = "/data/data/com.android.phone/files/phoneResults.txt";
    private static final int TEST_TIMER = 15 * 60 * 60 * 1000; // 15 hours

    // Define metrics for result report
    private static final String mMetricsName = "PhoneVoiceConnectionStress";
    private final String[] keys = {"CallActiveFailure", "CallDisconnectionFailure", "HangupFailure",
            "ServiceStateChange", "SuccessfulCall"};
    private int[] callStatus = new int[5];
    private RadioHelper mRadioHelper;

    // Define instrumentation test package and runner.
    private static final String TEST_PACKAGE_NAME = "com.android.phonetests";
    private static final String TEST_RUNNER_NAME = ".PhoneInstrumentationStressTestRunner";
    private static final String TEST_CLASS_NAME =
        "com.android.phonetests.stress.telephony.TelephonyStress";
    public static final String TEST_METHOD = "testRadioOnOutgoingCalls";

    @Option(name="call-duration",
            description="The time of a call to be held in the test (in seconds)")
    private String mCallDuration = "5";

    @Option(name="pause-time",
            description="The idle time between two calls (in seconds)")
    private String mPauseTime = "2";

    @Option(name="phone-number",
            description="The phone number used for outgoing call test")
    private String mPhoneNumber = null;

    @Option(name="repeat-count",
            description="The number of calls to make during the test")
    private String mRepeatCount = "1000";

    /**
     * Run the telephony outgoing call stress test
     * Collect results and post results to dash board
     */
    @Override
    public void run(ITestInvocationListener listener)
            throws DeviceNotAvailableException {
        CLog.d("input options: mCallDuration(%s),mPauseTime(%s), mPhoneNumber(%s),"
                + "mRepeatCount(%s)", mCallDuration, mPauseTime, mPhoneNumber, mRepeatCount);

        Assert.assertNotNull(mTestDevice);
        Assert.assertNotNull(mPhoneNumber);
        mRadioHelper = new RadioHelper(mTestDevice);
        // wait for data connection
        if (!mRadioHelper.radioActivation() || !mRadioHelper.waitForDataSetup()) {
            mRadioHelper.getBugreport(listener);
            return;
        }

        IRemoteAndroidTestRunner runner = new RemoteAndroidTestRunner(TEST_PACKAGE_NAME,
                TEST_RUNNER_NAME, mTestDevice.getIDevice());
        runner.setClassName(TEST_CLASS_NAME);
        runner.setMethodName(TEST_CLASS_NAME, TEST_METHOD);

        runner.addInstrumentationArg("callduration", mCallDuration);
        runner.addInstrumentationArg("pausetime", mPauseTime);
        runner.addInstrumentationArg("phonenumber", mPhoneNumber);
        runner.setMaxtimeToOutputResponse(TEST_TIMER);

        // Add bugreport listener for failed test
        BugreportCollector bugListener = new
            BugreportCollector(listener, mTestDevice);
        bugListener.addPredicate(BugreportCollector.AFTER_FAILED_TESTCASES);
        bugListener.setDescriptiveName(mTestName);
        // Device may reboot during the test, to capture a bugreport after that,
        // wait for 30 seconds for device to be online, otherwise, bugreport will be empty
        bugListener.setDeviceWaitTime(30);

        CollectingTestListener collectListener = new CollectingTestListener();
        int remainingCalls = Integer.parseInt(mRepeatCount);

        while (remainingCalls > 0) {
            CLog.d("remaining calls: %s", remainingCalls);
            runner.addInstrumentationArg("repeatcount", String.valueOf(remainingCalls));
            mTestDevice.runInstrumentationTests(runner, bugListener, collectListener);
            if (collectListener.hasFailedTests()) {
                // the test failed
                int numCalls = logOutputFile(bugListener);
                remainingCalls -= numCalls;
                cleanOutputFiles();
            } else {
                // the test passed
                remainingCalls = 0;
            }
        }
        reportMetrics(mMetricsName, bugListener);
    }

    /**
     * Collect number of successful calls and failure reason
     *
     * @param listener
     */
    private int logOutputFile(ITestInvocationListener listener) throws DeviceNotAvailableException {
        File resFile = null;
        int calls = 0;
        resFile = mTestDevice.pullFile(mOutputFile);
        BufferedReader br = null;
        try {
            if (resFile == null) {
                // test failed without writing any results
                // either system crash, or other fails, treat as one failed iteration
                return 1;
            }
            br = new BufferedReader(new FileReader(resFile));
            String line = br.readLine();

            // The output file should only include one line
            if (line == null) {
                return 0;
            }

            // Get number of calls and failure reason;
            String[] res = line.split(" ");
            calls = Integer.parseInt(res[0]);
            int reason = Integer.parseInt(res[1]);
            callStatus[reason]++;
        } catch (IOException e) {
            CLog.e("IOException while reading outputfile %s", resFile.getAbsolutePath());
        } finally {
            FileUtil.deleteFile(resFile);
            StreamUtil.close(br);
        }
        return calls;
    }

    /**
     * Report run metrics by creating an empty test run to stick them in
     * <p />
     * Exposed for unit testing
     */
    private void reportMetrics(String metricsName, ITestInvocationListener listener) {
        Map<String, String> metrics = new HashMap<String, String>();
        for (int i = 0; i < (keys.length - 1); i++) {
            callStatus[keys.length - 1] = Integer.parseInt(mRepeatCount) - callStatus[i];
            metrics.put(keys[i], Integer.toString(callStatus[i]));
        }
        metrics.put(keys[keys.length - 1], Integer.toString(callStatus[keys.length - 1]));

        // Create an empty testRun to report the parsed runMetrics
        CLog.d("About to report metrics to %s: %s", metricsName, metrics);
        listener.testRunStarted(metricsName, 0);
        listener.testRunEnded(0, metrics);
    }

    /**
     * Clean up output files from the last test run
     */
    private void cleanOutputFiles() throws DeviceNotAvailableException {
        CLog.d("Remove output file: %s", mOutputFile);
        mTestDevice.executeShellCommand(String.format("rm %s", mOutputFile));
    }

    @Override
    public void setDevice(ITestDevice testDevice) {
        mTestDevice = testDevice;
    }

    @Override
    public ITestDevice getDevice() {
        return mTestDevice;
    }
}
