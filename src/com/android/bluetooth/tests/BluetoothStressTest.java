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

package com.android.bluetooth.tests;

import com.android.ddmlib.IDevice;
import com.android.ddmlib.Log;
import com.android.ddmlib.testrunner.IRemoteAndroidTestRunner;
import com.android.ddmlib.testrunner.RemoteAndroidTestRunner;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.result.CollectingTestListener;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.SnapshotInputStreamSource;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.util.StreamUtil;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import junit.framework.Assert;
import junit.framework.TestCase;

/**
 * Runs the Bluetooth stress testcases.
 * FIXME: more details on what the testcases do
 */
public class BluetoothStressTest implements IDeviceTest, IRemoteTest {
    private static final String LOG_TAG = "BluetoothStressTest";

    ITestDevice mTestDevice = null;

    // Constants for running the tests
    private static final String TEST_CLASS_NAME = "android.bluetooth.BluetoothStressTest";
    private static final String TEST_PACKAGE_NAME = "com.android.frameworks.coretests";
    private static final String TEST_RUNNER_NAME = "android.bluetooth.BluetoothTestRunner";

    private static final Pattern ITERATION_PATTERN =
            Pattern.compile("\\S+ iteration (\\d+) of (\\d+)");

    /**
     * Matches {@code methodName(arg1=arg1, arg2=arg2) completed} where {@code args} are
     * additional information used for debugging the logs.
     */
    private static final String METHOD_COMPLETED_STR = "((?:\\w|-)+)\\((?:.*)\\) completed";
    private static final Pattern PERF_PATTERN =
            Pattern.compile(METHOD_COMPLETED_STR + " in (\\d+) ms");
    private static final Pattern METHOD_PATTERN = Pattern.compile(METHOD_COMPLETED_STR + ".*");

    private static final String OUTPUT_PATH = "BluetoothStressTestOutput.txt";

    /**
     * Stores the test cases that we should consider running.
     * <p/>
     * This currently includes "discoverable", "enable", and "scan"
     */
    private List<TestInfo> mTestCases = null;

    /**
     * A struct that contains useful info about the tests to run
     */
    static class TestInfo {
        public String mTestName = null;
        public String mTestMethod = null;
        public String mIterKey = null;
        public Integer mIterCount = null;
        public Set<String> mPerfMetrics = new HashSet<String>();

        public String getTestMetricsName() {
            if (mTestName == null) {
                return null;
            }
            return String.format("bt_%s_stress", mTestName);
        }

        @Override
        public String toString() {
            String perfMetrics = null;
            if (mPerfMetrics != null) {
                perfMetrics = mPerfMetrics.toString();
            }
            return String.format("TestInfo: method(%s) iters(%s) metrics(%s)", mTestMethod,
                    mIterCount, perfMetrics);
        }
    }

    @Option(name="discoverable-iterations",
            description="Number of iterations to run for the 'discoverable' test")
    private Integer mDiscoverableIterations = null;

    @Option(name="enable-iterations",
            description="Number of iterations to run for the 'enable' test")
    private Integer mEnableIterations = null;

    @Option(name="scan-iterations",
            description="Number of iterations to run for the 'scan' test")
    private Integer mScanIterations = null;

    @Option(name="enable-pan-iterations",
            description="Number of iterations to run for the 'enable_pan' test")
    private Integer mEnablePanIterations = null;

