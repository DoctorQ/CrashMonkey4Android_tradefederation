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


import java.io.File;
import java.util.Hashtable;
import java.util.Map;

/**
 * A {@link IBuildInfo} that represents a complete Android device build and (optionally) its tests.
 */
public class DeviceBuildInfo extends BuildInfo {

    private Map<String, ImageFile> mImageFileMap;

    private static final String DEVICE_IMAGE_NAME = "device";
    private static final String USERDATA_IMAGE_NAME = "userdata";
    private static final String TESTZIP_IMAGE_NAME = "testszip";
    private static final String BASEBAND_IMAGE_NAME = "baseband";
    private static final String BOOTLOADER_IMAGE_NAME = "bootloader";

    /**
     * Data structure containing the image file and related metadata
     */
    private static class ImageFile {
        private final File mImageFile;
        private final String mVersion;

        ImageFile(File imageFile, String version) {
            mImageFile = imageFile;
            mVersion = version;
        }

        File getImageFile() {
            return mImageFile;
        }

        String getVersion() {
            return mVersion;
        }
    }

    /**
     * Creates a {@link DeviceBuildInfo}.
     *
     * @param buildId the unique build id
     * @param testTarget the test target name
     * @param buildName the build name
     */
    public DeviceBuildInfo(int buildId, String testTarget, String buildName) {
        super(buildId, testTarget, buildName);
        mImageFileMap = new Hashtable<String, ImageFile>();
    }

    /**
     * Helper method to retrieve an image file with given name.
     * @param imageName
     * @return the image file or <code>null</code> if not found
     */
    public File getImageFile(String imageName) {
        ImageFile imgFileRecord = mImageFileMap.get(imageName);
        if (imgFileRecord != null) {
            return imgFileRecord.getImageFile();
        }
        return null;
    }

    /**
     * Helper method to retrieve an image file version with given name.
     * @param imageName
     * @return the image version or <code>null</code> if not found
     */
    public String getImageVersion(String imageName) {
        ImageFile imgFileRecord = mImageFileMap.get(imageName);
        if (imgFileRecord != null) {
            return imgFileRecord.getVersion();
        }
        return null;
    }

    /**
     * Stores an image file with given name in this build info
     *
     * @param imageName the unique name of the image
     * @param file the local image {@link File}
     * @param version the image file version
     */
    public void setImageFile(String imageName, File file, String version) {
        mImageFileMap.put(imageName, new ImageFile(file, version));
    }


    /**
     * Get the local device image zip file.
     */
    public File getDeviceImageFile() {
        return getImageFile(DEVICE_IMAGE_NAME);
    }

    public void setDeviceImageFile(File deviceImageFile) {
        setImageFile(DEVICE_IMAGE_NAME, deviceImageFile, Integer.toString(getBuildId()));
    }

    /**
     * Get the local test userdata image file.
     */
    public File getUserDataImageFile() {
        return getImageFile(USERDATA_IMAGE_NAME);
    }

    public void setUserDataImageFile(File userDataFile) {
        setImageFile(USERDATA_IMAGE_NAME, userDataFile, Integer.toString(getBuildId()));
    }

    /**
     * Get the local tests zip file.
     */
    public File getTestsZipFile() {
        return getImageFile(TESTZIP_IMAGE_NAME);
    }

    public void setTestsZipFile(File testsZipFile) {
        setImageFile(TESTZIP_IMAGE_NAME, testsZipFile, Integer.toString(getBuildId()));
    }

    /**
     * Get the local baseband image file.
     */
    public File getBasebandImageFile() {
        return getImageFile(BASEBAND_IMAGE_NAME);
    }

    /**
     * Get the baseband version.
     */
    public String getBasebandVersion() {
        return getImageVersion(BASEBAND_IMAGE_NAME);
    }

    /**
     * Set the baseband image for the device build.
     *
     * @param basebandFile the baseband image {@link File}
     * @param version the version of the baseband
     */
    public void setBasebandImage(File basebandFile, String version) {
        setImageFile(BASEBAND_IMAGE_NAME, basebandFile, version);
    }

    /**
     * Get the local bootloader image file.
     */
    public File getBootloaderImageFile() {
        return getImageFile(BOOTLOADER_IMAGE_NAME);
    }

    /**
     * Get the bootloader version.
     */
    public String getBootloaderVersion() {
        return getImageVersion(BOOTLOADER_IMAGE_NAME);
    }

    /**
     * Set the bootloader image for the device build.
     *
     * @param bootloaderImgFile the bootloader image {@link File}
     * @param version the version of the bootloader
     */
    public void setBootloaderImageFile(File bootloaderImgFile, String version) {
        setImageFile(BOOTLOADER_IMAGE_NAME, bootloaderImgFile, version);
    }

    /**
     * Removes all temporary files
     */
    public void cleanUp() {
        for (ImageFile fileRecord : mImageFileMap.values()) {
            fileRecord.getImageFile().delete();
        }
        mImageFileMap.clear();
    }
}
