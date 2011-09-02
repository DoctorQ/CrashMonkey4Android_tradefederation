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

import junit.framework.Assert;

/**
 * Run the connectivity manager functional tests. This test verifies the
 * connectivity state transition and data connectivity when the device
 * switches on different network interfaces.
 */
public class ConnectivityManagerTest implements IRemoteTest, IDeviceTest {
    private ITestDevice mTestDevice = null;

    // Define instrumentation test package and runner.
    private static final String TEST_PACKAGE_NAME =
        "com.android.connectivitymanagertest";
    private static final String TEST_RUNNER_NAME =
        ".ConnectivityManagerTestRunner";
    private RadioHelper mRadioHelper;

    @Option(name="ssid",
            description="The ssid used for wi-fi connection.")
    private String mSsid = null;

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
        Assert.assertTrue("Activation failed", mRadioHelper.radioActivation());

        // Add bugreport listener for bugreport after each test case fails
        BugreportCollector bugListener = new
            BugreportCollector(standardListener, mTestDevice);
        bugListener.addPredicate(BugreportCollector.AFTER_FAILED_TESTCASES);
        bugListener.setDescriptiveName("connectivity_manager_test");

        IRemoteAndroidTestRunner runner = new RemoteAndroidTestRunner(
                TEST_PACKAGE_NAME, TEST_RUNNER_NAME, mTestDevice.getIDevice());
        runner.addInstrumentationArg("ssid", mSsid);
        mTestDevice.runInstrumentationTests(runner, bugListener);
    }
}
