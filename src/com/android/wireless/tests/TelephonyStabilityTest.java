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
import com.android.tradefed.result.SnapshotInputStreamSource;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.util.RegexTrie;
import com.android.tradefed.log.LogUtil.CLog;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import junit.framework.Assert;

/**
 * Run telephony stability test. The test stresses the stability of telephony by
 * putting device into sleep mode and wake up, voice connection and data connection
 * are verified after device wakeup.
 */
public class TelephonyStabilityTest implements IRemoteTest, IDeviceTest {
    private static final String TEST_NAME = "TelephonyTest";
    private static final String OUTPUT_FILE = "/data/data/com.android.phone/files/phoneResults.txt";
    // Define report RU
    private static final String METRICS_NAME = "telephony_stability";
    // Define instrumentation test package and runner.
    private static final String TEST_PACKAGE_NAME = "com.android.phonetests";
    private static final String TEST_RUNNER_NAME = ".PhoneInstrumentationStressTestRunner";
    private static final String TEST_CLASS_NAME =
            "com.android.phonetests.stress.telephony.TelephonyStress";
    public static final String TEST_METHOD = "testTelephonyStability";
    private static final Pattern ITERATION_PATTERN =
            Pattern.compile("^iteration (\\d+) out of (\\d+)");
    private static final String VOICE_REGISTRATION_KEY = "voice_registration";
    private static final String VOICE_CONNECTION_KEY = "voice_call";
    private static final String DATA_REGISTRATION_KEY = "data_registration";
    private static final String DATA_CONNECTION_KEY = "data_connection";

    private ITestDevice mTestDevice = null;
    private RegexTrie<String> mPatternMap = null;
    Map<String, String> mRunMetrics = new HashMap<String, String>();
    private int mResIndex = 0;

    @Option(name="call-duration",
            description="The time of a call to be held in the test (in seconds)")
    private String mCallDuration = "60";

    @Option(name="phone-number",
            description="The phone number used for outgoing call test")
    private String mPhoneNumber = null;

    @Option(name="iteration",
            description="The number of calls to make during the test")
    private int mIteration = 100;

    @Option(name="idletime",
            description="The time to allow device staty in suspend mode (in seconds)")
    private int mIdleTime = 120;

    @Option(name="screen-time-out",
            description="Set screen timer (in minutes)")
    private int mScreenTimer = 30;

    /**
     * Configure screen timeout property
     * @throws DeviceNotAvailableException
     */
    private void configDevice() throws DeviceNotAvailableException {
        int timeOut = mScreenTimer * 60 * 1000;
        String command = ("sqlite3 /data/data/com.android.providers.settings/databases/settings.db "
                + "\"UPDATE system SET value=\'" + timeOut + "\' WHERE name=\'screen_off_timeout\';\"");
        CLog.d("Command to set screen timeout value to %d minutes: %s", mScreenTimer, command);
        mTestDevice.executeShellCommand(command);
        // Set device screen_off_timeout as svc power can be set to false in the Wi-Fi test
        mTestDevice.executeShellCommand("svc power stayon false");

        // reboot to allow the setting to take effect, post setup will be taken care by the reboot
        mTestDevice.reboot();
    }

    private void setupTest() {
        mPatternMap = new RegexTrie<String>();
        mPatternMap.put(VOICE_REGISTRATION_KEY, "^Voice registration: (\\d+)");
        mPatternMap.put(VOICE_CONNECTION_KEY, "^Voice connection: (\\d+)");
        mPatternMap.put(DATA_REGISTRATION_KEY, "^Data registration: (\\d+)");
        mPatternMap.put(DATA_CONNECTION_KEY, "^Data connection: (\\d+)");
        String value = "0";
        mRunMetrics.put(VOICE_REGISTRATION_KEY, value);
        mRunMetrics.put(VOICE_CONNECTION_KEY, value);
        mRunMetrics.put(DATA_REGISTRATION_KEY, value);
        mRunMetrics.put(DATA_CONNECTION_KEY, value);
    }

