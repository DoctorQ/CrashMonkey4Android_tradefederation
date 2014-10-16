/*
 * Copyright (C) 2012 The Android Open Source Project
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
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.CollectingTestListener;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.TestResult;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;
import com.google.common.collect.ImmutableMap;

import junit.framework.Assert;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Test that measures the average latency of foreground and background
 * operations in various scenarios.
 */
public class FrameworkPerfTest implements IRemoteTest, IDeviceTest {

    private static final String TEST_PACKAGE_NAME = "com.android.frameworkperf";
    private static final String TEST_RUNNER_NAME = "android.test.InstrumentationTestRunner";
    private static final Pattern METRICS_PATTERN =
            Pattern.compile("(\\d+\\.\\d+),(\\d+),(\\d+),(\\d+\\.\\d+),(\\d+),(\\d+)");
    private static final int PERF_TIMEOUT = 30 * 60 * 1000; //30 minutes timeout
    private static final int PRE_TEST_SLEEP_MS = 30 *1000; //30s sleep prior to test start

    private static final String LAYOUT = "framework_perf_layout";
    private static final String SCHEDULING = "framework_perf_scheduling";
    private static final String METHOD = "framework_perf_method";
    private static final String GC = "framework_perf_gc";
    private static final String IPCFG = "framework_perf_ipcfg";
    private static final String XML = "framework_perf_xml";
    private static final String BITMAP = "framework_perf_bitmap";
    private static final String FILE = "framework_perf_file";
    private static final String OTHER = "framework_perf_other";
    private static final ImmutableMap<String, String> TEST_TAG_MAP =
            new ImmutableMap.Builder<String, String>()
            .put("LayoutInflaterButtonFg", LAYOUT)
            .put("LayoutInflaterFg", LAYOUT)
            .put("LayoutInflaterImageButtonFg", LAYOUT)
            .put("LayoutInflaterLargeFg", LAYOUT)
            .put("LayoutInflaterViewFg", LAYOUT)
            .put("SchedFgSchedBg", SCHEDULING)
            .put("MethodCallFgCPUBg", METHOD)
            .put("MethodCallFgCreateFileBg", METHOD)
            .put("MethodCallFgCreateWriteFileBg", METHOD)
            .put("MethodCallFgCreateWriteSyncFileBg", METHOD)
            .put("MethodCallFgGcBg", METHOD)
            .put("MethodCallFgReadFileBg", METHOD)
            .put("MethodCallFgSchedBg", METHOD)
            .put("MethodCallFgWriteFileBg", METHOD)
            .put("MethodCallFg", METHOD)
            .put("ObjectGcFg", GC)
            .put("FinalizingGcFg", GC)
            .put("GcFg", GC)
            .put("PaintGcFg", GC)
            .put("IpcFgCPUBg", IPCFG)
            .put("IpcFgCreateFileBg", IPCFG)
            .put("IpcFgCreateWriteFileBg", IPCFG)
            .put("IpcFgCreateWriteSyncFileBg", IPCFG)
            .put("IpcFgGcBg", IPCFG)
            .put("IpcFgReadFileBg", IPCFG)
            .put("IpcFgSchedBg", IPCFG)
            .put("IpcFgWriteFileBg", IPCFG)
            .put("IpcFg", IPCFG)
            .put("OpenXmlResFg", XML)
            .put("ParseLargeXmlResFg", XML)
            .put("ParseXmlResFg", XML)
            .put("ReadXmlAttrsFg", XML)
            .put("CreateBitmapFg", BITMAP)
            .put("CreateRecycleBitmapFg", BITMAP)
            .put("LoadLargeBitmapFg", BITMAP)
            .put("LoadLargeScaledBitmapFg", BITMAP)
            .put("LoadRecycleLargeBitmapFg", BITMAP)
            .put("LoadRecycleSmallBitmapFg", BITMAP)
            .put("LoadSmallBitmapFg", BITMAP)
            .put("LoadSmallScaledBitmapFg", BITMAP)
            .put("CreateFileFg", FILE)
            .put("CreateWriteFileFg", FILE)
            .put("CreateWriteSyncFileFg", FILE)
            .put("ReadFileFgCreateWriteFileBg", FILE)
            .put("ReadFileFgCreateWriteSyncFileBg", FILE)
            .put("ReadFileFgReadFileBg", FILE)
            .put("ReadFileFgWriteFileBg", FILE)
            .put("ReadFileFg", FILE)
            .put("WriteFileFgCreateWriteFileBg", FILE)
            .put("WriteFileFgCreateWriteSyncFileBg", FILE)
            .put("WriteFileFgReadFileBg", FILE)
            .put("WriteFileFgWriteFileBg", FILE)
            .put("WriteFileFg", FILE)
            .build();

    private ITestDevice mTestDevice = null;

    /**
     * {@inheritDoc}
     */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        Assert.assertNotNull(mTestDevice);

        getDevice().reboot();
        getRunUtil().sleep(PRE_TEST_SLEEP_MS);
        IRemoteAndroidTestRunner runner = new RemoteAndroidTestRunner(TEST_PACKAGE_NAME,
                TEST_RUNNER_NAME, mTestDevice.getIDevice());
        runner.setMaxtimeToOutputResponse(PERF_TIMEOUT);

        CollectingTestListener collectingListener = new CollectingTestListener();
        Assert.assertTrue(mTestDevice.runInstrumentationTests(runner, collectingListener));

        Collection<TestResult> testResultsCollection =
                collectingListener.getCurrentRunResults().getTestResults().values();

        List<TestResult> testResults =
                new ArrayList<TestResult>(testResultsCollection);

        if (!testResults.isEmpty()) {
            Map<String, String> testMetrics = testResults.get(0).getMetrics();
            if (testMetrics != null) {
                reportMetrics(listener, testMetrics);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ITestDevice getDevice() {
        return mTestDevice;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setDevice(ITestDevice device) {
        mTestDevice = device;
    }

    /**
     * Report run metrics by creating an empty test run to stick them in.
     * @param listener The {@link ITestInvocationListener} of test results
     * @param metrics The {@link Map} that contains metrics for the given test
     */
    private void reportMetrics(ITestInvocationListener listener, Map<String, String> metrics)
            throws IllegalArgumentException {
        // Parse out only averages
        Map<String, Map<String, String>> allMetrics = new HashMap<String, Map<String, String>>();
        for (String key : metrics.keySet()) {
            Matcher m = METRICS_PATTERN.matcher(metrics.get(key));
            if (m.matches()) {
                Map<String, String> parsedMetrics = new HashMap<String, String>();
                parsedMetrics.put(String.format("%s_fgavg", key), m.group(1));
                parsedMetrics.put(String.format("%s_bgavg", key), m.group(4));

                String testLabel = TEST_TAG_MAP.get(key);
                if (testLabel == null) {
                    testLabel = OTHER;
                }
                if (allMetrics.containsKey(testLabel)) {
                    allMetrics.get(testLabel).putAll(parsedMetrics);
                } else {
                    allMetrics.put(testLabel, parsedMetrics);
                }
            }
            else {
                throw new IllegalArgumentException("Input text contains no metrics to parse");
            }
        }

        for (String section : allMetrics.keySet()) {
            Map<String, String> sectionMetrics = allMetrics.get(section);
            if (sectionMetrics != null && !sectionMetrics.isEmpty()) {
                CLog.d("About to report '%s' metrics: %s", section, sectionMetrics);
                listener.testRunStarted(section, 0);
                listener.testRunEnded(0, sectionMetrics);
            }
        }
    }

    IRunUtil getRunUtil() {
        return RunUtil.getDefault();
    }
}
