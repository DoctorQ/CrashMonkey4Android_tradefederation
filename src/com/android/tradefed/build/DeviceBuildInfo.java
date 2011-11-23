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

    private Map<String, VersionedFile> mVersionedFileMap;

    private static final String DEVICE_IMAGE_NAME = "device";
    private static final String USERDATA_IMAGE_NAME = "userdata";
    private static final String TESTDIR_IMAGE_NAME = "testsdir";
    private static final String BASEBAND_IMAGE_NAME = "baseband";
    private static final String BOOTLOADER_IMAGE_NAME = "bootloader";
    private static final String OTA_IMAGE_NAME = "ota";

    /**
     * Data structure containing the image file and related metadata
     */
    private static class VersionedFile {
        private final File mFile;
        private final String mVersion;

        VersionedFile(File file, String version) {
            mFile = file;
            mVersion = version;
        }

        File getFile() {
            return mFile;
        }

        String getVersion() {
            return mVersion;
        }
    }

    public DeviceBuildInfo() {
        mVersionedFileMap = new Hashtable<String, VersionedFile>();
    }

    /**
     * Creates a {@link DeviceBuildInfo}.
     *
     * @param buildId the unique build id
     * @param testTarget the test target name
     * @param buildName the build name
     */
    public DeviceBuildInfo(String buildId, String testTarget, String buildName) {
        super(buildId, testTarget, buildName);
        mVersionedFileMap = new Hashtable<String, VersionedFile>();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public File getFile(String name) {
        VersionedFile fileRecord = mVersionedFileMap.get(name);
        if (fileRecord != null) {
            return fileRecord.getFile();
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getVersion(String name) {
        VersionedFile fileRecord = mVersionedFileMap.get(name);
        if (fileRecord != null) {
            return fileRecord.getVersion();
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setFile(String name, File file, String version) {
        if (mVersionedFileMap.containsKey(name)) {
            Log.e(LOG_TAG, String.format(
                    "Device build already contains a file for %s in thread %s", name,
                    Thread.currentThread().getName()));
            return;
        }
        mVersionedFileMap.put(name, new VersionedFile(file, version));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public File getDeviceImageFile() {
        return getFile(DEVICE_IMAGE_NAME);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setDeviceImageFile(File deviceImageFile) {
        setFile(DEVICE_IMAGE_NAME, deviceImageFile, getBuildId());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public File getUserDataImageFile() {
        return getFile(USERDATA_IMAGE_NAME);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setUserDataImageFile(File userDataFile) {
        setFile(USERDATA_IMAGE_NAME, userDataFile, getBuildId());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public File getTestsDir() {
        return getFile(TESTDIR_IMAGE_NAME);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setTestsDir(File testsDir) {
        setFile(TESTDIR_IMAGE_NAME, testsDir, getBuildId());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public File getBasebandImageFile() {
        return getFile(BASEBAND_IMAGE_NAME);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getBasebandVersion() {
        return getVersion(BASEBAND_IMAGE_NAME);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setBasebandImage(File basebandFile, String version) {
        setFile(BASEBAND_IMAGE_NAME, basebandFile, version);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public File getBootloaderImageFile() {
        return getFile(BOOTLOADER_IMAGE_NAME);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getBootloaderVersion() {
        return getVersion(BOOTLOADER_IMAGE_NAME);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setBootloaderImageFile(File bootloaderImgFile, String version) {
        setFile(BOOTLOADER_IMAGE_NAME, bootloaderImgFile, version);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public File getOtaPackageFile() {
        return getFile(OTA_IMAGE_NAME);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setOtaPackageFile(File otaFile) {
        setFile(OTA_IMAGE_NAME, otaFile, getBuildId());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void cleanUp() {
        for (VersionedFile fileRecord : mVersionedFileMap.values()) {
            FileUtil.recursiveDelete(fileRecord.getFile());
        }
        mVersionedFileMap.clear();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IBuildInfo clone()  {
        try {
            DeviceBuildInfo copy = new DeviceBuildInfo(getBuildId(), getTestTag(),
                    getBuildTargetName());
            copy.addAllBuildAttributes(this);
            for (Map.Entry<String, VersionedFile> fileEntry : mVersionedFileMap.entrySet()) {
                File origFile = fileEntry.getValue().getFile();
                File hardlinkFile;
                if (origFile.isDirectory()) {
                    hardlinkFile = FileUtil.createTempDir(fileEntry.getKey());
                    FileUtil.recursiveHardlink(origFile, hardlinkFile);
                } else {
                    // Only using createTempFile to create a unique dest filename
                    hardlinkFile = FileUtil.createTempFile(fileEntry.getKey(),
                            FileUtil.getExtension(origFile.getName()));
                    hardlinkFile.delete();
                    FileUtil.hardlinkFile(origFile, hardlinkFile);
                }
                copy.mVersionedFileMap.put(fileEntry.getKey(), new VersionedFile(hardlinkFile,
                             fileEntry.getValue().getVersion()));
            }
            return copy;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
