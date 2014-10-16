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
package com.android.tradefed.device;

import com.android.ddmlib.Log;
import com.android.tradefed.device.IDeviceManager.FreeDeviceState;
import com.android.tradefed.testtype.DeviceTestCase;

/**
 * Functional tests for {@link DeviceManager}
 */
public class DeviceManagerFuncTest extends DeviceTestCase {

    private static final String LOG_TAG = "DeviceManagerFuncTest";
    private ITestDevice mUsbDevice;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mUsbDevice = getDevice();
    }

    /**
     * Test reconnecting a device to adb over tcp, and back to usb again.
     * <p/>
     * Note: device under test must have a valid IP, that can be connected to from host machine
     * @throws DeviceNotAvailableException
     */
    public void testReconnectDeviceToTcp_backUsb() throws DeviceNotAvailableException {
        Log.i(LOG_TAG, "Starting testReconnectDeviceToTcp_backUsb");
        ITestDevice tcpDevice = DeviceManager.getInstance().reconnectDeviceToTcp(mUsbDevice);
        assertNotNull(tcpDevice);
        assertTrue(tcpDevice.isAdbTcp());
        assertTrue(DeviceManager.getInstance().disconnectFromTcpDevice(tcpDevice));
        // ensure device is back on usb
        mUsbDevice.waitForDeviceAvailable(30 * 1000);
    }

    /**
     * Test reconnecting a device to adb over tcp, and rebooting.
     * <p/>
     * Done to verify the usb variant device will be detected again if it reboots.
     * <p/>
     * Note: device under test must have a valid IP, that can be connected to from host machine
     *
     * @throws DeviceNotAvailableException
     */
    public void testReconnectDeviceToTcp_reboot() throws DeviceNotAvailableException {
        Log.i(LOG_TAG, "Starting testReconnectDeviceToTcp_reboot");
        ITestDevice tcpDevice = DeviceManager.getInstance().reconnectDeviceToTcp(mUsbDevice);
        assertNotNull(tcpDevice);
        try {
            try {
                tcpDevice.reboot();
                fail("DeviceNotAvailableException not thrown");
            } catch (DeviceNotAvailableException e) {
                // expected
            }
            // ensure device is back on usb
            mUsbDevice.waitForDeviceAvailable();
        } finally {
            DeviceManager.getInstance().freeDevice(tcpDevice, FreeDeviceState.IGNORE);
        }

    }
}
