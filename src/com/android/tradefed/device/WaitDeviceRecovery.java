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
import com.android.tradefed.config.Option;
import com.android.tradefed.util.RunUtil;
import com.android.tradefed.util.RunUtil.IRunnableResult;

import java.io.IOException;

/**
 * A simple implementation of a {@link IDeviceRecovery} that waits for device to be online and
 * respond to simple commands.
 */
public class WaitDeviceRecovery implements IDeviceRecovery {

    private static final String LOG_TAG = "WaitDeviceRecovery";

    private static final int CHECK_PM_ATTEMPTS = 5;
    /** the maximum operation time in ms for a 'poll for package manager' command */
    private static final long MAX_PM_POLL_TIME = 30 * 1000;

    @Option(name="device-wait-time",
            description="maximum time in ms to wait for a single device recovery command")
    private long mWaitTime = 4 * 60 * 1000;

    /**
     * {@inheritDoc}
     */
    public void recoverDevice(ITestDevice device) throws DeviceNotAvailableException {
        // device may have just gone offline
        // sleep a small amount to give ddms state a chance to settle
        // TODO - see if there is better way to handle this
        Log.i(LOG_TAG, String.format("Pausing for %s to recover", device.getSerialNumber()));
        RunUtil.sleep(5*1000);

        // TODO: consider checking if device is in bootloader, and if so, reboot to adb mode
        getDeviceManager().waitForDevice(device, mWaitTime);

        // TODO: ensure device running as root here ?
        // device.setRoot();

        checkDevicePmResponsive(device, mWaitTime, CHECK_PM_ATTEMPTS);
    }

    /**
     * Gets the IDeviceManager to be used. Exposed so this can be mocked by unit tests.
     */
    IDeviceManager getDeviceManager() {
        return DeviceManager.getInstance();
    }


    /**
     * Checks if device is responsive to a adb shell pm command.
     *
     * TODO: consider moving this to {@link ITestDevice}
     *
     * @param device the {@link ITestDevice} to test
     * @param time the maximum time in ms to wait
     */
    private void checkDevicePmResponsive(final ITestDevice device, final long time, int attempts)
            throws DeviceNotAvailableException {
        long pollTime = time / attempts;
        IRunnableResult pmPollRunnable = new IRunnableResult() {
            public boolean run() {
                final String cmd = "pm path android";
                try {
                    // TODO move collecting output command to IDevice
                    CollectingOutputReceiver receiver = new CollectingOutputReceiver();
                    // assume the 'adb shell pm path android' command will always
                    // return 'package: something' in the success case
                    // intentionally calling IDevice directly to avoid an infinite loop of
                    // recovery logic
                    device.getIDevice().executeShellCommand(cmd, receiver);
                    String output = receiver.getOutput();
                    Log.d(LOG_TAG, String.format("%s returned %s", cmd, output));
                    return output.contains("package:");
                } catch (IOException e) {
                    Log.i(LOG_TAG, String.format("%s failed: %s", cmd, e.getMessage()));
                    return false;
                }
            }
        };
        if(!RunUtil.runTimedRetry(MAX_PM_POLL_TIME, pollTime, attempts, pmPollRunnable)) {
            Log.w(LOG_TAG, String.format("Device %s package manager is not responding.",
                    device.getSerialNumber()));
           throw new DeviceNotAvailableException();
        }
    }
}
