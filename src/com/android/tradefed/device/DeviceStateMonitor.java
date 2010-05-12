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
import com.android.tradefed.util.RunUtil;
import com.android.tradefed.util.RunUtil.IRunnableResult;

import java.io.IOException;

/**
 * Helper class for monitoring the state of a {@link IDevice}.
 */
class DeviceStateMonitor implements IDeviceStateMonitor {

    private static final String LOG_TAG = "DeviceStateMonitor";
    private final IDevice mDevice;
    private final IAndroidDebugBridge mAdbBridge;

    /** the time in ms to wait between 'poll for package manager' attempts */
    private static final int CHECK_PM_POLL_TIME = 5 * 1000;
    /** the maximum operation time in ms for a 'poll for package manager' command */
    private static final long MAX_PM_POLL_TIME = 30 * 1000;

    /** The default time in ms to wait for a device command to complete. */
    private static final int DEFAULT_CMD_TIMEOUT = 2 * 60 * 1000;

    private static final int FASTBOOT_POLL_ATTEMPTS = 5;

    /** the ratio of time to wait for device to be online vs responsive in
     * {@link DeviceManager#waitForDeviceAvailable(ITestDevice, long)}.
     */
    private static final float WAIT_DEVICE_RATIO = 0.4f;

    /** The  time in ms to wait for a device to boot. */
    // TODO: make this configurable - or auto-scale according to device performance ?
    private static final long DEFAULT_BOOT_TIMEOUT = 4 * 60 * 1000;

    DeviceStateMonitor(IDevice device, IAndroidDebugBridge adbBridge) {
        mDevice = device;
        mAdbBridge = adbBridge;
    }

    /**
     * {@inheritDoc}
     */
    public boolean waitForDeviceOnline(long waitTime) {
        return waitForDeviceState(TestDeviceState.ONLINE, waitTime);
    }

    /**
     * {@inheritDoc}
     */
    public boolean waitForDeviceOnline() {
        return waitForDeviceOnline((long)(DEFAULT_BOOT_TIMEOUT*WAIT_DEVICE_RATIO));
    }

    /**
     * {@inheritDoc}
     */
    public boolean waitForDeviceNotAvailable(long waitTime) {
        return waitForDeviceState(TestDeviceState.NOT_AVAILABLE, waitTime);
    }

    /**
     * {@inheritDoc}
     */
    public boolean waitForDeviceAvailable(final long waitTime) {
        if (waitForDeviceOnline((long)(waitTime*WAIT_DEVICE_RATIO))) {
            return waitForPmResponsive((long)(waitTime*(1-WAIT_DEVICE_RATIO)));
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    public boolean waitForDeviceAvailable() {
        return waitForDeviceAvailable(DEFAULT_BOOT_TIMEOUT);
    }

    /**
     * Waits for the device package manager to be responsive.
     *
     * @param waitTime time in ms to wait before giving up
     * @return <code>true</code> if package manage becomes responsive before waitTime expires.
     * <code>false</code> otherwise
     */
    boolean waitForPmResponsive(final long waitTime) {
        Log.i(LOG_TAG, String.format("Waiting for device %s package manager",
                mDevice.getSerialNumber()));
        IRunnableResult pmPollRunnable = new IRunnableResult() {
            public boolean run() {
                final String cmd = "pm path android";
                try {
                    // TODO move collecting output command to IDevice
                    CollectingOutputReceiver receiver = new CollectingOutputReceiver();
                    // assume the 'adb shell pm path android' command will always
                    // return 'package: something' in the success case
                    mDevice.executeShellCommand(cmd, receiver);
                    String output = receiver.getOutput();
                    Log.d(LOG_TAG, String.format("%s returned %s", cmd, output));
                    return output.contains("package:");
                } catch (IOException e) {
                    Log.i(LOG_TAG, String.format("%s failed: %s", cmd, e.getMessage()));
                    return false;
                }
            }
        };
        int numAttempts = (int)(waitTime / CHECK_PM_POLL_TIME);
        return RunUtil.runTimedRetry(MAX_PM_POLL_TIME, CHECK_PM_POLL_TIME, numAttempts,
                pmPollRunnable);
    }

    /**
     * {@inheritDoc}
     */
    public TestDeviceState getDeviceState() {
        if (mDevice.isOnline() && isDeviceOnAdb()) {
            return TestDeviceState.ONLINE;
        } else if (mDevice.isOffline() && isDeviceOnAdb()) {
            return TestDeviceState.OFFLINE;
        } else if (isDeviceOnFastboot()) {
            return TestDeviceState.FASTBOOT;
        } else {
            return TestDeviceState.NOT_AVAILABLE;
        }
    }

    private boolean isDeviceOnAdb() {
        // if device has just disappeared from adb entirely, it may still be marked as online
        // ensure its active on adb bridge
        for (IDevice device : mAdbBridge.getDevices()) {
            if (device.getSerialNumber().equals(getSerialNumber())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Helper method for retrieving device serial number.
     */
    String getSerialNumber() {
        return mDevice.getSerialNumber();
    }

    private boolean isDeviceOnFastboot() {
        String fastbootOut = RunUtil.runTimedCmd(DEFAULT_CMD_TIMEOUT, "fastboot", "devices");
        Log.d(LOG_TAG, String.format("fastboot devices returned %s", fastbootOut));
        return fastbootOut != null && fastbootOut.contains(getSerialNumber());
    }

    /**
     * {@inheritDoc}
     */
    public boolean waitForDeviceBootloader(long time) {
        long pollTime = time / FASTBOOT_POLL_ATTEMPTS;
        for (int i=0; i < FASTBOOT_POLL_ATTEMPTS; i++) {
            if (isDeviceOnFastboot()) {
                return true;
            }
            try {
                Thread.sleep(pollTime);
            } catch (InterruptedException e) {
                // ignore
            }
        }
        return false;
    }

    private boolean waitForDeviceState(TestDeviceState state, long time) {
        String deviceSerial = getSerialNumber();
        if (getDeviceState() == state) {
            Log.i(LOG_TAG, String.format("Device %s is already %s", deviceSerial, state));
            return true;
        }
        Log.i(LOG_TAG, String.format("Waiting for device %s to be %s...", deviceSerial, state));
        AdbDeviceListener listener = new AdbDeviceListener(deviceSerial, state);
        mAdbBridge.addDeviceChangeListener(listener);
        IDevice device = listener.waitForDevice(time);
        mAdbBridge.removeDeviceChangeListener(listener);
        if (device == null) {
            Log.i(LOG_TAG, String.format("Device %s could not be found in state %s", deviceSerial,
                    state));
            return false;
        } else {
            Log.i(LOG_TAG, String.format("Found device %s %s", device.getSerialNumber(), state));
            return true;
        }
    }
}
