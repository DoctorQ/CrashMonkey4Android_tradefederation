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

package com.android.tradefed.targetsetup;

import com.android.ddmlib.Log;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A class that flashes an image on physical Android hardware.
 */
public class DeviceFlasher implements IDeviceFlasher  {

    public static final String BASEBAND_IMAGE_NAME = "radio";

    private static final String LOG_TAG = "DeviceFlasher";

    private UserDataFlashOption mUserDataFlashOption = UserDataFlashOption.FLASH;

    private final IFlashingResourcesRetriever mResourceRetriever;

    /**
     * Creates a {@link DeviceFlasher}.
     *
     * @param resourceRetriever the {@link IFlashingResourcesRetriever} to use
     */
    public DeviceFlasher(IFlashingResourcesRetriever resourceRetriever) {
        mResourceRetriever = resourceRetriever;
    }

    /**
     * {@inheritDoc}
     */
    public void setUserDataFlashOption(UserDataFlashOption flashOption) {
        mUserDataFlashOption = flashOption;
    }

    /**
     * {@inheritDoc}
     */
    public void flash(ITestDevice device, DeviceBuildInfo deviceBuild) throws TargetSetupError,
            DeviceNotAvailableException {
        try {
            Log.i(LOG_TAG, String.format("Flashing device %s with build %d",
                    device.getSerialNumber(), deviceBuild.getBuildId()));
            device.rebootIntoBootloader();

            downloadFlashingResources(device, deviceBuild);

            checkAndFlashBootloader(device, deviceBuild);
            checkAndFlashBaseband(device, deviceBuild);
            eraseCache(device);
            flashUserData(device, deviceBuild);
            flashSystem(device, deviceBuild);

        } finally {
            // TODO: move this to a more generic location
            deviceBuild.cleanUp();
        }
    }

    /**
     * Downloads extra flashing image files needed
     *
     * @param device the {@link ITestDevice} to download resources for
     * @param localBuild the {@link DeviceBuildInfo} to populate. Assumes device image file is
     * already set
     *
     * @throws DeviceNotAvailableException if device is not available
     * @throws TargetSetupError if failed to retrieve resources
     */
    private void downloadFlashingResources(ITestDevice device, DeviceBuildInfo localBuild)
            throws TargetSetupError, DeviceNotAvailableException {
        IFlashingResourcesParser resourceParser = createFlashingResourcesParser(localBuild);

        if (resourceParser.getRequiredBoards() == null) {
            throw new TargetSetupError(String.format("Build %s is missing required board info.",
                    localBuild.getBuildId()));
        }
        String deviceProductType = device.getProductType();
        if (deviceProductType == null) {
            // treat this as a fatal device error
            throw new DeviceNotAvailableException(String.format(
                    "Could not determine product type for device %s", device.getSerialNumber()));
        }
        if (!resourceParser.getRequiredBoards().contains(deviceProductType)) {
            throw new TargetSetupError(String.format("Device %s is %s. Expected %s",
                    device.getSerialNumber(), deviceProductType,
                    resourceParser.getRequiredBoards()));
        }

        String bootloaderVersion = resourceParser.getRequiredBootloaderVersion();
        if (bootloaderVersion != null) {
           localBuild.setBootloaderImageFile(mResourceRetriever.retrieveFile(
                   getBootloaderFilePrefix(device), bootloaderVersion), bootloaderVersion);
        }
        String basebandVersion = resourceParser.getRequiredBasebandVersion();
        if (basebandVersion != null) {
            localBuild.setBasebandImage(mResourceRetriever.retrieveFile(BASEBAND_IMAGE_NAME,
                    basebandVersion), basebandVersion);
        }
        downloadExtraImageFiles(resourceParser, mResourceRetriever, localBuild);
    }

    /**
     * Hook to allow subclasses to download extra custom image files if needed.
     *
     * @param resourceParser the {@link IFlashingResourcesParser}
     * @param retriever the {@link IFlashingResourcesRetriever}
     * @param localBuild the {@link DeviceBuildInfo}
     * @throws TargetSetupError
     */
    protected void downloadExtraImageFiles(IFlashingResourcesParser resourceParser,
            IFlashingResourcesRetriever retriever, DeviceBuildInfo localBuild)
            throws TargetSetupError {
    }

    /**
     * Factory method for creating a {@link IFlashingResourcesParser}.
     * <p/>
     * Exposed for unit testing.
     *
     * @param localBuild the {@link DeviceBuildInfo} to parse
     * @return
     * @throws TargetSetupError
     */
    protected IFlashingResourcesParser createFlashingResourcesParser(DeviceBuildInfo localBuild)
            throws TargetSetupError {
        return new FlashingResourcesParser(localBuild.getDeviceImageFile());
    }

