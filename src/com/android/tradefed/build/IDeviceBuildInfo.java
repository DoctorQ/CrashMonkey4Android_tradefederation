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

package com.android.tradefed.build;

import java.io.File;

/**
 * A {@link IBuildInfo} that represents a complete Android device build and (optionally) its tests.
 */
public interface IDeviceBuildInfo extends IBuildInfo {

    /**
     * Helper method to retrieve a file with given name.
     * @param name
     * @return the image file or <code>null</code> if not found
     */
    public File getFile(String name);

    /**
     * Helper method to retrieve a file version with given name.
     * @param name
     * @return the image version or <code>null</code> if not found
     */
    public String getVersion(String name);

    /**
     * Stores an file with given name in this build info
     *
     * @param name the unique name of the file
     * @param file the local {@link File}
     * @param version the file version
     */
    public void setFile(String name, File file, String version);

    /**
     * Get the local device image zip file.
     */
    public File getDeviceImageFile();

    /**
     * Set the device system image file to use.
     *
     * @param deviceImageFile
     */
    public void setDeviceImageFile(File deviceImageFile);

    /**
     * Get the local test userdata image file.
     */
    public File getUserDataImageFile();

    /**
     * Set the user data image file to use.
     *
     * @param userDataFile
     */
    public void setUserDataImageFile(File userDataFile);

    /**
     * Get the local path to the extracted tests.zip file contents.
     */
    public File getTestsDir();

    /**
     * Set local path to the extracted tests.zip file contents.
     *
     * @param testsZipFile
     */
    public void setTestsDir(File testsZipFile);

    /**
     * Get the local baseband image file.
     */
    public File getBasebandImageFile();

    /**
     * Get the baseband version.
     */
    public String getBasebandVersion();

    /**
     * Set the baseband image for the device build.
     *
     * @param basebandFile the baseband image {@link File}
     * @param version the version of the baseband
     */
    public void setBasebandImage(File basebandFile, String version);

    /**
     * Get the local bootloader image file.
     */
    public File getBootloaderImageFile();

    /**
     * Get the bootloader version.
     */
    public String getBootloaderVersion();

    /**
     * Set the bootloader image for the device build.
     *
     * @param bootloaderImgFile the bootloader image {@link File}
     * @param version the version of the bootloader
     */
    public void setBootloaderImageFile(File bootloaderImgFile, String version);

    /**
     * Get the device OTA package zip file
     */
    public File getOtaPackageFile();

    /**
     * Set the device OTA package zip file
     */
    public void setOtaPackageFile(File otaFile);

    /**
     * Removes all temporary files
     */
    @Override
    public void cleanUp();

}
