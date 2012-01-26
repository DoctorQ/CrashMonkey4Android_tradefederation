/*
 * Copyright (C) 2012 The Android Open Source Project
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
 * A {@link IBuildInfo} that represents a kernel build paired with a complete Android build.
 */
public class KernelDeviceBuildInfo extends BuildInfo implements IKernelDeviceBuildInfo {
    private static final String RAMDISK_NAME = "ramdisk";
    private static final String MKBOOTIMG_NAME = "mkbootimg";

    private IDeviceBuildInfo mDeviceBuild;
    private IKernelBuildInfo mKernelBuild;

    /**
     * Creates a  {@link KernelDeviceBuildInfo}.
     *
     * @param deviceBuild the {@link IDeviceBuildInfo} object.
     * @param kernelBuild the {@link IKernelBuildInfo} object.
     */
    public KernelDeviceBuildInfo(IDeviceBuildInfo deviceBuild, IKernelBuildInfo kernelBuild) {
        this(deviceBuild, kernelBuild, null, null);
    }

    /**
     * Creates a  {@link KernelDeviceBuildInfo}.
     *
     * @param deviceBuild the {@link IDeviceBuildInfo} object.
     * @param kernelBuild the {@link IKernelBuildInfo} object.
     * @param testTarget the test target
     * @param buildName the buildName
     */
    public KernelDeviceBuildInfo(IDeviceBuildInfo deviceBuild, IKernelBuildInfo kernelBuild,
            String testTarget, String buildName) {
        // NPE will be thrown when calling super if deviceBuild or kernelBuild are null.
        super(String.format("%s_%s", kernelBuild.getBuildId(), deviceBuild.getBuildId()),
                testTarget, buildName);

        mDeviceBuild = deviceBuild;
        mKernelBuild = kernelBuild;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public File getMkbootimgFile() {
        return mDeviceBuild.getFile(MKBOOTIMG_NAME);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setMkbootimgFile(File mkbootimg) {
        mDeviceBuild.setFile(MKBOOTIMG_NAME, mkbootimg, getBuildId());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public File getRamdiskFile() {
        return mDeviceBuild.getFile(RAMDISK_NAME);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setRamdiskFile(File ramdisk) {
        mDeviceBuild.setFile(RAMDISK_NAME, ramdisk, getBuildId());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IDeviceBuildInfo getDeviceBuildInfo() {
        return mDeviceBuild;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IKernelBuildInfo getKernelBuildInfo() {
        return mKernelBuild;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void cleanUp() {
        mDeviceBuild.cleanUp();
        mKernelBuild.cleanUp();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IKernelDeviceBuildInfo clone() {
        KernelDeviceBuildInfo copy = new KernelDeviceBuildInfo(
                (IDeviceBuildInfo) mDeviceBuild.clone(), (IKernelBuildInfo) mKernelBuild.clone(),
                getTestTag(), getBuildTargetName());
        copy.addAllBuildAttributes(this);

        return copy;
    }
}