    /**
     * If needed, flash the bootloader image on device.
     * <p/>
     * Will only flash bootloader if current version on device != required version.
     *
     * @param device the {@link ITestDevice} to flash
     * @param deviceBuild the {@link DeviceBuildInfo} that contains the bootloader image to flash
     * @throws DeviceNotAvailableException if device is not available
     * @throws TargetSetupError if failed to flash bootloader
     */
    protected void checkAndFlashBootloader(ITestDevice device, DeviceBuildInfo deviceBuild)
            throws DeviceNotAvailableException, TargetSetupError {
        String currentBootloaderVersion = getImageVersion(device, "bootloader");
        if (deviceBuild.getBootloaderVersion() != null &&
                !deviceBuild.getBootloaderVersion().equals(currentBootloaderVersion)) {
            Log.i(LOG_TAG, String.format("Flashing bootloader %s",
                    deviceBuild.getBootloaderVersion()));
            flashBootloader(device, deviceBuild.getBootloaderImageFile());
        } else {
            Log.i(LOG_TAG, String.format("Bootloader is already version %s, skipping flashing",
                    currentBootloaderVersion));
        }
    }

    /**
     * Flashes the given bootloader image and reboots back into bootloader
     *
     * @param device the {@link ITestDevice} to flash
     * @param bootloaderImageFile the bootloader image {@link File}
     * @throws DeviceNotAvailableException if device is not available
     * @throws TargetSetupError if failed to flash
     */
    protected void flashBootloader(ITestDevice device, File bootloaderImageFile)
            throws DeviceNotAvailableException, TargetSetupError {
        // bootloader images are small, and flash quickly. so use the 'normal' timeout
        executeFastbootCmd(device, "flash", getBootPartitionName(),
                bootloaderImageFile.getAbsolutePath());
        device.rebootIntoBootloader();
    }

    /**
     * Get the boot partition name for this device flasher.
     * <p/>
     * Defaults to 'hboot'. Subclasses should override if necessary.
     */
    protected String getBootPartitionName() {
        return "hboot";
    }

    /**
     * Get the bootloader file prefix.
     * <p/>
     * Defaults to {@link #getBootPartitionName()}. Subclasses should override if necessary.
     *
     * @param device the {@link ITestDevice} to flash
     * @throws DeviceNotAvailableException if device is not available
     * @throws TargetSetupError if failed to get prefix
     */
    protected String getBootloaderFilePrefix(ITestDevice device) throws TargetSetupError,
            DeviceNotAvailableException {
        return getBootPartitionName();
    }

    /**
     * If needed, flash the baseband image on device. Will only flash baseband if current version
     * on device != required version
     *
     * @param device the {@link ITestDevice} to flash
     * @param deviceBuild the {@link DeviceBuildInfo} that contains the baseband image to flash
     * @throws DeviceNotAvailableException if device is not available
     * @throws TargetSetupError if failed to flash baseband
     */
    protected void checkAndFlashBaseband(ITestDevice device, DeviceBuildInfo deviceBuild)
            throws DeviceNotAvailableException, TargetSetupError {
        String currentBasebandVersion = getImageVersion(device, "baseband");
        if (deviceBuild.getBasebandVersion() != null &&
                !deviceBuild.getBasebandVersion().equals(currentBasebandVersion)) {
            Log.i(LOG_TAG, String.format("Flashing baseband %s", deviceBuild.getBasebandVersion()));
            flashBaseband(device, deviceBuild.getBasebandImageFile());
        } else {
            Log.i(LOG_TAG, String.format("Baseband is already version %s, skipping flashing",
                    currentBasebandVersion));
        }
    }

    /**
     * Flashes the given baseband image and reboot back into bootloader
     *
     * @param device the {@link ITestDevice} to flash
     * @param basebandImageFile the baseband image {@link File}
     * @throws DeviceNotAvailableException if device is not available
     * @throws TargetSetupError if failed to flash baseband
     */
    protected void flashBaseband(ITestDevice device, File basebandImageFile)
            throws DeviceNotAvailableException, TargetSetupError {
        executeLongFastbootCmd(device, "flash", BASEBAND_IMAGE_NAME,
                basebandImageFile.getAbsolutePath());
        device.rebootIntoBootloader();
    }

    /**
     * Erase the cache partition on device.
     *
     * @param device the {@link ITestDevice} to flash
     * @throws DeviceNotAvailableException if device is not available
     * @throws TargetSetupError if failed to flash cache
     */
    protected void eraseCache(ITestDevice device) throws DeviceNotAvailableException,
            TargetSetupError {
        // only wipe cache if user data is being wiped
        if (mUserDataFlashOption.equals(UserDataFlashOption.FLASH) ||
                mUserDataFlashOption.equals(UserDataFlashOption.WIPE)) {
            Log.i(LOG_TAG, String.format("Erasing cache on %s", device.getSerialNumber()));
            executeFastbootCmd(device, "erase", "cache");
        } else {
            Log.d(LOG_TAG, String.format("Skipping cache erase on %s", device.getSerialNumber()));
        }
    }

