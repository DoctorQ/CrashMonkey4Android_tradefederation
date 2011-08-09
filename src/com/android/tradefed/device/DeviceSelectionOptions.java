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
import com.android.ddmlib.Log;
import com.android.tradefed.config.Option;
import com.android.tradefed.log.LogUtil.CLog;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/**
 * Container for for device selection criteria.
 */
public class DeviceSelectionOptions implements IDeviceSelectionOptions {

    private static final String LOG_TAG = "DeviceSelectionOptions";

    @Option(name = "serial", shortName = 's', description =
        "run this test on a specific device with given serial number(s).")
    private Collection<String> mSerials = new ArrayList<String>();

    @Option(name = "exclude-serial", description =
        "run this test on any device except those with this serial number(s).")
    private Collection<String> mExcludeSerials = new ArrayList<String>();

    @Option(name = "product-type", description =
            "run this test on device with this product type(s).  May also filter by variant " +
            "using product:variant.")
    private Collection<String> mProductTypes = new ArrayList<String>();

    @Option(name = "property", description =
        "run this test on device with this property value. " +
        "Expected format <propertyname>=<propertyvalue>.")
    private Collection<String> mPropertyStrings = new ArrayList<String>();

    @Option(name = "emulator", shortName = 'e', description =
        "run this test on emulator.")
    private boolean mEmulatorRequested = false;

    @Option(name = "null-device", shortName = 'n', description =
        "do not allocate a device for this test.")
    private boolean mNullDeviceRequested = false;

    // If we have tried to fetch the environment variable ANDROID_SERIAL before.
    private boolean mFetchedEnvVariable = false;

    private static final String VARIANT_SEPARATOR = ":";

    /**
     * Add a serial number to the device selection options.
     *
     * @param serialNumber
     */
    public void addSerial(String serialNumber) {
        mSerials.add(serialNumber);
    }

    /**
     * Add a serial number to exclusion list.
     *
     * @param serialNumber
     */
    public void addExcludeSerial(String serialNumber) {
        mExcludeSerials.add(serialNumber);
    }

    /**
     * Add a product type to the device selection options.
     *
     * @param serialNumber
     */
    public void addProductType(String productType) {
        mProductTypes.add(productType);
    }

    /**
     * Add a property criteria to the device selection options
     *
     * @param propertyKeyValue a property to match. Expected format propertykey=propertyvalue
     */
    public void addProperty(String propertyKeyValue) {
        mPropertyStrings.add(propertyKeyValue);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<String> getSerials() {
        // If no serial was explicitly set, use the environment variable ANDROID_SERIAL.
        if (mSerials.isEmpty() && !mFetchedEnvVariable) {
            String env_serial = fetchEnvironmentVariable("ANDROID_SERIAL");
            if (env_serial != null) {
                mSerials.add(env_serial);
            }
            mFetchedEnvVariable = true;
        }
        return copyCollection(mSerials);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<String> getExcludeSerials() {
        return copyCollection(mExcludeSerials);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<String> getProductTypes() {
        return copyCollection(mProductTypes);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean emulatorRequested() {
        return mEmulatorRequested;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean nullDeviceRequested() {
        return mNullDeviceRequested;
    }

    /**
     * Sets the emulator requested flag
     */
    public void setEmulatorRequested(boolean emulatorRequested) {
        mEmulatorRequested = emulatorRequested;
    }

    /**
     * Sets the null device requested flag
     */
    public void setNullDeviceRequested(boolean nullDeviceRequested) {
        mNullDeviceRequested = nullDeviceRequested;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, String> getProperties() {
        Map<String, String> propertyMap = new HashMap<String, String>(mPropertyStrings.size());
        for (String propertyKeyValue : mPropertyStrings) {
            String[] keyValuePair =  propertyKeyValue.split("=");
            if (keyValuePair.length == 2) {
                propertyMap.put(keyValuePair[0], keyValuePair[1]);
            } else {
                Log.e(LOG_TAG, String.format("Unrecognized property key value pair: '%s'",
                        propertyKeyValue));
            }
        }
        return propertyMap;
    }

    private Collection<String> copyCollection(Collection<String> original) {
        Collection<String> listCopy = new ArrayList<String>(original.size());
        listCopy.addAll(original);
        return listCopy;
    }

    /**
     * Helper function used to fetch environment variable. It is essentially a wrapper around
     * {@link System#getenv(String)} This is done for unit testing purposes.
     *
     * @param name the environment variable to fetch.
     * @return a {@link String} value of the environment variable or null if not available.
     */
    String fetchEnvironmentVariable(String name) {
        return System.getenv(name);
    }

    /**
     * @return <code>true</code> if the given {@link IDevice} is a match for the provided options.
     * <code>false</code> otherwise
     */
    @Override
    public boolean matches(IDevice device) {
        Collection<String> serials = getSerials();
        Collection<String> excludeSerials = getExcludeSerials();
        Map<String, Collection<String>> productVariants = splitOnVariant(getProductTypes());
        Collection<String> productTypes = productVariants.keySet();
        Map<String, String> properties = getProperties();

        if (!serials.isEmpty() &&
                !serials.contains(device.getSerialNumber())) {
            return false;
        }
        if (excludeSerials.contains(device.getSerialNumber())) {
            return false;
        }
        if (!productTypes.isEmpty()) {
            String productType = getDeviceProductType(device);
            if (productTypes.contains(productType)) {
                // check variant
                String productVariant = getDeviceProductVariant(device);
                Collection<String> variants = productVariants.get(productType);
                if (variants != null && !variants.contains(productVariant)) {
                    return false;
                }
            } else {
                // no product type matches; bye-bye
                return false;
            }
        }
        for (Map.Entry<String, String> propEntry : properties.entrySet()) {
            if (!propEntry.getValue().equals(device.getProperty(propEntry.getKey()))) {
                return false;
            }
        }
        if (emulatorRequested() != device.isEmulator()) {
            // only match with emulator if explicitly requested
            return false;
        }
        if (nullDeviceRequested() != (device instanceof NullDevice)) {
            return false;
        }

        return true;
    }

    private Map<String, Collection<String>> splitOnVariant(Collection<String> products) {
        // FIXME: we should validate all provided device selection options once, on the first
        // FIXME: call to #matches
        Map<String, Collection<String>> splitProducts =
                new HashMap<String, Collection<String>>(products.size());
        // FIXME: cache this
        for (String prod : products) {
            String[] parts = prod.split(VARIANT_SEPARATOR);
            if (parts.length == 1) {
                splitProducts.put(parts[0], null);
            } else if (parts.length == 2) {
                // A variant was specified as product:variant
                Collection<String> variants = splitProducts.get(parts[0]);
                if (variants == null) {
                    variants = new HashSet<String>();
                    splitProducts.put(parts[0], variants);
                }
                variants.add(parts[1]);
            } else {
                throw new IllegalArgumentException(String.format("The product type filter \"%s\" " +
                "is invalid.  It must contain 0 or 1 '%s' characters, not %d.",
                prod, VARIANT_SEPARATOR, parts.length));
            }
        }

        return splitProducts;
    }

    private String getDeviceProductType(IDevice device) {
        // FIXME: merge this into the getProperties match
        return device.getProperty("ro.hardware");
    }

    private String getDeviceProductVariant(IDevice device) {
        return device.getProperty("ro.product.device");
    }
}