    /**
     * Run the telephony stability test  and collect results
     */
    @Override
    public void run(ITestInvocationListener listener)
            throws DeviceNotAvailableException {
        CLog.d("input options: mCallDuration(%s), mPhoneNumber(%s), mIteration(%d), "
                + "mIdleTime(%d), mScreenTimer(%d)", mCallDuration, mPhoneNumber, mIteration,
                mIdleTime, mScreenTimer);

        Assert.assertNotNull(mTestDevice);
        Assert.assertNotNull(mPhoneNumber);
        configDevice();
        setupTest();

        IRemoteAndroidTestRunner runner = new RemoteAndroidTestRunner(TEST_PACKAGE_NAME,
                TEST_RUNNER_NAME, mTestDevice.getIDevice());
        runner.setClassName(TEST_CLASS_NAME);
        runner.setMethodName(TEST_CLASS_NAME, TEST_METHOD);

        runner.addInstrumentationArg("callduration", mCallDuration);
        runner.addInstrumentationArg("phonenumber", mPhoneNumber);
        runner.addInstrumentationArg("idletime", Integer.toString(mIdleTime));

        // Add bugreport listener for failed test
        BugreportCollector bugListener = new
            BugreportCollector(listener, mTestDevice);
        bugListener.addPredicate(BugreportCollector.AFTER_FAILED_TESTCASES);
        bugListener.setDescriptiveName(TEST_NAME);

        int remainingIteration = mIteration;
        while (remainingIteration > 0) {
            runner.addInstrumentationArg("iteration", String.valueOf(remainingIteration));
            mTestDevice.runInstrumentationTests(runner, bugListener);
            int testRun = logOutputFile(bugListener);
            remainingIteration -= testRun;
            CLog.d("remainingIteration: %d", remainingIteration);
            cleanOutputFiles();
        }
        reportMetrics(bugListener);
    }

    /**
     * Collect results from the previous run
     * @param listener
     */
    private int logOutputFile(ITestInvocationListener listener) throws DeviceNotAvailableException {
        File resFile = null;
        InputStreamSource outputSource = null;
        resFile = mTestDevice.pullFile(OUTPUT_FILE);
        int testRun = 0;
        try {
            Assert.assertNotNull("no output file", resFile);
            // Save a copy of the output file
            CLog.d("Sending %d byte file %s into the logosphere!",
                   resFile.length(), resFile);
            outputSource = new SnapshotInputStreamSource(new FileInputStream(resFile));
            listener.testLog(String.format("result_%d", mResIndex++), LogDataType.TEXT,
                             outputSource);
            BufferedReader br= new BufferedReader(new FileReader(resFile));
            String line = null;
            while ((line = br.readLine()) != null) {
                Matcher m = ITERATION_PATTERN.matcher(line);
                if (m.matches()) {
                    testRun = Integer.parseInt(m.group(1));
                    CLog.d("test run: %d", testRun);
                } else {
                    List<List<String>> capture = new ArrayList<List<String>>(1);
                    String key = mPatternMap.retrieve(capture, line);
                    if (key != null) {
                        // retrive from the metrics, add the new value and put it back
                        int value = Integer.parseInt(mRunMetrics.get(key));
                        value += Integer.parseInt(capture.get(0).get(0));
                        mRunMetrics.put(key, Integer.toString(value));
                    }
                }
            }
        } catch (IOException e) {
            CLog.e("IOException while reading outputfile %s", resFile.getAbsolutePath());
        } finally {
            if (resFile != null) {
                resFile.delete();
            }
            if (outputSource != null) {
                outputSource.cancel();
            }
        }
        return (testRun + 1);
    }

    /**
     * Report run metrics by creating an empty test run to stick them in
     * <p />
     * Exposed for unit testing
     */
    private void reportMetrics(ITestInvocationListener listener) {
        // Create an empty testRun to report the parsed runMetrics
        CLog.d("About to report metrics to %s: %s", METRICS_NAME, mRunMetrics);
        listener.testRunStarted(METRICS_NAME, 0);
        listener.testRunEnded(0, mRunMetrics);
    }

    /**
     * Clean up output files from the last test run
     */
    private void cleanOutputFiles() throws DeviceNotAvailableException {
        CLog.d("Remove output file: %s", OUTPUT_FILE);
        mTestDevice.executeShellCommand(String.format("rm %s", OUTPUT_FILE));
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
