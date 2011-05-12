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
package com.android.tradefed.build;

import java.io.File;
import java.util.List;

/**
 * A {@link IDeviceBuildInfo} that also contains a {@link IAppBuildInfo}.
 */
public class AppDeviceBuildInfo extends BuildInfo implements IDeviceBuildInfo, IAppBuildInfo {

    private IDeviceBuildInfo mDeviceBuild;
    private IAppBuildInfo mAppBuildInfo;

    /**
     * @see {@link DeviceBuildInfo#DeviceBuildInfo(String, String, String)}
     */
    public AppDeviceBuildInfo(String buildId, String testTarget, String buildName) {
        super(buildId, testTarget, buildName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public File getBasebandImageFile() {
        return mDeviceBuild.getBasebandImageFile();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getBasebandVersion() {
        return mDeviceBuild.getBasebandVersion();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public File getBootloaderImageFile() {
        return mDeviceBuild.getBootloaderImageFile();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getBootloaderVersion() {
        return mDeviceBuild.getBootloaderVersion();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public File getDeviceImageFile() {
        return mDeviceBuild.getDeviceImageFile();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public File getImageFile(String imageName) {
        return mDeviceBuild.getImageFile(imageName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getImageVersion(String imageName) {
        return mDeviceBuild.getImageVersion(imageName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public File getTestsDir() {
        return mDeviceBuild.getTestsDir();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public File getUserDataImageFile() {
        return mDeviceBuild.getUserDataImageFile();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setBasebandImage(File basebandFile, String version) {
        mDeviceBuild.setBasebandImage(basebandFile, version);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setBootloaderImageFile(File bootloaderImgFile, String version) {
        mDeviceBuild.setBootloaderImageFile(bootloaderImgFile, version);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setDeviceImageFile(File deviceImageFile) {
        mDeviceBuild.setDeviceImageFile(deviceImageFile);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setImageFile(String imageName, File file, String version) {
        mDeviceBuild.setImageFile(imageName, file, version);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setTestsDir(File testsDir) {
        mDeviceBuild.setTestsDir(testsDir);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setUserDataImageFile(File userDataFile) {
        mDeviceBuild.setUserDataImageFile(userDataFile);
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
    public File getOtaPackageFile() {
        return mDeviceBuild.getOtaPackageFile();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setOtaPackageFile(File otaFile) {
        mDeviceBuild.setOtaPackageFile(otaFile);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void cleanUp() {
        mDeviceBuild.cleanUp();
        mAppBuildInfo.cleanUp();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addAppPackageFile(File appPackageFile) {
        mAppBuildInfo.addAppPackageFile(appPackageFile);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<File> getAppPackageFiles() {
        return mAppBuildInfo.getAppPackageFiles();
    }

    /**
     * @param appBuild
     */
    public void setAppBuild(IAppBuildInfo appBuild) {
        mAppBuildInfo = appBuild;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IBuildInfo clone() {
        AppDeviceBuildInfo copy = new AppDeviceBuildInfo(getBuildId(), getTestTag(),
                getBuildTargetName());
        copy.addAllBuildAttributes(this);
        IDeviceBuildInfo deviceBuildClone = (IDeviceBuildInfo)mDeviceBuild.clone();
        copy.setDeviceBuild(deviceBuildClone);
        IAppBuildInfo appBuildClone = (IAppBuildInfo)mAppBuildInfo.clone();
        copy.setAppBuild(appBuildClone);
        return copy;
    }
}
