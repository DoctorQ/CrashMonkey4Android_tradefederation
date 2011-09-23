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

package com.android.performance.tests;

import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.ITestDevice.RecoveryMode;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.util.RunUtil;
import com.android.tradefed.util.brillopad.BugreportParser;
import com.android.tradefed.util.brillopad.ItemList;
import com.android.tradefed.util.brillopad.item.GenericMapItem;
import com.android.tradefed.util.brillopad.item.IItem;
import com.android.tradefed.util.brillopad.section.MemInfoParser;
import com.android.tradefed.util.brillopad.section.ProcRankParser;

import junit.framework.Assert;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Tests to gather device metrics from during and immediately after boot
 */
public class StartupMetricsTest implements IDeviceTest, IRemoteTest {
    public static final String BUGREPORT_LOG_NAME = "bugreport_startup.txt";

    @Option(name="boot-time-ms", description="Timeout in ms to wait for device to boot.")
    private static final long mBootTimeMs = 5 * 60 * 1000;

    @Option(name="boot-poll-time-ms", description="Delay in ms between polls for device to boot.")
    private static final long mBootPoolTimeMs = 500;

    ITestDevice mTestDevice = null;

    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        Assert.assertNotNull(mTestDevice);
        executeRebootTest(listener);
        fetchBugReportMetrics(listener);
    }

    /**
     * Check how long the device takes to come online and become available after
     * a reboot.
     *
     * @param listener the {@link ITestInvocationListener} of test results
     */
    void executeRebootTest(ITestInvocationListener listener) throws DeviceNotAvailableException {
        Map<String, String> runMetrics = new HashMap<String, String>();
        mTestDevice.setRecoveryMode(RecoveryMode.NONE);
        CLog.d("Reboot test start.");
        mTestDevice.nonBlockingReboot();
        long startTime = System.currentTimeMillis();
        mTestDevice.waitForDeviceOnline();
        long onlineTime = System.currentTimeMillis();
        Assert.assertTrue(waitForBootComplete(mTestDevice, mBootTimeMs, mBootPoolTimeMs));
        long availableTime = System.currentTimeMillis();

        long offlineDuration = onlineTime - startTime;
        long unavailDuration = availableTime - startTime;
        CLog.d("Reboot: %d millis until online, %d until available",
                offlineDuration, unavailDuration);
        runMetrics.put("online", Double.toString((double)offlineDuration/1000.0));
        runMetrics.put("bootcomplete", Double.toString((double)unavailDuration/1000.0));

        reportMetrics(listener, "boottime", runMetrics);
    }

    /**
     * Fetch proc rank metrics from the bugreport after reboot.
     *
     * @param listener the {@link ITestInvocationListener} of test results
     * @throws DeviceNotAvailableException
     */
    @SuppressWarnings("unchecked")
    void fetchBugReportMetrics(ITestInvocationListener listener)
            throws DeviceNotAvailableException {
        // Make sure the device is available and settled, before getting bugreport.
        mTestDevice.waitForDeviceAvailable();
        BugreportParser parser = new BugreportParser();
        ItemList bugreport = null;
        // Retrieve bugreport
        InputStreamSource bugSource = mTestDevice.getBugreport();
        try {
            listener.testLog(BUGREPORT_LOG_NAME, LogDataType.TEXT, bugSource);
            bugreport = parser.parse(bugSource);
        } catch (IOException e) {
            Assert.fail(String.format("Failed to fetch and parse bugreport for device %s: %s",
                    mTestDevice.getSerialNumber(), e));
        } finally {
            bugSource.cancel();
        }
        // Process meminfo information and post it to the dashboard
        IItem item = bugreport.getFirstItemByType(MemInfoParser.SECTION_NAME);
        if (item != null) {
            Map<String, String> memInfoMap = convertMap((GenericMapItem<String, Integer>) item);
            reportMetrics(listener, "startup-meminfo", memInfoMap);
        }

        // Process procrank information and post it to the dashboard
        item = bugreport.getFirstItemByType(ProcRankParser.SECTION_NAME);
        if (item != null) {
            Map <String, Map<String, Integer>> procRankMap =
                    (GenericMapItem<String, Map<String, Integer>>) item;
            parseProcRankMap(listener, procRankMap);
        }
    }

    /**
     * Helper method to convert Map<String, Integer> to Map<String, String>.
     *
     * @param input the {@link Map} to convert from
     * @return output the converted {@link Map}
     */
    Map<String, String> convertMap(Map<String, Integer> input) {
        Map<String, String> output = new HashMap<String, String>();
        for (Map.Entry<String, Integer> entry : input.entrySet()) {
            output.put(entry.getKey(), entry.getValue().toString());
        }
        return output;
    }

    /**
     * Report run metrics by creating an empty test run to stick them in
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
     * Aggregates the procrank data by the pss, rss, and uss values.
     *
     * @param listener the {@link ITestInvocationListener} of test results
     * @param procRankMap the {@link Map} parsed from brillopad for the procrank
     *            section
     */
    void parseProcRankMap(ITestInvocationListener listener,
            Map<String, Map<String, Integer>> procRankMap) {
        // final maps for pss, rss, and uss.
        Map<String, String> pssOutput = new HashMap<String, String>();
        Map<String, String> rssOutput = new HashMap<String, String>();
        Map<String, String> ussOutput = new HashMap<String, String>();
        // total number of processes.
        Integer numProcess = 0;
        // aggregate pss, rss, uss across all processes.
        Integer pssTotal = 0;
        Integer rssTotal = 0;
        Integer ussTotal = 0;

        for (Map.Entry<String, Map<String, Integer>> entry : procRankMap.entrySet()) {
            // Skip empty processes.
            if (entry.getKey() == null)
                continue;
            if (entry.getKey().length() == 0)
                continue;

            numProcess++;
            Map<String, Integer> valueMap = entry.getValue();
            Integer pss = valueMap.get("Pss");
            Integer rss = valueMap.get("Rss");
            Integer uss = valueMap.get("Uss");
            if (pss != null) {
                pssTotal += pss;
                pssOutput.put(entry.getKey(), pss.toString());
            }
            if (rss != null) {
                rssTotal += rss;
                rssOutput.put(entry.getKey(), rss.toString());
            }
            if (uss != null) {
                ussTotal += pss;
                ussOutput.put(entry.getKey(), uss.toString());
            }
        }
        // Add aggregation data.
        pssOutput.put("count", numProcess.toString());
        pssOutput.put("total", pssTotal.toString());
        rssOutput.put("count", numProcess.toString());
        rssOutput.put("total", rssTotal.toString());
        ussOutput.put("count", numProcess.toString());
        ussOutput.put("total", ussTotal.toString());

        // Report metrics to dashboard
        reportMetrics(listener, "startup-procrank-pss", pssOutput);
        reportMetrics(listener, "startup-procrank-rss", rssOutput);
        reportMetrics(listener, "startup-procrank-uss", ussOutput);
    }

    /**
     * Blocks until the device's boot complete flag is set.
     *
     * @param device the {@link ITestDevice}
     * @param timeOut time in msecs to wait for the flag to be set
     * @param pollDelay time in msecs between checks
     * @return true if device's boot complete flag is set within the timeout
     * @throws DeviceNotAvailableException
     */
    private boolean waitForBootComplete(ITestDevice device,long timeOut, long pollDelay)
            throws DeviceNotAvailableException {
        long startTime = System.currentTimeMillis();
        while ((System.currentTimeMillis() - startTime) < timeOut) {
            String output = device.executeShellCommand("getprop dev.bootcomplete");
            output = output.replace('#', ' ').trim();
            if (output.equals("1")) {
                return true;
            }
            RunUtil.getDefault().sleep(pollDelay);
        }
        CLog.w("Device %s did not boot after %d ms", device.getSerialNumber(), timeOut);
        return false;
    }

    @Override
    public void setDevice(ITestDevice device) {
        mTestDevice = device;
    }

    @Override
    public ITestDevice getDevice() {
        return mTestDevice;
    }
}
