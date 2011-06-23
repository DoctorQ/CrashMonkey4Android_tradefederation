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

import com.android.ddmlib.Log;
import com.android.tradefed.util.FileUtil;

import java.io.File;
import java.io.IOException;
import java.util.Hashtable;
import java.util.Map;

/**
 * A {@link IBuildInfo} that represents a complete Android device build and (optionally) its tests.
 */
public class DeviceBuildInfo extends BuildInfo implements IDeviceBuildInfo {
    private static final String LOG_TAG = "DeviceBuildInfo";

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

    public DeviceBuildInfo() {
        mImageFileMap = new Hashtable<String, ImageFile>();
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
     * {@inheritDoc}
     */
    public File getImageFile(String imageName) {
        ImageFile imgFileRecord = mImageFileMap.get(imageName);
        if (imgFileRecord != null) {
            return imgFileRecord.getImageFile();
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    public String getImageVersion(String imageName) {
        ImageFile imgFileRecord = mImageFileMap.get(imageName);
        if (imgFileRecord != null) {
            return imgFileRecord.getVersion();
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    public void setImageFile(String imageName, File file, String version) {
        if (mImageFileMap.containsKey(imageName)) {
            Log.e(LOG_TAG, String.format(
                    "Device build already contains an image for %s in thread %s", imageName,
                    Thread.currentThread().getName()));
            return;
        }
        mImageFileMap.put(imageName, new ImageFile(file, version));
    }


    /**
     * {@inheritDoc}
     */
    public File getDeviceImageFile() {
        return getImageFile(DEVICE_IMAGE_NAME);
    }

    /**
     * {@inheritDoc}
     */
    public void setDeviceImageFile(File deviceImageFile) {
        setImageFile(DEVICE_IMAGE_NAME, deviceImageFile, Integer.toString(getBuildId()));
    }

    /**
     * {@inheritDoc}
     */
    public File getUserDataImageFile() {
        return getImageFile(USERDATA_IMAGE_NAME);
    }

    /**
     * {@inheritDoc}
     */
    public void setUserDataImageFile(File userDataFile) {
        setImageFile(USERDATA_IMAGE_NAME, userDataFile, Integer.toString(getBuildId()));
    }

    /**
     * {@inheritDoc}
     */
    public File getTestsZipFile() {
        return getImageFile(TESTZIP_IMAGE_NAME);
    }

    /**
     * {@inheritDoc}
     */
    public void setTestsZipFile(File testsZipFile) {
        setImageFile(TESTZIP_IMAGE_NAME, testsZipFile, Integer.toString(getBuildId()));
    }

    /**
     * {@inheritDoc}
     */
    public File getBasebandImageFile() {
        return getImageFile(BASEBAND_IMAGE_NAME);
    }

    /**
     * {@inheritDoc}
     */
    public String getBasebandVersion() {
        return getImageVersion(BASEBAND_IMAGE_NAME);
    }

    /**
     * {@inheritDoc}
     */
    public void setBasebandImage(File basebandFile, String version) {
        setImageFile(BASEBAND_IMAGE_NAME, basebandFile, version);
    }

    /**
     * {@inheritDoc}
     */
    public File getBootloaderImageFile() {
        return getImageFile(BOOTLOADER_IMAGE_NAME);
    }

    /**
     * {@inheritDoc}
     */
    public String getBootloaderVersion() {
        return getImageVersion(BOOTLOADER_IMAGE_NAME);
    }

    /**
     * {@inheritDoc}
     */
    public void setBootloaderImageFile(File bootloaderImgFile, String version) {
        setImageFile(BOOTLOADER_IMAGE_NAME, bootloaderImgFile, version);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void cleanUp() {
        for (ImageFile fileRecord : mImageFileMap.values()) {
            fileRecord.getImageFile().delete();
        }
        mImageFileMap.clear();
    }

    @Override
    public IBuildInfo clone()  {
        try {
            DeviceBuildInfo copy = new DeviceBuildInfo(getBuildId(), getTestTarget(),
                    getBuildName());
            copy.addAllBuildAttributes(getAttributesMultiMap());
            for (Map.Entry<String, ImageFile> fileEntry : mImageFileMap.entrySet()) {
                File origImageFile = fileEntry.getValue().getImageFile();
                File fileCopy = FileUtil.createTempFile(fileEntry.getKey(),
                        FileUtil.getExtension(origImageFile.getName()));
                FileUtil.copyFile(origImageFile, fileCopy);
                copy.mImageFileMap.put(fileEntry.getKey(), new ImageFile(fileCopy,
                        fileEntry.getValue().getVersion()));
            }
            return copy;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Override finalize to determine when a DeviceBuildInfo is being destroyed without having been
     * cleaned up.  This is temporary as we try to hunt down file leaks.
     */
    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        String name = String.format("%s[%s]", LOG_TAG, Thread.currentThread().getName());
        Log.d(LOG_TAG, String.format("%s in finalizer", name));
        if (!mImageFileMap.isEmpty()) {
            Log.e(LOG_TAG, String.format("%s was not cleaned up: %s", name,
                    mImageFileMap.toString()));
            cleanUp();
        }
    }
}
