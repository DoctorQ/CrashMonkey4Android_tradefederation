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

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;

/**
 * A wrapper class for a {@link IBuildInfo}, that contains helper methods to retrieve device
 * platform build information.
 * <p/>
 * Intended to be use for "unbundled" aka not device builds {@link IBuildInfo}, that desire
 * metadata about what device the build was run on.
 */
public class DeviceBuildDescriptor {

    private static final String DEVICE_BUILD_ID = "device_build_id";
    private static final String DEVICE_PRODUCT = "device_product_name";
    private static final String DEVICE_BUILD_TYPE = "device_build_type";

    private final IBuildInfo mBuild;

    public DeviceBuildDescriptor(IBuildInfo build) {
        mBuild = build;
    }

    /**
     * Determines if given {@link IBuildInfo} contains device build metadata
     *
     * @param build
     * @return
     */
    public static boolean describesDeviceBuild(IBuildInfo build) {
        return build.getBuildAttributes().containsKey(DEVICE_BUILD_ID);
    }

    /**
     * Gets the device build ID.
     */
    public String getDeviceBuildId() {
        return mBuild.getBuildAttributes().get(DEVICE_BUILD_ID);
    }

    /**
     * Gets the device product name.
     */
    public String getProductName() {
        return mBuild.getBuildAttributes().get(DEVICE_PRODUCT);
    }

    /**
     * Gets the device build type. eg typically one of userdebug, user, eng
     */
    public String getType() {
        return mBuild.getBuildAttributes().get(DEVICE_BUILD_TYPE);
    }

    /**
     * Inserts attributes from device into build.
     *
     * @param device
     * @throws DeviceNotAvailableException
     */
    public static void injectDeviceAttributes(ITestDevice device, IBuildInfo b)
            throws DeviceNotAvailableException {
        b.addBuildAttribute(DEVICE_BUILD_ID, device.getProperty("ro.build.id"));
        b.addBuildAttribute(DEVICE_PRODUCT, device.getProperty("ro.product.name"));
        b.addBuildAttribute(DEVICE_BUILD_TYPE, device.getProperty("ro.build.type"));
    }
}
