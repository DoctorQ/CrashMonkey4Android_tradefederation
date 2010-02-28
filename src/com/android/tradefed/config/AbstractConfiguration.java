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

import java.io.PrintStream;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Hashtable;
import java.util.Map;

import junit.framework.Test;

/**
 * A {@link IConfiguration} base class that tracks loaded configuration objects.
 */
abstract class AbstractConfiguration implements IConfiguration {

    // names for built in configuration objects
    static final String BUILD_PROVIDER_NAME = "build_provider";
    static final String TARGET_PREPARER_NAME = "target_preparer";
    static final String TEST_NAME = "test";
    static final String DEVICE_RECOVERY_NAME = "device_recovery";
    static final String LOGGER_NAME = "logger";
    static final String RESULT_REPORTER_NAME = "result_reporter";

    /** Mapping of config object name to config object. */
    private Map<String, Object> mConfigMap;

    AbstractConfiguration() {
        mConfigMap = new Hashtable<String, Object>();
    }

    /**
     * Adds a loaded object to this configuration.
     *
     * @param name the unique name of the configuration object
     * @param configObject the configuration object
     */
    void addObject(String name, Object configObject) {
        mConfigMap.put(name, configObject);
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
    public Test getTest() throws ConfigurationException {
        return (Test)getConfigurationObject(TEST_NAME,
                Test.class);
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
    public ITestInvocationListener getTestInvocationListener() throws ConfigurationException {
        return (ITestInvocationListener)getConfigurationObject(RESULT_REPORTER_NAME,
                ITestInvocationListener.class);
    }

    /**
     * {@inheritDoc}
     */
    public Object getConfigurationObject(String name, Class<?> expectedType)
    throws ConfigurationException {
        Object configObject = mConfigMap.get(name);
        if (configObject == null) {
            throw new ConfigurationException(String.format(
                    "Could not find config object with name %s", name));
        } else if (!expectedType.isInstance(configObject)) {
            throw new ConfigurationException(String.format(
                    "The config object %s is not the correct type. Expected %s ", name,
                    expectedType.getCanonicalName()));
        }
        return configObject;

    }

    /**
     * {@inheritDoc}
     */
    public Collection<? extends Object> getConfigurationObjects() {
        return mConfigMap.values();
    }

    /**
     * {@inheritDoc}
     */
    public void printCommandUsage(PrintStream out) throws ConfigurationException {
        // TODO: pretty print the output ?
        // TODO: this implementation probably belongs in ArgsOptionParser
        for (Object configObject : getConfigurationObjects()) {
            printOptionsForObject(configObject, out);
        }
    }

    /**
     * Prints out the available config options for given configuration object.
     *
     * @param configObject the config object.
     * @param out the output strem to dump output to.
     */
    private void printOptionsForObject(Object configObject, PrintStream out) {
        final Class<?> optionClass = configObject.getClass();
        for (Field field : optionClass.getDeclaredFields()) {
            if (field.isAnnotationPresent(Option.class)) {
                final Option option = field.getAnnotation(Option.class);
                out.printf("%s%s: %s", ArgsOptionParser.OPTION_NAME_PREFIX, option.name(),
                        option.description());
            }
        }
    }
}
