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
import com.android.ddmlib.Log.LogLevel;
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

import java.util.ArrayList;
import java.util.Collection;

/**
 * A {@link ITargetPreparer} that flashes an image on physical Android hardware.
 */
public abstract class DeviceFlashPreparer implements ITargetPreparer {

    private static final String LOG_TAG = "DeviceFlashPreparer";

    private static final int BOOT_POLL_TIME_MS = 5 * 1000;

    @Option(name="device-boot-time", description="max time in ms to wait for device to boot.")
    private long mDeviceBootTime = 5 * 60 * 1000;

    @Option(name="userdata-flash", description=
        "specify handling of userdata partition.")
    private UserDataFlashOption mUserDataFlashOption = UserDataFlashOption.FLASH;

    @Option(name="encrypt-userdata", description=
        "specify if userdata partition should be encrypted")
    private boolean mEncryptUserData = false;

    @Option(name="force-system-flash", description=
        "specify if system should always be flashed even if already running desired build.")
    private boolean mForceSystemFlash = false;

    @Option(name="wipe-skip-list", description=
        "list of /data subdirectories to NOT wipe when doing UserDataFlashOption.TESTS_ZIP")
    private Collection<String> mDataWipeSkipList = new ArrayList<String>();

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
    public void setUserDataFlashOption(UserDataFlashOption flashOption) {
        mUserDataFlashOption = flashOption;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setUp(ITestDevice device, IBuildInfo buildInfo) throws TargetSetupError,
            DeviceNotAvailableException, BuildError {
        Log.i(LOG_TAG, String.format("Performing setup on %s", device.getSerialNumber()));
        if (!(buildInfo instanceof IDeviceBuildInfo)) {
            throw new IllegalArgumentException("Provided buildInfo is not a IDeviceBuildInfo");
        }
        IDeviceBuildInfo deviceBuild = (IDeviceBuildInfo)buildInfo;
        device.setRecoveryMode(RecoveryMode.ONLINE);
        IDeviceFlasher flasher = createFlasher(device);
        flasher.overrideDeviceOptions(device);
        flasher.setUserDataFlashOption(mUserDataFlashOption);
        flasher.setForceSystemFlash(mForceSystemFlash);
        flasher.setDataWipeSkipList(mDataWipeSkipList);
        preEncryptDevice(device, flasher);
        flasher.flash(device, deviceBuild);
    	//after flash, the device may not be online due to driver or other error
        try {
        	//等待设备开机处于online状态
        	CLog.logAndDisplay(LogLevel.DEBUG, String.format("OS have been setup on %s,device is starting up,please wait...", device.getSerialNumber()));
        	device.waitForDeviceOnline();
        } catch (DeviceNotAvailableException e) {
            // assume this is a build problem
            throw new DeviceNotAvailableOnBootError(String.format(
                    "Device %s did not become online after flashing %s",
                    device.getSerialNumber(), deviceBuild.getDeviceBuildId()));
        }
        postEncryptDevice(device, flasher);
        // only want logcat captured for current build, delete any accumulated log data
        device.clearLogcat();
        try {
            device.setRecoveryMode(RecoveryMode.AVAILABLE);
            device.waitForDeviceAvailable(mDeviceBootTime);
        } catch (DeviceUnresponsiveException e) {
            // assume this is a build problem
            throw new DeviceFailedToBootError(String.format(
                    "Device %s did not become available after flashing %s",
                    device.getSerialNumber(), deviceBuild.getDeviceBuildId()));
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
     * Handle encrypting or unencrypting of the device pre-flash.
     *
     * @see #postEncryptDevice(ITestDevice, IDeviceFlasher)
     * @param device
     * @throws DeviceNotAvailableException
     * @throws TargetSetupError if the device should be unencrypted but the
     *     {@link IDeviceFlasher.UserDataFlashOption#RETAIN} flash option is used, or if the device
     *     could not be encrypted, unencrypted, or unlocked.
     */
    private void preEncryptDevice(ITestDevice device, IDeviceFlasher flasher)
            throws DeviceNotAvailableException, TargetSetupError {
        if (!device.isEncryptionSupported()) {
            if (mEncryptUserData) {
                throw new TargetSetupError("Encryption is not supported");
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
            if (!device.unencryptDevice()) {
                throw new TargetSetupError("Failed to unencrypt device");
            }
        }

        // Need to encrypt device
        if (mEncryptUserData && !device.isDeviceEncrypted()) {
            switch(flasher.getUserDataFlashOption()) {
                case TESTS_ZIP: // Intentional fall through.
                case WIPE_RM:
                    if (!device.encryptDevice(false)) {
                        throw new TargetSetupError("Failed to encrypt device");
                    }
                    if (!device.unlockDevice()) {
                        throw new TargetSetupError("Failed to unlock device");
                    }
                    break;
                case RETAIN:
                    if (!device.encryptDevice(true)) {
                        throw new TargetSetupError("Failed to encrypt device");
                    }
                    if (!device.unlockDevice()) {
                        throw new TargetSetupError("Failed to unlock device");
                    }
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
     * @throws TargetSetupError If the device could not be encrypted or unlocked.
     */
    private void postEncryptDevice(ITestDevice device, IDeviceFlasher flasher)
            throws DeviceNotAvailableException, TargetSetupError {
        if (!device.isEncryptionSupported()) {
            if (mEncryptUserData) {
                throw new TargetSetupError("Encryption is not supported");
            }
            return;
        }

        if (mEncryptUserData) {
            switch(flasher.getUserDataFlashOption()) {
                case FLASH:
                    if (!device.encryptDevice(true)) {
                        throw new TargetSetupError("Failed to encrypt device");
                    }
                    break;
                case WIPE: // Intentional fall through.
                case FORCE_WIPE:
                    if (!device.encryptDevice(false)) {
                        throw new TargetSetupError("Failed to encrypt device");
                    }
                    break;
                default:
                    // Do nothing, userdata was encrypted pre-flash.
            }
            if (!device.unlockDevice()) {
                throw new TargetSetupError("Failed to unlock device");
            }
        }
    }
}
