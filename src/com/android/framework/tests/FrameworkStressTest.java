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
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.TestResult;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.util.brillopad.BugreportParser;
import com.android.tradefed.util.brillopad.ItemList;
import com.android.tradefed.util.brillopad.item.GenericMapItem;
import com.android.tradefed.util.brillopad.item.IItem;
import com.android.tradefed.util.brillopad.section.syslog.AnrParser;
import com.android.tradefed.util.brillopad.section.syslog.JavaCrashParser;
import com.android.tradefed.util.brillopad.section.syslog.NativeCrashParser;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import junit.framework.Assert;

/**
 * Test that instruments a stress test package, gathers iterations metrics, and posts the results.
 */
public class FrameworkStressTest implements IDeviceTest, IRemoteTest {
    public static final String BUGREPORT_LOG_NAME = "bugreport_stress.txt";

    ITestDevice mTestDevice = null;

    @Option(name = "test-package-name", description = "Android test package name.")
    private static String TEST_PACKAGE_NAME = null;

    @Option(name = "test-class-name", description = "Test class name.")
    private static String TEST_CLASS_NAME = null;

    @Option(name = "dashboard-test-label",
            description = "Test label to use when posting to dashboard.")
            private static String DASHBOARD_TEST_LABEL = null;

    private static final String CURRENT_ITERATION_LABEL= "currentiterations";

    @Override
    @SuppressWarnings("unchecked")
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        Assert.assertNotNull(mTestDevice);
        IRemoteAndroidTestRunner runner = new RemoteAndroidTestRunner(TEST_PACKAGE_NAME,
                mTestDevice.getIDevice());
        runner.setClassName(TEST_CLASS_NAME);
        CollectingTestListener collectingListener = new CollectingTestListener();
        mTestDevice.runInstrumentationTests(runner, collectingListener, listener);
        // Retrieve bugreport
        BugreportParser parser = new BugreportParser();
        ItemList bugreport = null;
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

        Map<String, String> stressTestMetrics = new HashMap<String, String>();
        Integer numAnrs = 0;
        Integer numJavaCrashes = 0;
        Integer numNativeCrashes = 0;
        Integer numIterations = 0;
        Integer numSuccessfulIterations = 0;

        IItem item = bugreport.getFirstItemByType(AnrParser.SECTION_NAME);
        if (item != null) {
            Map<String, String> output = (GenericMapItem<String, String>) item;
            numAnrs = output.size();
        }
        item = bugreport.getFirstItemByType(JavaCrashParser.SECTION_NAME);
        if (item != null) {
            Map<String, String> output = (GenericMapItem<String, String>) item;
            numJavaCrashes = output.size();
        }
        item = bugreport.getFirstItemByType(NativeCrashParser.SECTION_NAME);
        if (item != null) {
            Map<String, String> output = (GenericMapItem<String, String>) item;
            numNativeCrashes = output.size();
        }
        // Fetch the last iteration count from the InstrumentationTestResult. We only expect to have
        // one test, and we take the result from the first test result.
        Collection<TestResult> testResults =
            collectingListener.getCurrentRunResults().getTestResults().values();
        if (testResults != null && testResults.iterator().hasNext()) {
            Map<String, String> testMetrics = testResults.iterator().next().getMetrics();
            if (testMetrics != null) {
                CLog.d(testMetrics.toString());
                // We want to report all test metrics as well.
                for (String metric : testMetrics.keySet()) {
                    if (metric.equalsIgnoreCase(CURRENT_ITERATION_LABEL)) {
                        String test_iterations = testMetrics.get(metric);
                        numIterations = Integer.parseInt(test_iterations);
                    } else {
                        stressTestMetrics.put(metric, testMetrics.get(metric));
                    }
                }
            }
        }

        // Calculate the number of successful iterations.
        numSuccessfulIterations = numIterations - numAnrs - numJavaCrashes - numNativeCrashes;

        // Report other metrics from bugreport.
        stressTestMetrics.put("anrs", numAnrs.toString());
        stressTestMetrics.put("java_crashes", numJavaCrashes.toString());
        stressTestMetrics.put("native_crashes", numNativeCrashes.toString());
        stressTestMetrics.put("iterations", numSuccessfulIterations.toString());

        // Post everything to the dashboard.
        reportMetrics(listener, DASHBOARD_TEST_LABEL, stressTestMetrics);
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

    @Override
    public void setDevice(ITestDevice device) {
        mTestDevice = device;
    }

    @Override
    public ITestDevice getDevice() {
        return mTestDevice;
    }
}
