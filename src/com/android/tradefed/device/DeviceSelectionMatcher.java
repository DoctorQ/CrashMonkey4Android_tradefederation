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
package com.android.tradefed.device;

import com.android.ddmlib.IDevice;

import java.util.Collection;

/**
 * Helper class to determine if a given {@link IDevice} matches a
 * {@link DeviceSelectionOptions}.
 */
public class DeviceSelectionMatcher {

    /**
     * @return <code>true</code> if provided <var>deviceOptions</var> matches <var>device</var>.
     * <code>false> otherwise
     */
    public static boolean matches(IDevice device, DeviceSelectionOptions deviceOptions) {
        Collection<String> serials = deviceOptions.getSerials();
        Collection<String> excludeSerials = deviceOptions.getExcludeSerials();
        Collection<String> productTypes = deviceOptions.getProductTypes();

        if (!serials.isEmpty() &&
                !serials.contains(device.getSerialNumber())) {
            return false;
        }
        if (excludeSerials.contains(device.getSerialNumber())) {
            return false;
        }
        if (!productTypes.isEmpty() &&
                !productTypes.contains(getDeviceProductType(device))) {
            return false;
        }
        return true;
    }

    private static String getDeviceProductType(IDevice device) {
        String type = device.getProperty("ro.product.board");
        if(type == null || type.isEmpty()) {
            // last-chance fallback to ro.product.device, which may be set if ro.product.board isn't
            type = device.getProperty("ro.product.device");
        }
        return type;
    }
}