    private void setupTests() {
        if (mTestCases != null) {
            // assume already set up
            return;
        }
        mTestCases = new ArrayList<TestInfo>(3);

        TestInfo t = new TestInfo();
        t.mTestName = "discoverable";
        t.mTestMethod = "testDiscoverable";
        t.mIterKey = "discoverable_iterations";
        t.mIterCount = mDiscoverableIterations;
        t.mPerfMetrics.add("discoverable");
        t.mPerfMetrics.add("undiscoverable");
        mTestCases.add(t);

        t = new TestInfo();
        t.mTestName = "enable";
        t.mTestMethod = "testEnable";
        t.mIterKey = "enable_iterations";
        t.mIterCount = mEnableIterations;
        t.mPerfMetrics.add("enable");
        t.mPerfMetrics.add("disable");
        mTestCases.add(t);

        t = new TestInfo();
        t.mTestName = "scan";
        t.mTestMethod = "testScan";
        t.mIterKey = "scan_iterations";
        t.mIterCount = mScanIterations;
        t.mPerfMetrics.add("startScan");
        t.mPerfMetrics.add("stopScan");
        mTestCases.add(t);

        t = new TestInfo();
        t.mTestName = "enable_pan";
        t.mTestMethod = "testEnablePan";
        t.mIterKey = "enable_pan_iterations";
        t.mIterCount = mEnablePanIterations;
        t.mPerfMetrics.add("enablePan");
        t.mPerfMetrics.add("disablePan");
        mTestCases.add(t);
    }

    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        Assert.assertNotNull(mTestDevice);
        setupTests();

        IRemoteAndroidTestRunner runner = new RemoteAndroidTestRunner(TEST_PACKAGE_NAME,
                TEST_RUNNER_NAME, mTestDevice.getIDevice());
        runner.setClassName(TEST_CLASS_NAME);

