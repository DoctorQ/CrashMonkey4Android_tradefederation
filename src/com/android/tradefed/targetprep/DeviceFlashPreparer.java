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
import com.android.tradefed.log.LogUtil.CLog;
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
        "specify handling of userdata partition. One of FLASH, TESTS_ZIP, WIPE, WIPE_RM, SKIP.")
    private String mUserDataFlashString = UserDataFlashOption.FLASH.toString();

    @Option(name="encrypt-userdata", description=
        "specify if userdata partition should be encrypted")
    private boolean mEncryptUserData = false;

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
        preEncryptDevice(device, flasher);
        flasher.flash(device, deviceBuild);
        device.waitForDeviceOnline();
        postEncryptDevice(device, flasher);
        // only want logcat captured for current build, delete any accumulated log data
        device.clearLogcat();
        try {
            waitForBootComplete(device, buildInfo.getBuildId());
            device.setRecoveryMode(RecoveryMode.AVAILABLE);
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
    private void waitForBootComplete(ITestDevice device, String buildId)
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
        throw new BuildError(String.format("Device %s running build %s did not boot after %d ms",
                device.getSerialNumber(), buildId, mDeviceBootTime));
    }

    /**
     * Handle encrypting or unencrypting of the device pre-flash.
     *
     * @see #postEncryptDevice(ITestDevice, IDeviceFlasher)
     * @param device
     * @throws DeviceNotAvailableException
     * @throws TargetSetupError if the device should be unencrypted but the
     * {@link IDeviceFlasher.UserDataFlashOption#RETAIN} flash option is used.
     */
    private void preEncryptDevice(ITestDevice device, IDeviceFlasher flasher)
            throws DeviceNotAvailableException, TargetSetupError {
        if (!device.isEncryptionSupported()) {
            if (mEncryptUserData) {
                CLog.e("Encryption on %s is not supported", device.getSerialNumber());
            }
            return;
        }

        // Need to unencrypt device
        if (!mEncryptUserData && device.isDeviceEncrypted()) {
            if (flasher.getUserDataFlashOption() == UserDataFlashOption.RETAIN) {
                throw new TargetSetupError(String.format("not possible to go from encrypted "
                        + "userdata partition to unencrypted with %s",
                        flasher.getUserDataFlashOption()));
            }
            device.unencryptDevice();
        }

        // Need to encrypt device
        if (mEncryptUserData && !device.isDeviceEncrypted()) {
            switch(flasher.getUserDataFlashOption()) {
                case TESTS_ZIP:
                case WIPE_RM:
                    device.encryptDevice(false);
                    device.unlockDevice();
                    break;
                case RETAIN:
                    device.encryptDevice(true);
                    device.unlockDevice();
                    break;
                default:
                    // Do nothing, userdata will be encrypted post-flash.
            }
        }
    }

    /**
     * Handle encrypting of the device post-flash.
     * <p>
     * This method handles encrypting the device after a flash in cases where a flash would undo any
     * encryption pre-flash, such as when the device is flashed or wiped.
     * </p>
     *
     * @see #preEncryptDevice(ITestDevice, IDeviceFlasher)
     * @param device
     * @throws DeviceNotAvailableException
     */
    private void postEncryptDevice(ITestDevice device, IDeviceFlasher flasher)
            throws DeviceNotAvailableException {
        if (!device.isEncryptionSupported()) {
            if (mEncryptUserData) {
                CLog.e("Encryption on %s is not supported", device.getSerialNumber());
            }
            return;
        }

        if (mEncryptUserData) {
            switch(flasher.getUserDataFlashOption()) {
                case FLASH:
                    device.encryptDevice(true);
                    break;
                case WIPE:
                case FORCE_WIPE:
                    device.encryptDevice(false);
                    break;
                default:
                    // Do nothing, userdata was encrypted pre-flash.
            }
            device.unlockDevice();
        }
    }
}
