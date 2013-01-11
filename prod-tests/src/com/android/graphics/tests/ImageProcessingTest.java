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

package com.android.graphics.tests;

import com.android.ddmlib.IDevice;
import com.android.ddmlib.testrunner.IRemoteAndroidTestRunner;
import com.android.ddmlib.testrunner.RemoteAndroidTestRunner;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.BugreportCollector;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.SnapshotInputStreamSource;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.RunUtil;
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
 * Run the ImageProcessing test. The test provides benchmark for image processing
 * in Android System.
 */
public class ImageProcessingTest implements IDeviceTest, IRemoteTest {

    private ITestDevice mTestDevice = null;

    // Define instrumentation test package and runner.
    private static final String TEST_PACKAGE_NAME = "com.android.rs.image";
    private static final String TEST_RUNNER_NAME = ".ImageProcessingTestRunner";
    private static final String TEST_CLASS = "com.android.rs.image.ImageProcessingTest";
    private static final String OUTPUT_FILE = "image_processing_result.txt";
    private static final long START_TIMER = 2 * 60 * 1000; // 2 minutes

    // Define keys for data posting
    private static final String TEST_RUN_NAME = "graphics_image_processing";
    private static final String ITEM_KEY = "frame_time";
    private static final Pattern FRAME_TIME_PATTERN =
            Pattern.compile("^Average frame time: (\\d+) ms");

    /**
     * Run the ImageProcessing benchmark test, parse test results.
     */
    @Override
    public void run(ITestInvocationListener standardListener)
            throws DeviceNotAvailableException {
        Assert.assertNotNull(mTestDevice);
        // Start the test after device is fully booted and stable
        // FIXME: add option in TF to wait until device is booted and stable
        RunUtil.getDefault().sleep(START_TIMER);

        IRemoteAndroidTestRunner runner = new RemoteAndroidTestRunner(
                TEST_PACKAGE_NAME, TEST_RUNNER_NAME, mTestDevice.getIDevice());
        runner.setClassName(TEST_CLASS);
        // Add bugreport listener for failed test
        BugreportCollector bugListener = new
            BugreportCollector(standardListener, mTestDevice);
        bugListener.addPredicate(BugreportCollector.AFTER_FAILED_TESTCASES);
        bugListener.setDescriptiveName(TEST_CLASS);
        mTestDevice.runInstrumentationTests(runner, bugListener);
        logOutputFile(bugListener);
        cleanOutputFiles();
    }

    /**
     * Collect test results, report test results to test listener.
     *
     * @param test
     * @param listener
     */
    private void logOutputFile(ITestInvocationListener listener)
            throws DeviceNotAvailableException {
        // take a bug report, it is possible the system crashed
        InputStreamSource bugreport = mTestDevice.getBugreport();
        listener.testLog("bugreport.txt", LogDataType.TEXT, bugreport);
        bugreport.cancel();
        File resFile = null;
        InputStreamSource outputSource = null;
        Map<String, String> runMetrics = new HashMap<String, String>();
        BufferedReader br = null;
        try {
            resFile = mTestDevice.pullFileFromExternal(OUTPUT_FILE);
            if (resFile == null) {
                CLog.v("File %s doesn't exist or pulling the file failed.", OUTPUT_FILE);
                return;
            }
            CLog.d("output file: %s", resFile.getPath());
            // Save a copy of the output file
            CLog.d("Sending %d byte file %s into the logosphere!",
                    resFile.length(), resFile);
            outputSource = new SnapshotInputStreamSource(new FileInputStream(resFile));
            listener.testLog(OUTPUT_FILE, LogDataType.TEXT, outputSource);

            // Parse the results file and report results to dash board
            br = new BufferedReader(new FileReader(resFile));
            String line = null;
            while ((line = br.readLine()) != null) {
                Matcher match = FRAME_TIME_PATTERN.matcher(line);
                if (match.matches()) {
                    String value = match.group(1);
                    CLog.d("frame time is %s ms", value);
                    runMetrics.put(ITEM_KEY, value);
                }
            }
        } catch (IOException e) {
            CLog.e("IOException while reading outputfile %s", OUTPUT_FILE);
        } finally {
            FileUtil.deleteFile(resFile);
            StreamUtil.cancel(outputSource);
            StreamUtil.close(br);
        }
        reportMetrics(TEST_RUN_NAME, listener, runMetrics);
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
        String extStore = mTestDevice.getMountPoint(IDevice.MNT_EXTERNAL_STORAGE);
        mTestDevice.executeShellCommand(String.format("rm %s/%s", extStore, OUTPUT_FILE));
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
