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
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.result.BugreportCollector;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.SnapshotInputStreamSource;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.log.LogUtil.CLog;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;

import junit.framework.Assert;

/**
 * Run the OpenGl performance test. The OpenGl performance test benchmarks the performance
 * of RenderScript in Android System.
 */
public class OpenGlPerformanceTest implements IDeviceTest, IRemoteTest {

    private ITestDevice mTestDevice = null;

    // Define instrumentation test package and runner.
    private static final String TEST_PACKAGE_NAME = "com.android.perftest";
    private static final String TEST_RUNNER_NAME = ".RsPerfTestRunner";
    private static final String TEST_CLASS = "com.android.perftest.RsBenchTest";
    private static final String OUTPUT_FILE = "rsbench_result";

    // The order of item keys are matching the order they show in the output file
    private static final String[] ITEM_KEYS = {"text1", "text2", "text3",
            "GeoTestFlatColor1", "GeoTestFlatColor2", "GeoTestFlatColor3", "GeoTestSingleTexture1",
            "GeoTestSingleTexture2", "GeoTestSingleTexture3",
            "FullScreenMesh10","FullScrennMesh100", "FullScreenMeshW4",
            "GeoTestHeavyVertex1", "GeoTestHeavyVertex2", "GeoTestHeavyVertex3",
            "10xSingleTexture", "Multitexture",
            "BlendedSingleTexture", "BlendedMultiTexture",
            "GeoTestHeavyFrag1", "GeoTestHeavyFrag2",
            "GeoTestHeavyFrag3", "GeoTestHeavyFragHeavyVertex1",
            "GeoTestHeavyFragHeavyVertex2", "GeoTestHeavyFragHeavyVertex3",
            "UITestWithIcon10by10", "UITestWithIcon100by100",
            "UITestWithImageText3", "UITestWithImageText5",
            "UITestListView", "UITestLiveWallPaper"};
    private final String[] RU_KEYS = {"graphics_text", "graphics_geo_light", "graphics_mesh",
            "graphics_geo_heavy", "graphics_texture", "graphics_ui"};
    private final int[][] RU_ITEM_KEYS_MAP = {{0, 1, 2}, {3, 4, 5, 6, 7, 8}, {9, 10, 11},
            {12, 13, 14, 19, 20, 21, 22, 23, 24}, {15, 16, 17, 18}, {25, 26, 27, 28, 29, 30}};

    @Option(name="iterations",
            description="The number of iterations to run benchmark tests.")
    private int mIterations = 10;

