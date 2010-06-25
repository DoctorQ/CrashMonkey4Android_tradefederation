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
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;

import java.io.IOException;

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
     * Get the {@link RunUtil} instance to use.
     * <p/>
     * Exposed for unit testing.
     */
    IRunUtil getRunUtil() {
        return RunUtil.getInstance();
    }

    /**
     * {@inheritDoc}
     */
    public void recoverDevice(IDeviceStateMonitor monitor)
            throws DeviceNotAvailableException {
        // device may have just gone offline
        // sleep a small amount to give ddms state a chance to settle
        // TODO - see if there is better way to handle this
        Log.i(LOG_TAG, String.format("Pausing for %d for %s to recover", INITIAL_PAUSE_TIME,
                monitor.getSerialNumber()));
        getRunUtil().sleep(INITIAL_PAUSE_TIME);

        // TODO: consider changing this to waitForDeviceBootloader so state is refreshed
        if (monitor.getDeviceState() == TestDeviceState.FASTBOOT) {
            Log.i(LOG_TAG, String.format(
                    "Found device %s in fastboot but expected online. Rebooting...",
                    monitor.getSerialNumber()));
            // TODO: retry if failed
            getRunUtil().runTimedCmd(20*1000, "fastboot", "-s", monitor.getSerialNumber(),
                    "reboot");
        }

        // wait for device online
        IDevice device = monitor.waitForDeviceOnline(mWaitTime);
        if (device == null) {
            throw new DeviceNotAvailableException(String.format("Could not find device %s",
                    monitor.getSerialNumber()));
        }
        if (monitor.waitForDeviceAvailable(mWaitTime) == null) {
            // device is online but not responsive, consider trying a reboot?
            throw new DeviceNotAvailableException(String.format(
                    "Device %s is online but unresponsive", monitor.getSerialNumber()));
        }
    }

    /**
     * {@inheritDoc}
     */
    public void recoverDeviceBootloader(final IDeviceStateMonitor monitor)
            throws DeviceNotAvailableException {
        // device may have just gone offline
        // sleep a small amount to give device state a chance to settle
        // TODO - see if there is better way to handle this
        Log.i(LOG_TAG, String.format("Pausing for %d for %s to recover", INITIAL_PAUSE_TIME,
                monitor.getSerialNumber()));
        getRunUtil().sleep(INITIAL_PAUSE_TIME);

        if (monitor.getDeviceState() == TestDeviceState.ONLINE) {
            Log.i(LOG_TAG, String.format(
                    "Found device %s online but expected fastboot. Rebooting...",
                    monitor.getSerialNumber()));
            // TODO: retry if failed
            IDevice device = monitor.waitForDeviceAvailable();
            try {
                device.reboot("bootloader");
            } catch (IOException e) {
                Log.w(LOG_TAG, String.format("failed to reboot %s", device.getSerialNumber()));
            }
        } else if (monitor.getDeviceState() == TestDeviceState.FASTBOOT) {
            Log.i(LOG_TAG, String.format(
                    "Found device %s in fastboot but unresponsive. Rebooting...",
                    monitor.getSerialNumber()));
            // TODO: retry
            getRunUtil().runTimedCmd(20*1000, "fastboot", "-s", monitor.getSerialNumber(),
                    "reboot-bootloader");
        }

        if (!monitor.waitForDeviceBootloader(mWaitTime)) {
            throw new DeviceNotAvailableException(String.format(
                    "Could not find device %s in bootloader", monitor.getSerialNumber()));
        }
    }
}
