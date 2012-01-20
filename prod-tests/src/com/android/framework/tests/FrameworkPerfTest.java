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

import com.android.ddmlib.IDevice;
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.Set;

import junit.framework.Assert;


/**
 * Test that measures the average latency of foreground and background
 * operations in various scenarios.
 */
public class FrameworkPerfTest implements IRemoteTest, IDeviceTest {

    private static final String TEST_CLASS_NAME = "com.android.frameworkperf.FrameworkPerfTest";
    private static final String TEST_PACKAGE_NAME = "com.android.frameworkperf";
    private static final String TEST_RUNNER_NAME = "android.test.InstrumentationTestRunner";
    private static final String TEST_TAG = "FrameworkPerformanceTests";
    private static final Pattern METRICS_PATTERN =
            Pattern.compile("(\\d+\\.\\d+),(\\d+),(\\d+),(\\d+\\.\\d+),(\\d+),(\\d+)");
    private static final int PERF_TIMEOUT = 30*60*1000; //30 minutes timeout

    private ITestDevice mTestDevice = null;

    /**
     * {@inheritDoc}
     */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        Assert.assertNotNull(mTestDevice);

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
                reportMetrics(listener, TEST_TAG, testMetrics);
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
     * @param runName The test name
     * @param metrics The {@link Map} that contains metrics for the given test
     */
    private void reportMetrics(ITestInvocationListener listener, String runName,
        Map<String, String> metrics) throws IllegalArgumentException {
        // Parse out only averages
        Map<String, String> parsedMetrics = new HashMap<String, String>();
        Iterator<String> keySetIterator = metrics.keySet().iterator();

        for (String key : metrics.keySet()) {
            Matcher m = METRICS_PATTERN.matcher(metrics.get(key));
            if (m.matches()) {
                parsedMetrics.put(String.format("%s_fgavg", key), m.group(1));
                parsedMetrics.put(String.format("%s_bgavg", key), m.group(4));
            }
            else {
                throw new IllegalArgumentException("Input text contains no metrics to parse");
            }
        }

        CLog.d("About to report metrics: %s", parsedMetrics);

        listener.testRunStarted(runName, 0);
        listener.testRunEnded(0, parsedMetrics);
    }
}
