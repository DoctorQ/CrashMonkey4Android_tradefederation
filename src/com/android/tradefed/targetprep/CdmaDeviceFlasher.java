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

package com.android.tradefed.targetprep;

import com.android.ddmlib.Log;
import com.android.tradefed.build.IDeviceBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;

import java.io.File;
import java.io.IOException;
import java.util.zip.ZipFile;

/**
 * A class that flashes an image on a physical Android device with a CDMA radio.
 * <p />
 * This class is required because a special flashing sequence is needed to properly update the
 * radio baseband, since it is typically the case that the radio and bootloader can't communicate
 * directly.  Typically, they use the RIL (which runs in userspace) as a proxy.
 */
public class CdmaDeviceFlasher extends DeviceFlasher {
    private static final String LOG_TAG = "CdmaDeviceFlasher";

    private boolean mShouldFlashBaseband = false;

    /** Time to allow for baseband to flash (in recovery mode), in ms */
    protected static final int BASEBAND_FLASH_TIMEOUT = 1000*60*10;

    public CdmaDeviceFlasher(IFlashingResourcesRetriever resourceRetriever) {
        super(resourceRetriever);
    }

    /**
     * {@inheritDoc}
     */
    protected String getBootPartitionName() {
        return "bootloader";
    }

    /**
     * {@inheritDoc}
     * <p />
     * If the baseband is up-to-date, this flasher behaves identically to the DeviceFlasher
     * superclass.  If the baseband needs to be updated, it does the following:
     * <ol>
     *   <li>Flash the bootloader as normal</li>
     *   <li>Unpack the updater.zip</li>
     *   <li>Flash the new baseband, but <emph>don't reboot afterward</emph></li>
     *   <li>Flash the boot, recovery, and system partitions</li>
     *   <li>Reboot (device comes up in Recovery to actually flash baseband)</li>
     *   <li>Reboot again</li>
     *   <li>Flash userdata</li>
     *   <li>Reboot into userspace</li>
     * </ol>
     */
    @Override
    public void flash(ITestDevice device, IDeviceBuildInfo deviceBuild) throws TargetSetupError,
            DeviceNotAvailableException {

        Log.i(LOG_TAG, String.format("Flashing device %s with build %d",
                device.getSerialNumber(), deviceBuild.getBuildId()));

        // get system build id before booting into fastboot
        int systemBuildId = device.getBuildId();

        device.rebootIntoBootloader();

        downloadFlashingResources(device, deviceBuild);

        checkAndFlashBootloader(device, deviceBuild);
        if (checkShouldFlashBaseband(device, deviceBuild)) {
            Log.i(LOG_TAG, "Performing special CDMA baseband update flash procedure");
            mShouldFlashBaseband = true;
            eraseCache(device);
            checkAndFlashBaseband(device, deviceBuild);
            checkAndFlashSystem(device, systemBuildId, deviceBuild);
            flashUserData(device, deviceBuild);
            device.reboot();
        } else {
            // Do the standard thing
            flashUserData(device, deviceBuild);
            eraseCache(device);
            checkAndFlashSystem(device, systemBuildId, deviceBuild);
        }
    }

    /**
     * Flashes the given baseband image and <emph>does not reboot the device afterward</emph>.
     *
     * @param device the {@link ITestDevice} to flash
     * @param basebandImageFile the baseband image {@link File}
     * @throws DeviceNotAvailableException if device is not available
     * @throws TargetSetupError if failed to flash baseband
     */
    @Override
    protected void flashBaseband(ITestDevice device, File basebandImageFile)
            throws DeviceNotAvailableException, TargetSetupError {
        executeLongFastbootCmd(device, "flash", BASEBAND_IMAGE_NAME,
                basebandImageFile.getAbsolutePath());
    }

    /**
     * Flash an individual partition
     */
    protected void flashPartition(ITestDevice device, File dir, String partition)
            throws DeviceNotAvailableException, TargetSetupError {
        // TODO: move this method into DeviceFlasher
        String imgFile = dir.getAbsolutePath() + File.separator + partition + ".img";
        Log.v(LOG_TAG, String.format("fastboot flash %s %s", partition, imgFile));
        executeLongFastbootCmd(device, "flash", partition, imgFile);
    }

    /**
     * Extract the updater zip to a directory and return the path of that directory
     * <p />
     * Exposed for unit testing
     */
    protected File extractSystemZip(IDeviceBuildInfo deviceBuild) throws IOException {
        File updateDir = FileUtil.createTempDir(LOG_TAG);
        ZipFile updater = new ZipFile(deviceBuild.getDeviceImageFile().getAbsolutePath());
        FileUtil.extractZip(updater, updateDir);
        return updateDir;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void flashSystem(ITestDevice device, IDeviceBuildInfo deviceBuild)
            throws DeviceNotAvailableException, TargetSetupError {
        if (mShouldFlashBaseband) {
            // Unpack updater zip and flash partitions manually
            Log.i(LOG_TAG, String.format("MANUALLY flashing individual partitions on %s.",
                    device.getSerialNumber()));
            File updateDir = null;
            try {
                // unzip
                updateDir = extractSystemZip(deviceBuild);

                // Expect updateDir to contain boot.img, recovery.img, system.img
                flashPartition(device, updateDir, "boot");
                flashPartition(device, updateDir, "recovery");
                flashPartition(device, updateDir, "system");
            } catch (IOException e) {
                throw new TargetSetupError(String.format("Got IOException: %s", e.getMessage()));
            } finally {
                if (updateDir != null) {
                    FileUtil.recursiveDelete(updateDir);
                    updateDir = null;
                }
            }

            // Do the fancy double-reboot
            // Don't use device.reboot() the first time because radio flash can take 5+ minutes
            device.executeFastbootCommand("reboot");
            device.waitForDeviceOnline(BASEBAND_FLASH_TIMEOUT);
            device.waitForDeviceAvailable();
            // Wait for radio version updater to do its thing
            getRunUtil().sleep(5000);
            // Reboot again.
            device.reboot();
            // Wait for radio version updater to do its thing again
            getRunUtil().sleep(5000);
            // Hopefully, that should be it
            device.rebootIntoBootloader();

        } else {
            super.flashSystem(device, deviceBuild);
        }
    }

    /**
     * Get the {@link RunUtil} instance to use.
     * <p/>
     * Exposed for unit testing.
     */
    protected IRunUtil getRunUtil() {
        return RunUtil.getInstance();
    }
}

