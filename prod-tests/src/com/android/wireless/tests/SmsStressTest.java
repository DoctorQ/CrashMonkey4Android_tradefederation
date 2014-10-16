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

import com.android.ddmlib.IDevice;
import com.android.ddmlib.testrunner.IRemoteAndroidTestRunner;
import com.android.ddmlib.testrunner.RemoteAndroidTestRunner;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.SnapshotInputStreamSource;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.StreamUtil;

import junit.framework.Assert;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Run the Sms stress test. This test stresses sms message sending and receiving
 */
public class SmsStressTest implements IRemoteTest, IDeviceTest {
    private ITestDevice mTestDevice = null;

    // Define instrumentation test package and runner.
    private static final String TEST_PACKAGE_NAME = "com.android.mms.tests";
    private static final String TEST_RUNNER_NAME = "com.android.mms.SmsTestRunner";
    private static final String TEST_CLASS_NAME = "com.android.mms.ui.SmsStressTest";

    private static final String ITEM_KEY = "single_thread";
    private static final String METRICS_NAME = "sms_stress";
    private static final Pattern MESSAGE_PATTERN =
            Pattern.compile("^send message (\\d+) out of (\\d+)");
    private static final String INSERT_COMMAND =
            "sqlite3 /data/data/com.android.providers.settings/databases/settings.db "
            + "\"INSERT INTO global (name, value) values (\'%s\',\'%s\');\"";
    private String mOutputFile = "result.txt";

    @Option(name="recipient",
            description="The recipient of sms messages")
    private String mRecipient = null;

    @Option(name="messages",
            description="The total number of messages to send")
    private int mNumMessages = 100;

    @Option(name="messagefile",
            description="The file to load sending message")
    private String mMessageFile = null;

    @Option(name="recipientfile",
            description="The file to load recipients")
    private String mRecipientFile = null;

    @Option(name="receivetimer",
            description="The timer before verifying messages receiption when sending sms"
            + "to the test device itself (s)")
    private int mReceiveTimer = 300;

    @Option(name="sendinterval",
            description="The time interval between two consecutive sms.")
    private int mSendInterval = 10;

    @Override
    public void setDevice(ITestDevice testDevice) {
        mTestDevice = testDevice;
    }

    @Override
    public ITestDevice getDevice() {
        return mTestDevice;
    }

    /**
     * Configure device with special settings
     */
    private void setupDevice() throws DeviceNotAvailableException {
        String command = String.format(
                INSERT_COMMAND, "sms_outgoing_check_max_count", "20000");
        CLog.d("Command to set sms_outgoing_check_max_count: %s", command);
        mTestDevice.executeShellCommand(command);

        // reboot to allow the setting to take effect
        // post setup will be taken care by the reboot
        mTestDevice.reboot();
    }

    /**
     * Run sms stress test and parse test results
     */
    @Override
    public void run(ITestInvocationListener standardListener)
            throws DeviceNotAvailableException {
        Assert.assertNotNull(mTestDevice);
        setupDevice();
        RadioHelper mRadioHelper = new RadioHelper(mTestDevice);
        // Capture a bugreport if activation or data setup failed
        if (!mRadioHelper.radioActivation() || !mRadioHelper.waitForDataSetup()) {
            mRadioHelper.getBugreport(standardListener);
            return;
        }

        IRemoteAndroidTestRunner runner = new RemoteAndroidTestRunner(
                TEST_PACKAGE_NAME, TEST_RUNNER_NAME, mTestDevice.getIDevice());
        runner.setClassName(TEST_CLASS_NAME);
        if (mRecipient != null) {
            runner.addInstrumentationArg("recipient", mRecipient);
        }
        if (mMessageFile != null) {
            runner.addInstrumentationArg("messagefile", mMessageFile);
        }
        if (mRecipientFile != null) {
            runner.addInstrumentationArg("messagefile", mMessageFile);
        }
        runner.addInstrumentationArg("messages", Integer.toString(mNumMessages));
        runner.addInstrumentationArg(
                "receivetimer", Integer.toString(mReceiveTimer));
        runner.addInstrumentationArg(
                "sendinterval", Integer.toString(mSendInterval));

        mTestDevice.runInstrumentationTests(runner, standardListener);
        logOutputFile(standardListener);
        cleanOutputFiles();
    }

    /**
     * Collect test results and report test results.
     *
     * @param listener
     */
    private void logOutputFile(ITestInvocationListener listener)
        throws DeviceNotAvailableException {
        // Capture a bugreport right after the test
        InputStreamSource bugreport = mTestDevice.getBugreport();
        listener.testLog("bugreport", LogDataType.TEXT, bugreport);
        bugreport.cancel();

        InputStreamSource outputSource = null;
        Map<String, String> runMetrics = new HashMap<String, String>();
        File resFile = null;
        BufferedReader br = null;
        try {
            resFile = mTestDevice.pullFileFromExternal(mOutputFile);
            if (resFile == null) {
              return;
            }
            // Save a copy of the output file
            CLog.d("Sending %d byte file %s into the logosphere!",
                    resFile.length(), resFile);
            outputSource = new SnapshotInputStreamSource(new FileInputStream(resFile));
            listener.testLog(mOutputFile, LogDataType.TEXT, outputSource);

            // Parse the results file and post results to test listener
            br = new BufferedReader(new FileReader(resFile));
            String line = null;
            while ((line = br.readLine()) != null) {
                Matcher match = MESSAGE_PATTERN.matcher(line);
                if (match.matches()) {
                    String value = match.group(1);
                    CLog.d("iteration: %s", value);
                    runMetrics.put(ITEM_KEY, value);
                }
            }
        } catch (IOException e) {
            CLog.e("IOException while reading from data stream: %s", e);
        } finally {
            FileUtil.deleteFile(resFile);
            StreamUtil.cancel(outputSource);
            StreamUtil.close(br);
        }
        reportMetrics(METRICS_NAME, listener, runMetrics);
    }

    /**
     * Report run metrics by creating an empty test run to stick them in
     */
    private void reportMetrics(String metricsName, ITestInvocationListener listener,
            Map<String, String> metrics) {
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
        String extStore = mTestDevice.getMountPoint(IDevice.MNT_EXTERNAL_STORAGE);
        mTestDevice.executeShellCommand(String.format("rm %s/%s", extStore, mOutputFile));
    }
}
