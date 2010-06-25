/*
 * Copyright (C) 2010 The Android Open Source Project
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
package com.android.tradefed.device;

import com.android.ddmlib.Log;
import com.android.ddmlib.testrunner.RemoteAndroidTestRunner;
import com.android.tradefed.TestAppConstants;
import com.android.tradefed.result.CollectingTestListener;
import com.android.tradefed.testtype.DeviceTestCase;

/**
 * Long running functional tests for {@link TestDevice} that verify an operation can be run
 * many times in sequence
 * <p/>
 * Requires a physical device to be connected.
 */
public class TestDeviceStressTest extends DeviceTestCase {

    private static final String LOG_TAG = "TestDeviceStressTest";
    private TestDevice mTestDevice;
    private IDeviceStateMonitor mMonitor;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mTestDevice = (TestDevice)getDevice();
        mMonitor = mTestDevice.getDeviceStateMonitor();
    }

    public void testManyReboots() throws DeviceNotAvailableException {
        for (int i=0; i < 10; i++) {
            Log.i(LOG_TAG, String.format("testReboot attempt %d", i));
            mTestDevice.reboot();
            assertEquals(TestDeviceState.ONLINE, mMonitor.getDeviceState());
        }
    }

    public void testManyRebootBootloaders() throws DeviceNotAvailableException {
        for (int i=0; i < 10; i++) {
            Log.i(LOG_TAG, String.format("testRebootBootloader attempt %d", i));
            mTestDevice.rebootIntoBootloader();
            assertEquals(TestDeviceState.FASTBOOT, mMonitor.getDeviceState());
            mTestDevice.reboot();
            assertEquals(TestDeviceState.ONLINE, mMonitor.getDeviceState());
        }
    }

    public void testManyDisableKeyguard() throws DeviceNotAvailableException {
        for (int i=0; i < 10; i++) {
            Log.i(LOG_TAG, String.format("testDisableKeyguard attempt %d", i));
            mTestDevice.reboot();
            assertTrue(runUITests());
        }
    }

    /**
     * Run the test app UI tests and return true if they all pass.
     */
    private boolean runUITests() throws DeviceNotAvailableException {
        RemoteAndroidTestRunner uirunner = new RemoteAndroidTestRunner(
                TestAppConstants.UITESTAPP_PACKAGE, getDevice().getIDevice());
        CollectingTestListener uilistener = new CollectingTestListener();
        getDevice().runInstrumentationTests(uirunner, uilistener);
        return TestAppConstants.UI_TOTAL_TESTS == uilistener.getNumPassedTests();
    }
}
