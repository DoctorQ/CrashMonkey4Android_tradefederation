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

package com.android.framework.tests;

import com.android.ddmlib.testrunner.IRemoteAndroidTestRunner;
import com.android.ddmlib.testrunner.RemoteAndroidTestRunner;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.CollectingTestListener;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.TestResult;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.util.IRunUtil.IRunnableResult;
import com.android.tradefed.util.MultiMap;
import com.android.tradefed.util.RunUtil;
import com.android.tradefed.util.net.HttpHelper;
import com.android.tradefed.util.net.IHttpHelper;
import com.android.tradefed.util.net.IHttpHelper.DataSizeException;

import junit.framework.Assert;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Test that instruments a bandwidth test, gathers bandwidth metrics, and posts
 * the results to the Release Dashboard.
 */
public class BandwidthMicroBenchMarkTest implements IDeviceTest, IRemoteTest {

    ITestDevice mTestDevice = null;

    @Option(name = "test-package-name", description = "Android test package name.")
    private String mTestPackageName;

    @Option(name = "test-class-name", description = "Test class name.")
    private String mTestClassName;

    @Option(name = "test-method-name", description = "Test method name.")
    private String mTestMethodName;

    @Option(name = "test-label",
            description = "Test label to identify the test run.")
    private String mTestLabel;

    @Option(name = "bandwidth-test-server",
            description = "Test label to use when posting to dashboard.",
            importance=Option.Importance.IF_UNSET)
    private String mTestServer;

    @Option(name = "ssid",
            description = "The ssid to use for the wifi connection.")
    private String mSsid;

    @Option(name = "initial-server-poll-interval-ms",
            description = "The initial poll interval in msecs for querying the test server.")
    private int mInitialPollIntervalMs = 1 * 1000;

    @Option(name = "server-total-timeout-ms",
            description = "The total timeout in msecs for querying the test server.")
    private int mTotalTimeoutMs = 40 * 60 * 1000;

    @Option(name = "server-query-op-timeout-ms",
            description = "The timeout in msecs for a single operation to query the test server.")
    private int mQueryOpTimeoutMs = 2 * 60 * 1000;

    private static final String TEST_RUNNER = "com.android.bandwidthtest.BandwidthTestRunner";
    private static final String TEST_SERVER_QUERY = "query";
    private static final String DEVICE_ID_LABEL = "device_id";
    private static final String TIMESTAMP_LABEL = "timestamp";
    private static final String DOWNLOAD_LABEL = "download";
    private static final String PROF_LABEL = "PROF_";
    private static final String PROC_LABEL = "PROC_";
    private static final String RX_LABEL = "rx";
    private static final String TX_LABEL = "tx";
    private static final String SIZE_LABEL = "size";

    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        Assert.assertNotNull(mTestDevice);

        Assert.assertNotNull("Need a test server, specify it using --bandwidth-test-server",
                mTestServer);
        IRemoteAndroidTestRunner runner = new RemoteAndroidTestRunner(mTestPackageName,
                TEST_RUNNER, mTestDevice.getIDevice());
        runner.setMethodName(mTestClassName, mTestMethodName);
        if (mSsid != null) {
            runner.addInstrumentationArg("ssid", mSsid);
        }
        runner.addInstrumentationArg("server", mTestServer);

        CollectingTestListener collectingListener = new CollectingTestListener();
        Assert.assertTrue(
                mTestDevice.runInstrumentationTests(runner, collectingListener, listener));

        // Collect bandwidth metrics from the instrumentation test out.
        Map<String, String> bandwidthTestMetrics = new HashMap<String, String>();
        Collection<TestResult> testResults =
                collectingListener.getCurrentRunResults().getTestResults().values();
        if (testResults != null && testResults.iterator().hasNext()) {
            Map<String, String> testMetrics = testResults.iterator().next().getMetrics();
            if (testMetrics != null) {
                bandwidthTestMetrics.putAll(testMetrics);
            }
        }
        // Fetch the data from the test server.
        String deviceId = bandwidthTestMetrics.get(DEVICE_ID_LABEL);
        String timestamp = bandwidthTestMetrics.get(TIMESTAMP_LABEL);
        Assert.assertNotNull("Failed to fetch deviceId from server", deviceId);
        Assert.assertNotNull("Failed to fetch timestamp from server", timestamp);
        Map<String, String> serverData = fetchDataFromTestServer(deviceId, timestamp);

        // Parse results and calculate differences.
        if (serverData != null) {
            calculateDifferences(bandwidthTestMetrics, serverData);
        } else {
            CLog.w("Missing server data");
        }
        // Calculate additional network sanity stats - pre-framework logic network stats
        BandwidthUtils bw = new BandwidthUtils(mTestDevice);
        Map<String, String> stats = bw.calculateStats();
        bandwidthTestMetrics.putAll(stats);

