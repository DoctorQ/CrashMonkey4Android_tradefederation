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
package com.android.tradefed.config;

import com.android.tradefed.device.IDeviceRecovery;
import com.android.tradefed.log.ILeveledLogOutput;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.targetsetup.IBuildProvider;
import com.android.tradefed.targetsetup.ITargetPreparer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import junit.framework.Test;

/**
 * A concrete {@link IConfiguration} implementation that stores the loaded config objects in a map
 */
// TODO: make package-private
public class Configuration implements IConfiguration {

    // names for built in configuration objects
    public static final String BUILD_PROVIDER_NAME = "build_provider";
    public static final String TARGET_PREPARER_NAME = "target_preparer";
    public static final String TEST_NAME = "test";
    public static final String DEVICE_RECOVERY_NAME = "device_recovery";
    public static final String LOGGER_NAME = "logger";
    public static final String RESULT_REPORTER_NAME = "result_reporter";

    private static Set<String> sObjNames = null;

    static Set<String> getConfigObjNames() {
        if (sObjNames == null) {
            sObjNames = new HashSet<String>();
            sObjNames.add(BUILD_PROVIDER_NAME);
            sObjNames.add(TARGET_PREPARER_NAME);
            sObjNames.add(TEST_NAME);
            sObjNames.add(DEVICE_RECOVERY_NAME);
            sObjNames.add(LOGGER_NAME);
            sObjNames.add(RESULT_REPORTER_NAME);
        }
        return sObjNames;
    }

    /** Mapping of config object name to config objects. */
    private Map<String, List<Object>> mConfigMap;

    /**
     * Creates a {@link Configuration} with no config objects.
     * <p/>
     * Exposed for unit testing
     */
    Configuration() {
        this(new HashMap<String, List<Object>>());
    }

    /**
     * Create a {@link Configuration}.
     *
     * @param configObjects the config object name to object list map. This {@link IConfiguration}
     *            will be injected into any object in the map that is a
     *            {@link IConfigurationReceiver}
     */
    Configuration(Map<String, List<Object>> configObjectMap) {
        mConfigMap = configObjectMap;
        for (List<Object> configObjectList : configObjectMap.values()) {
            for (Object configObject : configObjectList)
                if (configObject instanceof IConfigurationReceiver) {
                    ((IConfigurationReceiver)configObject).setConfiguration(this);
                }
        }
    }

    /**
     * Adds a loaded object to this configuration.
     * <p/>
     * Exposed for unit testing
     *
     * @param name the unique name of the configuration object
     * @param configObject the configuration object
     *
     */
    void addObject(String name, Object configObject) {
        List<Object> objList = mConfigMap.get(name);
        if (objList == null) {
            objList = new ArrayList<Object>(1);
            mConfigMap.put(name, objList);
        }
        objList.add(configObject);
        if (configObject instanceof IConfigurationReceiver) {
            ((IConfigurationReceiver)configObject).setConfiguration(this);
        }
    }

    /**
     * {@inheritDoc}
     */
    public IBuildProvider getBuildProvider() throws ConfigurationException {
        return (IBuildProvider)getConfigurationObject(BUILD_PROVIDER_NAME,
                IBuildProvider.class);
    }

    /**
     * {@inheritDoc}
     */
    public ITargetPreparer getTargetPreparer() throws ConfigurationException {
        return (ITargetPreparer)getConfigurationObject(TARGET_PREPARER_NAME,
                ITargetPreparer.class);
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    public List<Test> getTests() throws ConfigurationException {
        return (List<Test>)getConfigurationObjectList(TEST_NAME, Test.class);
    }

    /**
     * {@inheritDoc}
     */
    public IDeviceRecovery getDeviceRecovery() throws ConfigurationException {
        return (IDeviceRecovery)getConfigurationObject(DEVICE_RECOVERY_NAME,
                IDeviceRecovery.class);
    }

    /**
     * {@inheritDoc}
     */
    public ILeveledLogOutput getLogOutput() throws ConfigurationException {
        return (ILeveledLogOutput)getConfigurationObject(LOGGER_NAME,
                ILeveledLogOutput.class);
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    public List<ITestInvocationListener> getTestInvocationListeners() throws ConfigurationException {
        return (List<ITestInvocationListener>)getConfigurationObjectList(RESULT_REPORTER_NAME,
                ITestInvocationListener.class);
    }

    public List<?> getConfigurationObjectList(String name, Class<?> expectedType)
        throws ConfigurationException {
        List<Object> configObjects = mConfigMap.get(name);
        if (configObjects == null) {
            throw new ConfigurationException(String.format(
                    "Could not find config object with name %s", name));
        }
        for (Object object : configObjects) {
            if (!expectedType.isInstance(object)) {
                throw new ConfigurationException(String.format(
                        "The config object %s is not the correct type. Expected %s, received %s",
                        name, expectedType.getCanonicalName(),
                        object.getClass().getCanonicalName()));
            }
        }
        return configObjects;
    }

    /**
     * {@inheritDoc}
     */
    public Object getConfigurationObject(String name, Class<?> expectedType)
            throws ConfigurationException {
        List<?> configObjects = getConfigurationObjectList(name, expectedType);
        if (configObjects.size() != 1) {
            throw new ConfigurationException(String.format(
                    "Only one config object allowed for %s, but %d were specified.",
                    name, configObjects.size()));
        }
        return configObjects.get(0);
    }

    /**
     * {@inheritDoc}
     */
    public Collection<Object> getAllConfigurationObjects() {
        Collection<Object> objectsCopy = new ArrayList<Object>();
        for (List<Object> objectList : mConfigMap.values()) {
            objectsCopy.addAll(objectList);
        }
        return objectsCopy;
    }

    /**
     * {@inheritDoc}
     */
    public void injectOptionValue(String optionName, String valueText)
            throws ConfigurationException {
        OptionSetter optionSetter = new OptionSetter(getAllConfigurationObjects());
        optionSetter.setOptionValue(optionName, valueText);
    }
}
