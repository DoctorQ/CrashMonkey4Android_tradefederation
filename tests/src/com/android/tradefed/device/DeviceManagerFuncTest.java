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
import com.android.tradefed.device.DeviceManager.TestDeviceState;
import com.android.tradefed.testtype.DeviceTestCase;

/**
 * Functional tests for {@link TestDevice}.
 * <p/>
 * Requires a physical device to be connected.
 */
public class DeviceManagerFuncTest extends DeviceTestCase {

    private static final String LOG_TAG = "DeviceManagerFuncTest";
    private DeviceManager mManager;

    /**
     * {@inheritDoc}
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mManager = (DeviceManager)DeviceManager.getInstance();
    }

    /**
     * Verify device can be rebooted into bootloader and back to adb.
     */
    public void testRebootIntoBootloader() throws DeviceNotAvailableException {
        Log.i(LOG_TAG, "testRebootIntoBootloader");
        try {
            mManager.rebootIntoBootloader(getDevice());
            assertEquals(TestDeviceState.FASTBOOT, mManager.getDeviceState(getDevice()));
        } finally {
            mManager.reboot(getDevice());
            assertEquals(TestDeviceState.ONLINE, mManager.getDeviceState(getDevice()));
        }
    }

    /**
     * Verify device can be rebooted into adb.
     */
    public void testReboot() throws DeviceNotAvailableException {
        Log.i(LOG_TAG, "testReboot");
        mManager.reboot(getDevice());
        assertEquals(TestDeviceState.ONLINE, mManager.getDeviceState(getDevice()));
    }

}
