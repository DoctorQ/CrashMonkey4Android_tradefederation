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
 * A {@link IDeviceBuildInfo} that also contains other build artifacts contained in a directory on
 * the local filesystem.
 */
public class DeviceFolderBuildInfo extends BuildInfo implements IDeviceBuildInfo, IFolderBuildInfo {

    private IDeviceBuildInfo mDeviceBuild;
    private IFolderBuildInfo mFolderBuildInfo;

    /**
     * @see {@link DeviceBuildInfo#DeviceBuildInfo(int, String, String)}
     */
    public DeviceFolderBuildInfo(int buildId, String testTarget, String buildName) {
        super(buildId, testTarget, buildName);
    }

    /**
     * {@inheritDoc}
     */
    public File getBasebandImageFile() {
        return mDeviceBuild.getBasebandImageFile();
    }

    /**
     * {@inheritDoc}
     */
    public String getBasebandVersion() {
        return mDeviceBuild.getBasebandVersion();
    }

    /**
     * {@inheritDoc}
     */
    public File getBootloaderImageFile() {
        return mDeviceBuild.getBootloaderImageFile();
    }

    /**
     * {@inheritDoc}
     */
    public String getBootloaderVersion() {
        return mDeviceBuild.getBootloaderVersion();
    }

    /**
     * {@inheritDoc}
     */
    public File getDeviceImageFile() {
        return mDeviceBuild.getDeviceImageFile();
    }

    /**
     * {@inheritDoc}
     */
    public File getImageFile(String imageName) {
        return mDeviceBuild.getImageFile(imageName);
    }

    /**
     * {@inheritDoc}
     */
    public String getImageVersion(String imageName) {
        return mDeviceBuild.getImageVersion(imageName);
    }

    /**
     * {@inheritDoc}
     */
    public File getTestsZipFile() {
        return mDeviceBuild.getTestsZipFile();
    }

    /**
     * {@inheritDoc}
     */
    public File getUserDataImageFile() {
        return mDeviceBuild.getUserDataImageFile();
    }

    /**
     * {@inheritDoc}
     */
    public void setBasebandImage(File basebandFile, String version) {
        mDeviceBuild.setBasebandImage(basebandFile, version);
    }

    /**
     * {@inheritDoc}
     */
    public void setBootloaderImageFile(File bootloaderImgFile, String version) {
        mDeviceBuild.setBootloaderImageFile(bootloaderImgFile, version);
    }

    /**
     * {@inheritDoc}
     */
    public void setDeviceImageFile(File deviceImageFile) {
        mDeviceBuild.setDeviceImageFile(deviceImageFile);
    }

    /**
     * {@inheritDoc}
     */
    public void setImageFile(String imageName, File file, String version) {
        mDeviceBuild.setImageFile(imageName, file, version);
    }

    /**
     * {@inheritDoc}
     */
    public void setTestsZipFile(File testsZipFile) {
        mDeviceBuild.setTestsZipFile(testsZipFile);
    }

    /**
     * {@inheritDoc}
     */
    public void setUserDataImageFile(File userDataFile) {
        mDeviceBuild.setUserDataImageFile(userDataFile);
    }

    /**
     * {@inheritDoc}
     */
    public File getRootDir() {
        return mFolderBuildInfo.getRootDir();
    }

    /**
     * {@inheritDoc}
     */
    public void setRootDir(File rootDir) {
        mFolderBuildInfo.setRootDir(rootDir);
    }

    /**
     * @param localBuild
     */
    public void setCtsBuild(IFolderBuildInfo folderBuild) {
        mFolderBuildInfo = folderBuild;
    }

    /**
     * @param deviceBuild
     */
    public void setDeviceBuild(IDeviceBuildInfo deviceBuild) {
        mDeviceBuild = deviceBuild;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void cleanUp() {
        mDeviceBuild.cleanUp();
        mFolderBuildInfo.cleanUp();
    }
}
