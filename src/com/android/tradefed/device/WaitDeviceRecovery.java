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

import com.android.ddmlib.IDevice;
import com.android.ddmlib.Log;
import com.android.tradefed.config.Option;
import com.android.tradefed.util.RunUtil;

/**
 * A simple implementation of a {@link IDeviceRecovery} that waits for device to be online and
 * respond to simple commands.
 */
public class WaitDeviceRecovery implements IDeviceRecovery {

    private static final String LOG_TAG = "WaitDeviceRecovery";

    /** the time in ms to wait before beginning recovery attempts */
    private static final int INITIAL_PAUSE_TIME = 5*1000;

    @Option(name="device-wait-time",
            description="maximum time in ms to wait for a single device recovery command")
    private long mWaitTime = 4 * 60 * 1000;

    /**
     * {@inheritDoc}
     */
    public void recoverDevice(IDevice device, IDeviceStateMonitor monitor)
            throws DeviceNotAvailableException {
        // device may have just gone offline
        // sleep a small amount to give ddms state a chance to settle
        // TODO - see if there is better way to handle this
        Log.i(LOG_TAG, String.format("Pausing for %d for %s to recover", INITIAL_PAUSE_TIME,
                device.getSerialNumber()));
        RunUtil.sleep(INITIAL_PAUSE_TIME);

        // wait for device online
        if (!monitor.waitForDeviceOnline(mWaitTime)) {
            throw new DeviceNotAvailableException(String.format("Could not find device %s",
                    device.getSerialNumber()));
        }
        if (!monitor.waitForDeviceAvailable(mWaitTime)) {
            // device is online but not responsive, consider trying a reboot?
            throw new DeviceNotAvailableException(String.format(
                    "Device %s is online but unresponsive", device.getSerialNumber()));
        }
    }

    /**
     * {@inheritDoc}
     */
    public void recoverDeviceBootloader(IDevice device, IDeviceStateMonitor monitor)
            throws DeviceNotAvailableException {
        // TODO: check if device is on adb and reboot ?
        // wait for device in bootloader
        if (!monitor.waitForDeviceBootloader(mWaitTime)) {
            throw new DeviceNotAvailableException(String.format("Could not find device %s in" +
                    "bootloader", device.getSerialNumber()));
        }
    }
}