        for (TestInfo test : mTestCases) {
            String testName = test.mTestName;
            TestInfo t = test;
            CollectingTestListener auxListener = new CollectingTestListener();

            if (t.mIterCount != null && t.mIterCount <= 0) {
                Log.e(LOG_TAG, String.format("Cancelled '%s' test case with iter count %s",
                        testName, t.mIterCount));
                continue;
            }

            // Run the test
            cleanOutputFile();
            if (t.mIterKey != null && t.mIterCount != null) {
                runner.addInstrumentationArg(t.mIterKey, t.mIterCount.toString());
            }
            runner.setMethodName(TEST_CLASS_NAME, t.mTestMethod);
            mTestDevice.runInstrumentationTests(runner, listener, auxListener);
            if (t.mIterKey != null && t.mIterCount != null) {
                runner.removeInstrumentationArg(t.mIterKey);
            }

            // Log the output file
            logOutputFile(t, listener);
            cleanOutputFile();

            // Grab a bugreport if warranted
            if (auxListener.hasFailedTests()) {
                Log.e(LOG_TAG, String.format("Grabbing bugreport after test '%s' finished with " +
                        "%d failures and %d errors.", testName, auxListener.getNumFailedTests(),
                        auxListener.getNumErrorTests()));
                InputStreamSource bugreport = mTestDevice.getBugreport();
                listener.testLog(String.format("bugreport-%s.txt", testName), LogDataType.TEXT,
                        bugreport);
                bugreport.cancel();
            }
        }
    }

    /**
     * Clean up the tmp output file from previous test runs
     */
    private void cleanOutputFile() throws DeviceNotAvailableException {
        String extStore = mTestDevice.getMountPoint(IDevice.MNT_EXTERNAL_STORAGE);
        mTestDevice.executeShellCommand(String.format("rm %s/%s", extStore, OUTPUT_PATH));
    }

    /**
     * Pull the output file from the device, add it to the logs, and also parse out the relevant
     * test metrics and report them.
     */
    private void logOutputFile(TestInfo testInfo, ITestInvocationListener listener)
            throws DeviceNotAvailableException {
        File outputFile = null;
        InputStreamSource outputSource = null;
        try {
            outputFile = mTestDevice.pullFileFromExternal(OUTPUT_PATH);

            if (outputFile != null) {
                Log.d(LOG_TAG, String.format("Sending %d byte file %s into the logosphere!",
                        outputFile.length(), outputFile));
                outputSource = new SnapshotInputStreamSource(new FileInputStream(outputFile));
                listener.testLog(String.format("output-%s.txt", testInfo.mTestName),
                        LogDataType.TEXT, outputSource);
                parseOutputFile(testInfo, new FileInputStream(outputFile), listener);
            }
        } catch (IOException e) {
            Log.e(LOG_TAG, String.format("Got an IO Exception: %s", e));
        } finally {
            if (outputFile != null) {
                outputFile.delete();
            }
            if (outputSource != null) {
                outputSource.cancel();
            }
        }
    }

    /**
     * Parse the relevant metrics from the Instrumentation test output file
     */
    private void parseOutputFile(TestInfo testInfo, InputStream dataStream,
            ITestInvocationListener listener) {
        // Read output file contents into memory
        String contents;
        try {
            dataStream = new BufferedInputStream(dataStream);
            contents = StreamUtil.getStringFromStream(dataStream);
        } catch (IOException e) {
            Log.e(LOG_TAG, String.format("Got IOException: %s", e));
            return;
        }

        List<String> lines = Arrays.asList(contents.split("\n"));
        ListIterator<String> lineIter = lines.listIterator();
        String line;
        Integer iterCount = null;
        Map<String, List<Integer>> perfData = new HashMap<String, List<Integer>>();
        Map<String, Integer> iterData = new HashMap<String, Integer>();

        // Iterate through each line of output
        while (lineIter.hasNext()) {
            line = lineIter.next();

            Matcher m = ITERATION_PATTERN.matcher(line);
            if (m.matches()) {
                iterCount = Integer.parseInt(m.group(1));
                continue;
            }

            if (iterCount == null) {
                continue;
            }

            m = PERF_PATTERN.matcher(line);
            if (m.matches()) {
                String method = m.group(1);
                int time = Integer.parseInt(m.group(2));
                if (!perfData.containsKey(method)) {
                    perfData.put(method, new LinkedList<Integer>());
                }
                perfData.get(method).add(time);
            }

            m = METHOD_PATTERN.matcher(line);
            if (m.matches()) {
                String method = m.group(1);
                if (!iterData.containsKey(method)) {
                    iterData.put(method, 1);
                } else {
                    iterData.put(method, iterData.get(method).intValue() + 1);
                }
            }
        }

        // Coalesce the parsed values into metrics that we can report
        if (iterCount == null) {
            iterCount = 0;
        }

        Map<String, String> runMetrics = new HashMap<String, String>();
        for (String metric : testInfo.mPerfMetrics) {
            if (perfData.containsKey(metric)) {
                List<Integer> values = perfData.get(metric);
                String key = String.format("performance_%s_mean", metric);
                runMetrics.put(key, Float.toString(mean(values)));
            }

            if (iterData.containsKey(metric)) {
                Integer iters = iterData.get(metric);
                iterCount = min(iterCount, iters);
            } else {
                iterCount = 0;
            }
        }
        runMetrics.put("iterations", iterCount.toString());

        // And finally, report the coalesced metrics
        reportMetrics(listener, testInfo, runMetrics);
    }

    private static Integer min(Integer x, Integer y) {
        if (x == null || x.compareTo(y) > 0) {
            return y;
        } else {
            return x;
        }
    }

    private static float mean(List<Integer> values) {
        int sum = 0;
        for (Integer n : values) {
            sum += n;
        }
        return (float)sum / values.size();
    }

    /**
     * Report run metrics by creating an empty test run to stick them in
     * <p />
     * Exposed for unit testing
     */
    void reportMetrics(ITestInvocationListener listener, TestInfo test,
            Map<String, String> metrics) {
        // Create an empty testRun to report the parsed runMetrics
        Log.d(LOG_TAG, String.format("About to report metrics to %s: %s", test.getTestMetricsName(),
                metrics));
        listener.testRunStarted(test.getTestMetricsName(), 0);
        listener.testRunEnded(0, metrics);
    }


    @Override
    public void setDevice(ITestDevice device) {
        mTestDevice = device;
    }

    @Override
    public ITestDevice getDevice() {
        return mTestDevice;
    }

    /**
     * A meta-test to ensure that bits of the BluetoothStressTest are working properly
     */
    public static class MetaTest extends TestCase {
        private BluetoothStressTest mTestInstance = null;

        private TestInfo mScanInfo = null;

        private TestInfo mReportedTestInfo = null;
        private Map<String, String> mReportedMetrics = null;

        private static String join(String... pieces) {
            StringBuilder sb = new StringBuilder();
            for (String piece : pieces) {
                sb.append(piece);
                sb.append("\n");
            }
            return sb.toString();
        }

        @Override
        public void setUp() throws Exception {
            mTestInstance = new BluetoothStressTest() {
                @Override
                void reportMetrics(ITestInvocationListener l, TestInfo test,
                        Map<String, String> metrics) {
                    mReportedTestInfo = test;
                    mReportedMetrics = metrics;
                }
            };
            mScanInfo = new TestInfo();
            mScanInfo.mTestName = "scan";
            mScanInfo.mTestMethod = "testScan";
            mScanInfo.mIterCount = 1;
            mScanInfo.mPerfMetrics.add("startScan");
            mScanInfo.mPerfMetrics.add("stopScan");
        }

        /**
         * Make sure that parsing works in the expected case
         */
        public void testParse() throws Exception {
            String output = join(
                    "enable() completed in 5759 ms",
                    "scan iteration 1 of 3",
                    "startScan() completed in 102 ms",
                    "stopScan() completed in 104 ms",
                    "scan iteration 2 of 3",
                    "startScan() completed in 103 ms",
                    "stopScan() completed in 106 ms",
                    "scan iteration 3 of 3",
                    "startScan() completed in 107 ms",
                    "stopScan() completed in 103 ms",
                    "disable() completed in 3763 ms");

            InputStream iStream = new ByteArrayInputStream(output.getBytes());
            mTestInstance.parseOutputFile(mScanInfo, iStream, null);
            assertEquals(mScanInfo, mReportedTestInfo);
            assertNotNull(mReportedMetrics);
            assertEquals(3, mReportedMetrics.size());
            assertEquals("3", mReportedMetrics.get("iterations"));
            assertEquals("104.333",
                    mReportedMetrics.get("performance_stopScan_mean").substring(0, 7));
            assertEquals("104.0", mReportedMetrics.get("performance_startScan_mean"));
        }

        /**
         * Check parsing when the output is missing entire iterations
         */
        public void testParse_short() throws Exception {
            String output = join(
                    "enable() completed in 5759 ms",
                    "scan iteration 3 of 3",  // only one iteration reported
                    "startScan() completed in 107 ms",
                    "stopScan() completed in 103 ms",
                    "disable() completed in 3763 ms");

            InputStream iStream = new ByteArrayInputStream(output.getBytes());
            mTestInstance.parseOutputFile(mScanInfo, iStream, null);
            assertEquals(mScanInfo, mReportedTestInfo);
            assertNotNull(mReportedMetrics);
            assertEquals(3, mReportedMetrics.size());
            // Parser should realize that there was only 1 iteration reported
            assertEquals("1", mReportedMetrics.get("iterations"));
            assertEquals("103.0", mReportedMetrics.get("performance_stopScan_mean"));
            assertEquals("107.0", mReportedMetrics.get("performance_startScan_mean"));
        }

        /**
         * Check parsing when the output is missing optional datums
         */
        public void testParse_missingDatums() throws Exception {
            // stopScan is missing datums but completes successfully
            String output = join(
                    "enable() completed in 5759 ms",
                    "scan iteration 1 of 3",
                    "startScan() completed in 102 ms",
                    "stopScan() completed",
                    "scan iteration 2 of 3",
                    "startScan() completed in 103 ms",
                    "stopScan() completed",
                    "scan iteration 3 of 3",
                    "startScan() completed in 107 ms",
                    "stopScan() completed",
                    "disable() completed in 3763 ms");

            InputStream iStream = new ByteArrayInputStream(output.getBytes());
            mTestInstance.parseOutputFile(mScanInfo, iStream, null);
            assertEquals(mScanInfo, mReportedTestInfo);
            assertNotNull(mReportedMetrics);
            assertEquals(2, mReportedMetrics.size());
            // Parser should realize that one of the optional datums is missing
            assertEquals("3", mReportedMetrics.get("iterations"));
            assertEquals("104.0", mReportedMetrics.get("performance_startScan_mean"));
        }

        /**
         * Check parsing when the output is missing mandatory methods
         */
        public void testParse_missingMethods() throws Exception {
            String output = join(
                    "enable() completed in 5759 ms",
                    "scan iteration 1 of 3",
                    "startScan() completed in 102 ms",
                    //"stopScan() completed in 104 ms",
                    "scan iteration 2 of 3",
                    "startScan() completed in 103 ms",
                    //"stopScan() completed in 106 ms",
                    "scan iteration 3 of 3",
                    "startScan() completed in 107 ms",
                    //"stopScan() completed in 103 ms",
                    "disable() completed in 3763 ms");

            InputStream iStream = new ByteArrayInputStream(output.getBytes());
            mTestInstance.parseOutputFile(mScanInfo, iStream, null);
            assertEquals(mScanInfo, mReportedTestInfo);
            assertNotNull(mReportedMetrics);
            assertEquals(2, mReportedMetrics.size());
            // Parser should realize that one of the mandatory methods is missing
            assertEquals("0", mReportedMetrics.get("iterations"));
            assertEquals("104.0", mReportedMetrics.get("performance_startScan_mean"));
        }

        /**
         * Make sure that parsing works with additional information added.
         */
        public void testParse_extras() throws Exception {
            String output = join(
                    "enable() completed in 5759 ms",
                    "scan iteration 1 of 3",
                    "startScan(profile=1, device=12:34:45:78:9A:BC) completed in 102 ms",
                    "stopScan(profile=1, device=12:34:45:78:9A:BC) completed in 104 ms",
                    "scan iteration 2 of 3",
                    "startScan(profile=1, device=12:34:45:78:9A:BC) completed in 103 ms",
                    "stopScan(profile=1, device=12:34:45:78:9A:BC) completed in 106 ms",
                    "scan iteration 3 of 3",
                    "startScan(profile=1, device=12:34:45:78:9A:BC) completed in 107 ms",
                    "stopScan(profile=1, device=12:34:45:78:9A:BC) completed in 103 ms",
                    "disable() completed in 3763 ms");

            InputStream iStream = new ByteArrayInputStream(output.getBytes());
            mTestInstance.parseOutputFile(mScanInfo, iStream, null);
            assertEquals(mScanInfo, mReportedTestInfo);
            assertNotNull(mReportedMetrics);
            assertEquals(3, mReportedMetrics.size());
            assertEquals("3", mReportedMetrics.get("iterations"));
            assertEquals("104.333",
                    mReportedMetrics.get("performance_stopScan_mean").substring(0, 7));
            assertEquals("104.0", mReportedMetrics.get("performance_startScan_mean"));
        }

        /**
         * Make sure that parsing ignores anything before the first iterations
         */
        public void testParse_ignoreSetup() throws Exception {
            String output = join(
                    "enable() completed in 5759 ms",
                    "stopScan() completed in 10000 ms", // Should not be counted
                    "scan iteration 1 of 3",
                    "startScan() completed in 102 ms",
                    "stopScan() completed in 104 ms",
                    "scan iteration 2 of 3",
                    "startScan() completed in 103 ms",
                    "stopScan() completed in 106 ms",
                    "scan iteration 3 of 3",
                    "startScan() completed in 107 ms",
                    "stopScan() completed in 103 ms",
                    "disable() completed in 3763 ms");

            InputStream iStream = new ByteArrayInputStream(output.getBytes());
            mTestInstance.parseOutputFile(mScanInfo, iStream, null);
            assertEquals(mScanInfo, mReportedTestInfo);
            assertNotNull(mReportedMetrics);
            assertEquals(3, mReportedMetrics.size());
            assertEquals("3", mReportedMetrics.get("iterations"));
            assertEquals("104.333",
                    mReportedMetrics.get("performance_stopScan_mean").substring(0, 7));
            assertEquals("104.0", mReportedMetrics.get("performance_startScan_mean"));
        }
    }
}

