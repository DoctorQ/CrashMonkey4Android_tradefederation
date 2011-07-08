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

package com.android.tradefed.targetprep;

import com.android.ddmlib.Log;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.IDeviceBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.DeviceUnresponsiveException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.ITestDevice.RecoveryMode;
import com.android.tradefed.targetprep.IDeviceFlasher.UserDataFlashOption;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;

/**
 * A {@link ITargetPreparer} that flashes an image on physical Android hardware.
 */
public abstract class DeviceFlashPreparer implements ITargetPreparer {

    private static final String LOG_TAG = "DeviceFlashPreparer";

    private static final int BOOT_POLL_TIME_MS = 5 * 1000;

    @Option(name="device-boot-time", description="max time in ms to wait for device to boot.")
    private long mDeviceBootTime = 5 * 60 * 1000;

    @Option(name="userdata-flash", description=
        "specify handling of userdata partition. One of FLASH, TESTS_ZIP, WIPE, SKIP.")
    private String mUserDataFlashString = UserDataFlashOption.FLASH.toString();

    /**
     * Sets the device boot time
     * <p/>
     * Exposed for unit testing
     */
    void setDeviceBootTime(long bootTime) {
        mDeviceBootTime = bootTime;
    }

    /**
     * Gets the interval between device boot poll attempts.
     * <p/>
     * Exposed for unit testing
     */
    int getDeviceBootPollTimeMs() {
        return BOOT_POLL_TIME_MS;
    }

    /**
     * Gets the {@link IRunUtil} instance to use.
     * <p/>
     * Exposed for unit testing
     */
    IRunUtil getRunUtil() {
        return RunUtil.getDefault();
    }

    /**
     * Set the userdata-flash option
     *
     * @param flashOption
     */
    public void setUserDataFlashOption(String flashOption) {
        mUserDataFlashString = flashOption;
    }

    /**
     * {@inheritDoc}
     */
    public void setUp(ITestDevice device, IBuildInfo buildInfo) throws TargetSetupError,
            DeviceNotAvailableException, BuildError {
        Log.i(LOG_TAG, String.format("Performing setup on %s", device.getSerialNumber()));
        if (!(buildInfo instanceof IDeviceBuildInfo)) {
            throw new IllegalArgumentException("Provided buildInfo is not a IDeviceBuildInfo");
        }
        IDeviceBuildInfo deviceBuild = (IDeviceBuildInfo)buildInfo;
        device.setRecoveryMode(RecoveryMode.ONLINE);
        IDeviceFlasher flasher = createFlasher(device);
        flasher.setUserDataFlashOption(UserDataFlashOption.valueOf(mUserDataFlashString));
        flasher.flash(device, deviceBuild);
        device.waitForDeviceOnline();
        // only want logcat captured for current build, delete any accumulated log data
        device.clearLogcat();
        waitForBootComplete(device, buildInfo.getBuildId());
        device.setRecoveryMode(RecoveryMode.AVAILABLE);
        try {
            device.waitForDeviceAvailable();
        } catch (DeviceUnresponsiveException e) {
            // assume this is a build problem
            throw new BuildError(String.format("Device %s did not become available after flashing",
                    device.getSerialNumber()));
        }
        device.postBootSetup();
    }

    /**
     * Create {@link IDeviceFlasher} to use. Subclasses can override
     * @throws DeviceNotAvailableException
     */
    protected abstract IDeviceFlasher createFlasher(ITestDevice device)
            throws DeviceNotAvailableException;

    /**
     * Blocks until the device's boot complete flag is set
     *
     * @param device the {@link ITestDevice}
     * @param buildId the build id of current build. Used for logging purposes
     * @throws DeviceNotAvailableException, BuildError
     */
    private void waitForBootComplete(ITestDevice device, int buildId)
            throws DeviceNotAvailableException, BuildError {
        long startTime = System.currentTimeMillis();
        while ((System.currentTimeMillis() - startTime) < mDeviceBootTime) {
            String output = device.executeShellCommand("getprop dev.bootcomplete");
            output = output.replace('#', ' ').trim();
            if (output.equals("1")) {
                return;
            }
            getRunUtil().sleep(getDeviceBootPollTimeMs());
        }
        throw new BuildError(String.format("Device %s running build %d did not boot after %d ms",
                device.getSerialNumber(), buildId, mDeviceBootTime));
    }
}
