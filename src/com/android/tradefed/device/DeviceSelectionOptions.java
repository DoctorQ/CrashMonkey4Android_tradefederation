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

import com.android.ddmlib.Log;
import com.android.tradefed.config.Option;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Container for for device selection criteria.
 */
public class DeviceSelectionOptions implements IDeviceSelectionOptions {

    private static final String LOG_TAG = "DeviceSelectionOptions";

    @Option(name="serial", shortName='s', description=
        "run this test on a specific device with given serial number(s)")
    private Collection<String> mSerials = new ArrayList<String>();

    @Option(name="exclude-serial", description=
        "run this test on any device except those with this serial number(s)")
    private Collection<String> mExcludeSerials = new ArrayList<String>();

    @Option(name="product-type", description=
        "run this test on device with this product type(s)")
    private Collection<String> mProductTypes = new ArrayList<String>();

    @Option(name="property", description=
        "run this test on device with this property value. " +
        "Expected format <propertyname>=<propertyvalue>")
    private Collection<String> mPropertyStrings = new ArrayList<String>();

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
}
