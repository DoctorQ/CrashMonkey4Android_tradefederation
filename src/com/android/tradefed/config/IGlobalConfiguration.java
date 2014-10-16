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

package com.android.tradefed.config;

import com.android.tradefed.device.DeviceManager;
import com.android.tradefed.device.IDeviceLabelMapper;
import com.android.tradefed.device.IDeviceMonitor;
import com.android.tradefed.device.IDeviceSelection;

import java.util.List;

/**
 * A class to encompass global configuration information for a single Trade Federation instance
 * (encompassing any number of invocations of actual configurations).
 */
public interface IGlobalConfiguration {
    /**
     * Gets the {@link IDeviceMonitor} from the global config.
     *
     * @return the {@link IDeviceMonitor} from the global config, or <code>null</code> if none was
     *         specified.
     */
    public IDeviceMonitor getDeviceMonitor();

    /**
     * Set the {@link IDeviceMonitor}.
     *
     * @param deviceMonitor The monitor
     * @throws ConfigurationException if an {@link IDeviceMonitor} has already been set.
     */
    public void setDeviceMonitor(IDeviceMonitor deviceMonitor) throws ConfigurationException;

    /**
     * Generic method to set the config object list for the given name, replacing any existing
     * value.
     *
     * @param typeName the unique name of the config object type.
     * @param configList the config object list
     * @throws ConfigurationException if any objects in the list are not the correct type
     */
    public void setConfigurationObjectList(String typeName, List<?> configList)
            throws ConfigurationException;

    /**
     * Inject a option value into the set of configuration objects.
     * <p/>
     * Useful to provide values for options that are generated dynamically.
     *
     * @param optionName the option name
     * @param optionValue the option value(s)
     * @throws ConfigurationException if failed to set the option's value
     */
    public void injectOptionValue(String optionName, String optionValue)
            throws ConfigurationException;

    /**
     * Inject a option value into the set of configuration objects.
     * <p/>
     * Useful to provide values for options that are generated dynamically.
     *
     * @param optionName the map option name
     * @param optionKey the map option key
     * @param optionValue the map option value
     * @throws ConfigurationException if failed to set the option's value
     */
    public void injectOptionValue(String optionName, String optionKey, String optionValue)
            throws ConfigurationException;

    /**
     * Set the global config {@link Option} fields with given set of command line arguments
     * <p/>
     * @see {@link ArgsOptionParser} for expected format
     *
     * @param listArgs the command line arguments
     * @return the unconsumed arguments
     */
    public List<String> setOptionsFromCommandLineArgs(List<String> listArgs)
            throws ConfigurationException;

    /**
     * Set the {@link IDeviceSelection}, replacing any existing values.  This sets a global device
     * filter on which devices the {@link DeviceManager} can see.
     *
     * @param deviceSelection
     */
    public void setDeviceRequirements(IDeviceSelection deviceSelection);

    /**
     * Gets the {@link IDeviceSelection} to use from the configuration.  Represents a global filter
     * on which devices the {@link DeviceManager} can see.
     *
     * @return the {@link IDeviceSelection} provided in the configuration.
     */
    public IDeviceSelection getDeviceRequirements();

    public void setSerialToDeviceLabelMapper(IDeviceLabelMapper deviceMapper);
    
    public IDeviceLabelMapper getDeviceLabelMapper();

}
