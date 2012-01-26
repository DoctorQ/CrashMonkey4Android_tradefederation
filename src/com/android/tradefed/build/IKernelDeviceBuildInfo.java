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
public interface IKernelDeviceBuildInfo extends IBuildInfo {

    /**
     * Gets the mkbootimg file used to create the kernel image.
     *
     * @return the mkbootimg file.
     */
    public File getMkbootimgFile();

    /**
     * Sets the mkbootimg file used to create the kernel image.
     *
     * @param mkbootimg the mkbootimg file.
     */
    public void setMkbootimgFile(File mkbootimg);

    /**
     * Gets the ramdisk image used to create the kernel image.
     *
     * @return the ramdisk image.
     */
    public File getRamdiskFile();

    /**
     * Gets the ramdisk file used to create the kernel image.
     *
     * @param ramdisk the ramdisk image.
     */
    public void setRamdiskFile(File ramdisk);

    /**
     * Gets the {@link IDeviceBuildInfo} object representing the complete Android build.
     *
     * @return the {@link IDeviceBuildInfo} object
     */
    public IDeviceBuildInfo getDeviceBuildInfo();

    /**
     * Gets the {@link IKernelBuildInfo} object representing the kernel build.
     *
     * @return the {@link IKernelBuildInfo} object
     */
    public IKernelBuildInfo getKernelBuildInfo();
}
