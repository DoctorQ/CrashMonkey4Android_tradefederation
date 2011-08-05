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

import com.android.tradefed.build.IDeviceBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;

/**
 * Flashes a device image on a device.
 */
public interface IDeviceFlasher {

    /**
     * Enum of options for handling the userdata image
     */
    public enum UserDataFlashOption {
        /** flash the given userdata image on device */
        FLASH,
        /** wipe the device's userdata partition using fastboot erase, if supported by device */
        WIPE,
        /** wipe the device's userdata partition using fastboot erase, even if it's unadvised */
        FORCE_WIPE,
        /** delete content from the device's /data partition using adb shell rm */
        WIPE_RM,
        /** push the contents of the tests zip file onto the device's userdata partition */
        TESTS_ZIP,
        /** leave the userdata partition as is */
        RETAIN;
    }

    /**
     * Toggles whether the user data image should be flashed, wiped, or retained
     *
     * @param flashOption
     */
    public void setUserDataFlashOption(UserDataFlashOption flashOption);

    /**
     * Toggles whether the user data image should be encrypted.
     *
     * @param encrypt
     */
    public void setEncryptUserData(boolean encrypt);

    /**
     * Flashes build on device.
     * <p/>
     * Returns immediately after flashing is complete. Callers should wait for device to be
     * online and available before proceeding with testing.
     *
     * @param device the {@link ITestDevice} to flash
     * @param deviceBuild the {@link IDeviceBuildInfo} to flash
     *
     * @throws TargetSetupError if failed to flash build
     * @throws DeviceNotAvailableException if device becomes unresponsive
     */
    public void flash(ITestDevice device, IDeviceBuildInfo deviceBuild) throws TargetSetupError,
            DeviceNotAvailableException;

}
