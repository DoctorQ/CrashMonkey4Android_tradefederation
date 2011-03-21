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

import com.android.ddmlib.Log;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;

import java.util.HashMap;
import java.util.Map;

import junit.framework.Assert;

/**
 * Tests to gather device metrics from during and immediately after boot
 */
public class StartupMetricsTest implements IDeviceTest, IRemoteTest {
    private static final String LOG_TAG = "StartupMetricsTest";

    ITestDevice mTestDevice = null;

    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        Assert.assertNotNull(mTestDevice);

        executeRebootTest(listener);
    }

    /**
     * Check how long the device takes to come online and become available after a reboot
     */
    void executeRebootTest(ITestInvocationListener listener) throws DeviceNotAvailableException {
        Map<String, String> runMetrics = new HashMap<String, String>();

        long startTime = System.currentTimeMillis();
        mTestDevice.rebootUntilOnline();
        long onlineTime = System.currentTimeMillis();
        mTestDevice.waitForDeviceAvailable();
        long availableTime = System.currentTimeMillis();

        long offlineDuration = onlineTime - startTime;
        long unavailDuration = availableTime - startTime;
        Log.d(LOG_TAG, String.format("Reboot: %d millis until online, %d until available",
                offlineDuration, unavailDuration));
        runMetrics.put("offline", Long.toString(offlineDuration));
        runMetrics.put("unavail", Long.toString(unavailDuration));

        reportMetrics(listener, "reboot", runMetrics);
    }

    /**
     * Report run metrics by creating an empty test run to stick them in
     * <p />
     * Exposed for unit testing
     */
    void reportMetrics(ITestInvocationListener listener, String runName,
            Map<String, String> metrics) {
        // Create an empty testRun to report the parsed runMetrics
        Log.d(LOG_TAG, String.format("About to report metrics: %s", metrics));
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