    /**
     * Flash the test userdata image on device.
     *
     * @param device the {@link ITestDevice} to flash
     * @param deviceBuild the {@link DeviceBuildInfo} that contains the userdata to flash
     * @throws DeviceNotAvailableException if device is not available
     * @throws TargetSetupError if failed to flash user data
     */
    protected void flashUserData(ITestDevice device, DeviceBuildInfo deviceBuild)
            throws DeviceNotAvailableException, TargetSetupError {
        switch (mUserDataFlashOption) {
            case FLASH: {
                Log.i(LOG_TAG, String.format("Flashing %s with userdata %s",
                        device.getSerialNumber(),
                        deviceBuild.getUserDataImageFile().getAbsolutePath()));
                executeLongFastbootCmd(device, "flash", "userdata",
                        deviceBuild.getUserDataImageFile().getAbsolutePath());
                break;
            }
            case WIPE: {
                Log.i(LOG_TAG, String.format("Wiping userdata %s", device.getSerialNumber()));
                executeLongFastbootCmd(device, "erase", "userdata");
                break;
            }
            default: {
                Log.d(LOG_TAG, String.format("Skipping userdata flash for %s",
                        device.getSerialNumber()));
            }
        }
    }

    /**
     * Flash the system image on device.
     *
     * @param device the {@link ITestDevice} to flash
     * @param deviceBuild the {@link DeviceBuildInfo} to flash
     * @throws DeviceNotAvailableException if device is not available
     * @throws TargetSetupError if fastboot command fails
     */
    protected void flashSystem(ITestDevice device, DeviceBuildInfo deviceBuild)
            throws DeviceNotAvailableException, TargetSetupError {
        Log.i(LOG_TAG, String.format("Flashing %s with update %s", device.getSerialNumber(),
                deviceBuild.getDeviceImageFile().getAbsolutePath()));
        // give extra time to the update cmd
        executeLongFastbootCmd(device, "update",
                deviceBuild.getDeviceImageFile().getAbsolutePath());
    }

    /**
     * Helper method to get the current image version on device.
     *
     * @param device the {@link ITestDevice} to execute command on
     * @param imageName the name of image to get.
     * @return String the stdout output from command
     * @throws DeviceNotAvailableException if device is not available
     * @throws TargetSetupError if fastboot command fails or version could not be determined
     */
    protected String getImageVersion(ITestDevice device, String imageName)
            throws DeviceNotAvailableException, TargetSetupError {
        String versionQuery = String.format("version-%s", imageName);
        String queryOutput = executeFastbootCmd(device, "getvar", versionQuery);
        String patternString = String.format("%s:\\s(.*)\\s", versionQuery);
        Pattern versionOutputPattern = Pattern.compile(patternString);
        Matcher matcher = versionOutputPattern.matcher(queryOutput);
        if (matcher.find()) {
            return matcher.group(1);
        }
        throw new TargetSetupError(String.format("Could not find version for '%s'. Output '%s'",
                imageName, queryOutput));
    }

    /**
     * Helper method to execute fastboot command.
     *
     * @param device the {@link ITestDevice} to execute command on
     * @param cmdArgs the arguments to provide to fastboot
     * @return String the stderr output from command if non-empty. Otherwise returns the stdout
     * Some fastboot commands are weird in that they dump output to stderr on success case
     *
     * @throws DeviceNotAvailableException if device is not available
     * @throws TargetSetupError if fastboot command fails
     */
    protected String executeFastbootCmd(ITestDevice device, String... cmdArgs)
            throws DeviceNotAvailableException, TargetSetupError {
        CommandResult result = device.executeFastbootCommand(cmdArgs);
        return handleFastbootResult(device, result, cmdArgs);
    }

    /**
     * Helper method to execute a long-running fastboot command.
     * <p/>
     * Note: Most fastboot commands normally execute within the timeout allowed by
     * {@link ITestDevice#executeFastbootCommand(String...)}. However, when multiple devices are
     * flashing devices at once, fastboot commands can take much longer than normal.
     *
     * @param device the {@link ITestDevice} to execute command on
     * @param cmdArgs the arguments to provide to fastboot
     * @return String the stderr output from command if non-empty. Otherwise returns the stdout
     * Some fastboot commands are weird in that they dump output to stderr on success case
     *
     * @throws DeviceNotAvailableException if device is not available
     * @throws TargetSetupError if fastboot command fails
     */
    protected String executeLongFastbootCmd(ITestDevice device, String... cmdArgs)
            throws DeviceNotAvailableException, TargetSetupError {
        CommandResult result = device.executeLongFastbootCommand(cmdArgs);
        return handleFastbootResult(device, result, cmdArgs);
    }

    /**
     * Interpret the result of a fastboot command
     *
     * @param device
     * @param result
     * @param cmdArgs
     * @return the stderr output from command if non-empty. Otherwise returns the stdout
     * @throws TargetSetupError
     */
    private String handleFastbootResult(ITestDevice device, CommandResult result, String... cmdArgs)
            throws TargetSetupError {
        // TODO: consider re-trying
        if (result.getStatus() != CommandStatus.SUCCESS || result.getStderr().contains("FAILED")) {
            throw new TargetSetupError(String.format(
                    "fastboot command %s failed in device %s. stdout: %s, stderr: %s", cmdArgs[0],
                    device.getSerialNumber(), result.getStdout(), result.getStderr()));
        }
        if (result.getStderr().length() > 0) {
            return result.getStderr();
        } else {
            return result.getStdout();
        }
    }
}