    /**
     * Run the OpenGl benchmark tests
     * Collect results and post results to test listener.
     */
    @Override
    public void run(ITestInvocationListener standardListener)
            throws DeviceNotAvailableException {
        Assert.assertNotNull(mTestDevice);
        CLog.d("option values: mIterations(%d)", mIterations);

        IRemoteAndroidTestRunner runner = new RemoteAndroidTestRunner(
                TEST_PACKAGE_NAME, TEST_RUNNER_NAME, mTestDevice.getIDevice());
        runner.addInstrumentationArg("iterations", Integer.toString(mIterations));
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
    private void logOutputFile(ITestInvocationListener listener) throws DeviceNotAvailableException {
        // take a bug report, it is possible the system crashed
        InputStreamSource bugreport = mTestDevice.getBugreport();
        listener.testLog("bugreport.txt", LogDataType.TEXT, bugreport);
        bugreport.cancel();
        File resFile = null;
        InputStreamSource outputSource = null;
        List<String> testName = new ArrayList<String>();
        List<List<String>> testResults = new ArrayList<List<String>>();
        float[] testAverage = null;
        float[] testStd = null;

        for (int i = 0; i < mIterations; i++) {
            // In each iteration, the test result is saved in a file rsbench_result*.csv
            // e.g. for 10 iterations, results are in rsbench_result0.csv - rsbench_result9.csv
            String outputFileName = String.format("%s%d.csv", OUTPUT_FILE, i);
            CLog.d("pull result %s", outputFileName);

            try {
                resFile = mTestDevice.pullFileFromExternal(outputFileName);
                Assert.assertNotNull("no test results, test failed?", resFile);
                // Save a copy of the output file
                CLog.d("Sending %d byte file %s into the logosphere!",
                        resFile.length(), resFile);
                outputSource = new SnapshotInputStreamSource(new FileInputStream(resFile));
                listener.testLog(outputFileName, LogDataType.TEXT,
                        outputSource);

                // Parse the results file and report results to dash board
                parseOutputFile(resFile, testName, testResults, i, listener);
            } catch (IOException e) {
                CLog.e("IOException while reading outputfile %s", outputFileName);
            } finally {
                if (resFile != null) {
                    resFile.delete();
                }
                if (outputSource != null) {
                    outputSource.cancel();
                }
            }
        }

        testAverage = new float[testResults.size()];
        testStd = new float[testResults.size()];

        // After processing the output file, calculate average data and report data to dashboard
        // Find the RU-ITEM keys mapping for data posting
        for (int ruIndex = 0; ruIndex < RU_KEYS.length; ruIndex++) {
            Map<String, String> runMetrics = new HashMap<String, String>();
            int[] itemKeys = RU_ITEM_KEYS_MAP[ruIndex];
            for (int i  = 0; i < itemKeys.length; i++) {
                int itemIndex = itemKeys[i];
                float averageFps = getAverage(testResults.get(itemIndex));
                float std = getStd(testResults.get(itemIndex), averageFps);
                testAverage[itemIndex] = averageFps;
                testStd[itemIndex] =  std;
                runMetrics.put(ITEM_KEYS[itemIndex], String.valueOf(averageFps));
            }
            reportMetrics(RU_KEYS[ruIndex], listener, runMetrics);
        }

        // Log the results
        for (int i = 0; i < testName.size(); i++) {
            CLog.d("%s: %f, %f", testName.get(i), testAverage[i], testStd[i]);
        }
    }

    private float getAverage(List<String> dataArray) {
        float sum = 0;
        for (int i = 0; i < dataArray.size(); i++) {
            sum += Float.parseFloat(dataArray.get(i));
        }
        return (sum/dataArray.size());
    }

    private float getStd(List<String> dataArray, float mean) {
        float sum = 0;
        for (int i = 0; i < dataArray.size(); i++) {
            sum += Math.pow((Float.parseFloat(dataArray.get(i)) - mean), 2.0);
        }
        return ((float)Math.sqrt(sum/dataArray.size()));
    }

    // Parse one result file and save test name and fps value
    private void parseOutputFile(File dataFile, List<String> nameList,
            List<List<String>> dataArray, int iterationId, ITestInvocationListener listener) {
        try {
            BufferedReader br= new BufferedReader(new FileReader(dataFile));
            String line = null;
            int testIndex  = 0;
            while ((line = br.readLine()) != null) {
                String[]  data = line.trim().split(",");
                if (iterationId == 0) {
                    nameList.add(data[0].trim());
                    List<String>  fpsList = new ArrayList<String>();
                    fpsList.add(data[1].trim());
                    dataArray.add(testIndex, fpsList);
                } else {
                    // get the result list, update the result and add it back to the array
                    dataArray.get(testIndex).add(data[1].trim());
                }
                testIndex++;
            }
        } catch (IOException e) {
            CLog.e("IOException while reading from data stream: %s", e);
            return;
        }
    }

    /**
     * Report run metrics by creating an empty test run to stick them in
     * <p />
     * Exposed for unit testing
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
        for (int i = 0; i < mIterations; i++) {
            String outputFileName = String.format("%s%d.csv", OUTPUT_FILE, i);
            String extStore = mTestDevice.getMountPoint(IDevice.MNT_EXTERNAL_STORAGE);
            mTestDevice.executeShellCommand(String.format("rm %s/%s", extStore, outputFileName));
        }
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
