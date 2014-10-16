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

import com.android.ddmlib.testrunner.IRemoteAndroidTestRunner;
import com.android.ddmlib.testrunner.RemoteAndroidTestRunner;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.result.BugreportCollector;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.util.RunUtil;

import junit.framework.Assert;

/**
 * Run the connectivity manager functional tests. This test verifies the
 * connectivity state transition and data connectivity when the device
 * switches on different network interfaces.
 */
public class ConnectivityManagerTest implements IRemoteTest, IDeviceTest {
    private ITestDevice mTestDevice = null;

    private static long START_TIMER = 5 * 60 * 1000; //5 minutes
    // Define instrumentation test package and runner.
    private static final String TEST_PACKAGE_NAME =
        "com.android.connectivitymanagertest";
    private static final String TEST_RUNNER_NAME =
        ".ConnectivityManagerTestRunner";
    private static final String TEST_CLASS_NAME =
        String.format("%s.functional.ConnectivityManagerMobileTest", TEST_PACKAGE_NAME);
    private static final int TEST_TIMER = 60 * 60 * 1000;  // 1 hour

    private RadioHelper mRadioHelper;

    @Option(name="ssid",
            description="The ssid used for wi-fi connection.")
    private String mSsid = null;

    @Option(name="method", description="Test method to run")
    private String mTestMethodName = null;

    @Option(name="wifi-only")
    private boolean mWifiOnly = false;

    @Override
    public void setDevice(ITestDevice testDevice) {
        mTestDevice = testDevice;
    }

    @Override
    public ITestDevice getDevice() {
        return mTestDevice;
    }

    @Override
    public void run(ITestInvocationListener standardListener)
            throws DeviceNotAvailableException {
        Assert.assertNotNull(mTestDevice);
        Assert.assertNotNull(mSsid);
        mRadioHelper = new RadioHelper(mTestDevice);
        RunUtil.getDefault().sleep(START_TIMER);
        if (!mWifiOnly) {
            // capture a bugreport if activation or data setup failed
            if (!mRadioHelper.radioActivation() || !mRadioHelper.waitForDataSetup()) {
                mRadioHelper.getBugreport(standardListener);
                return;
            }
        }
        // Add bugreport listener for bugreport after each test case fails
        BugreportCollector bugListener = new
            BugreportCollector(standardListener, mTestDevice);
        bugListener.addPredicate(BugreportCollector.AFTER_FAILED_TESTCASES);
        bugListener.setDescriptiveName("connectivity_manager_test");
        // Device may reboot during the test, to capture a bugreport after that,
        // wait for 30 seconds for device to be online, otherwise, bugreport will be empty
        bugListener.setDeviceWaitTime(30);

        IRemoteAndroidTestRunner runner = new RemoteAndroidTestRunner(
                TEST_PACKAGE_NAME, TEST_RUNNER_NAME, mTestDevice.getIDevice());
        runner.addInstrumentationArg("ssid", mSsid);
        runner.setMaxtimeToOutputResponse(TEST_TIMER);
        if (mTestMethodName != null) {
            runner.setMethodName(TEST_CLASS_NAME, mTestMethodName);
        }
        if (mWifiOnly) {
            runner.addBooleanArg("wifi-only", true);
        }
        mTestDevice.runInstrumentationTests(runner, bugListener);
    }
}