        // Calculate event log network stats - post-framework logic network stats
        Map<String, String> eventLogStats = fetchEventLogStats();
        bandwidthTestMetrics.putAll(eventLogStats);

        // Post everything to the dashboard.
        reportMetrics(listener, mTestLabel, bandwidthTestMetrics);
    }

    /**
     * Fetch the bandwidth test data recorded on the test server.
     *
     * @param deviceId
     * @param timestamp
     * @return a map of the data that was recorded by the test server.
     */
    private Map<String, String> fetchDataFromTestServer(String deviceId, String timestamp) {
        IHttpHelper httphelper = new HttpHelper();
        MultiMap<String,String> params = new MultiMap<String,String> ();
        params.put("device_id", deviceId);
        params.put("timestamp", timestamp);
        String queryUrl = mTestServer;
        if (!queryUrl.endsWith("/")) {
            queryUrl += "/";
        }
        queryUrl += TEST_SERVER_QUERY;
        QueryRunnable runnable = new QueryRunnable(httphelper, queryUrl, params);
        if (RunUtil.getDefault().runEscalatingTimedRetry(mQueryOpTimeoutMs, mInitialPollIntervalMs,
                mQueryOpTimeoutMs, mTotalTimeoutMs, runnable)) {
            return runnable.getServerResponse();
        } else {
            CLog.w("Failed to query test server", runnable.getException());
        }
        return null;
    }

    private static class QueryRunnable implements IRunnableResult {
        private final IHttpHelper mHttpHelper;
        private final String mBaseUrl;
        private final MultiMap<String,String> mParams;
        private Map<String, String> mServerResponse = null;
        private Exception mException = null;

        public QueryRunnable(IHttpHelper helper, String testServerUrl,
                MultiMap<String,String> params) {
            mHttpHelper = helper;
            mBaseUrl = testServerUrl;
            mParams = params;
        }

        /**
         * Perform a single bandwidth test server query, storing the response or
         * the associated exception in case of error.
         */
        @Override
        public boolean run() {
            try {
                String serverResponse = mHttpHelper.doGet(mHttpHelper.buildUrl(mBaseUrl, mParams));
                mServerResponse = parseServerResponse(serverResponse);
                return true;
            } catch (IOException e) {
                CLog.i("IOException %s when contacting test server", e.getMessage());
                mException = e;
            } catch (DataSizeException e) {
                CLog.i("Unexpected oversized response when contacting test server");
                mException = e;
            }
            return false;
        }

        /**
         * Returns exception.
         *
         * @return the last {@link Exception} that occurred when performing
         *         run().
         */
        public Exception getException() {
            return mException;
        }

        /**
         * Returns the server response.
         *
         * @return a map of the server response.
         */
        public Map<String, String> getServerResponse() {
            return mServerResponse;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void cancel() {
            // ignore
        }
    }

    /**
     * Helper to parse test server's response into a map
     * <p>
     * Exposed for unit testing.
     *
     * @param serverResponse {@link String} for the test server http request
     * @return a map representation of the server response
     */
    public static Map<String, String> parseServerResponse(String serverResponse) {
        // No such test run was recorded.
        if (serverResponse == null || serverResponse.trim().length() == 0) {
            return null;
        }
        final String[] responseLines = serverResponse.split("\n");
        Map<String, String> results = new HashMap<String, String>();
        for (String responseLine : responseLines) {
            final String[] responsePairs = responseLine.split(" ");
            for (String responsePair : responsePairs) {
                final String[] pair = responsePair.split(":", 2);
                if (pair.length >= 2) {
                    results.put(pair[0], pair[1]);
                } else {
                    CLog.w("Invalid server response: %s", responsePair);
                }
            }
        }
        return results;
    }

    /**
     * Calculate percent differences between measured PROC, PROF, and server
     * values.
     *
     * @param deviceMetrics Map of PROC and PROF values
     * @param serverMetrics Map of server values
     */
    void calculateDifferences(Map<String, String> deviceMetrics,
            Map<String, String> serverMetrics) {
        boolean downloadTest = false;

        if (!serverMetrics.containsKey(DOWNLOAD_LABEL) || !serverMetrics.containsKey(SIZE_LABEL)) {
            CLog.d("Invalid server metrics, cannot calculate differences.");
            return;
        }
        String downloadTestString = serverMetrics.get(DOWNLOAD_LABEL);
        String serverSize = serverMetrics.get(SIZE_LABEL);
        if (downloadTestString.equalsIgnoreCase("true")) {
            downloadTest = true;

        }
        deviceMetrics.put(DOWNLOAD_LABEL, downloadTestString);
        deviceMetrics.put(DOWNLOAD_LABEL, serverSize);

        String procLabel = null;
        String profLabel = null;
        if (downloadTest) {
            procLabel = PROC_LABEL + RX_LABEL;
            profLabel = PROF_LABEL + RX_LABEL;
        } else {
            procLabel = PROC_LABEL + TX_LABEL;
            profLabel = PROF_LABEL + TX_LABEL;
        }
        if (!deviceMetrics.containsKey(procLabel)
                || !deviceMetrics.containsKey(profLabel)) {
            CLog.d("Missing device bandwidth metrics, cannot calculate differences.");
            return;
        }
        double procValue = Double.parseDouble(deviceMetrics.get(procLabel));
        double profValue = Double.parseDouble(deviceMetrics.get(profLabel));
        double serverValue = Double.parseDouble(serverSize);
        double procToProf = calculatePercentageDifference(procValue, profValue);
        double procToServer = calculatePercentageDifference(procValue, serverValue);
        double profToServer = calculatePercentageDifference(profValue, serverValue);
        deviceMetrics.put("Absolute difference for PROC and PROF", Double.toString(procToProf));
        deviceMetrics.put("Absolute difference for PROC and Server", Double.toString(procToServer));
        deviceMetrics.put("Absolute difference for PROF and Server", Double.toString(profToServer));
    }

    /**
     * Calculate the percent difference between two values.
     * <p>
     * Exposed for unit testing.
     *
     * @param x
     * @param y
     * @return the absolute difference between x and y
     */
    public static double calculatePercentageDifference(double x, double y) {
        if (x < 0 || y < 0) {
            CLog.w("Invalid values to calculate. Need non negative values.");
            return 0;
        }
        if (x == 0 && y == 0) {
            return 0;
        }
        return Math.abs((x - y) / ((x + y) / 2)) * 100;
    }

    /**
     * Report run metrics by creating an empty test run to stick them in.
     *
     * @param listener the {@link ITestInvocationListener} of test results
     * @param runName the test name
     * @param metrics the {@link Map} that contains metrics for the given test
     */
    void reportMetrics(ITestInvocationListener listener, String runName,
            Map<String, String> metrics) {
        // Create an empty testRun to report the parsed runMetrics
        CLog.d("About to report metrics: %s", metrics);
        listener.testRunStarted(runName, 0);
        listener.testRunEnded(0, metrics);
    }

    /**
     * Fetch the last stats from event log and calculate the differences.
     * @throws DeviceNotAvailableException
     */
    private Map<String, String> fetchEventLogStats() throws DeviceNotAvailableException {
        // issue a force update of stats
        Map<String, String> eventLogStats = new HashMap<String, String>();
        String res = mTestDevice.executeShellCommand("dumpsys netstats poll");
        if (!res.contains("Forced poll")) {
            CLog.w("Failed to force a poll on the device.");
        }
        // fetch events log
        String log = mTestDevice.executeShellCommand("logcat -d -b events");
        if (log != null) {
            parseForLatestStats("netstats_wifi_sample", log, eventLogStats);
            parseForLatestStats("netstats_mobile_sample", log, eventLogStats);
            return eventLogStats;
        }
        return null;
    }

    /**
     * Parse a log output for a given key and calculate the network stats.
     * @param key {@link String} to search for in the log
     * @param log obtained from adb logcat -b events
     * @param stats Map to write the stats to
     */
    private void parseForLatestStats(String key, String log, Map<String, String> stats) {
        String[] parts = log.split("\n");
        for (int i = parts.length - 1; i > 0; i--) {
            String str = parts[i];
            if (str.contains(key)) {
                int start = str.lastIndexOf("[");
                int end = str.lastIndexOf("]");
                String subStr = str.substring(start + 1, end);
                String[] statsStrArray = subStr.split(",");
                if (statsStrArray.length != 8) {
                    CLog.e("Failed to parse for %s in log.", key);
                    return;
                }
                float ifaceRb = Float.parseFloat(statsStrArray[0].trim());
                float ifaceRp = Float.parseFloat(statsStrArray[1].trim());
                float ifaceTb = Float.parseFloat(statsStrArray[2].trim());
                float ifaceTp = Float.parseFloat(statsStrArray[3].trim());
                float uidRb = Float.parseFloat(statsStrArray[4].trim());
                float uidRp = Float.parseFloat(statsStrArray[5].trim());
                float uidTb = Float.parseFloat(statsStrArray[6].trim());
                float uidTp = Float.parseFloat(statsStrArray[7].trim());
                BandwidthStats ifaceStats = new BandwidthStats(ifaceRb, ifaceRp, ifaceTb, ifaceTp);
                BandwidthStats uidStats = new BandwidthStats(uidRb, uidRp, uidTb, uidTp);
                BandwidthStats diffStats = ifaceStats.calculatePercentDifference(uidStats);
                stats.putAll(ifaceStats.formatToStringMap(key + "_IFACE_"));
                stats.putAll(uidStats.formatToStringMap(key + "_UID_"));
                stats.putAll(diffStats.formatToStringMap(key + "_%_"));
                return;
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setDevice(ITestDevice device) {
        mTestDevice = device;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ITestDevice getDevice() {
        return mTestDevice;
    }
}
