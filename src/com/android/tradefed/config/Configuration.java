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

import com.android.tradefed.build.IBuildProvider;
import com.android.tradefed.build.StubBuildProvider;
import com.android.tradefed.device.IDeviceRecovery;
import com.android.tradefed.device.WaitDeviceRecovery;
import com.android.tradefed.log.ILeveledLogOutput;
import com.android.tradefed.log.StdoutLogger;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.TextResultReporter;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.targetprep.StubTargetPreparer;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.testtype.StubTest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A concrete {@link IConfiguration} implementation that stores the loaded config objects in a map
 */
public class Configuration implements IConfiguration {

    // names for built in configuration objects
    public static final String BUILD_PROVIDER_NAME = "build_provider";
    public static final String TARGET_PREPARER_NAME = "target_preparer";
    public static final String TEST_NAME = "test";
    public static final String DEVICE_RECOVERY_NAME = "device_recovery";
    public static final String LOGGER_NAME = "logger";
    public static final String RESULT_REPORTER_NAME = "result_reporter";

    private static Map<String, ObjTypeInfo> sObjTypeMap = null;

    /** Mapping of config object name to config objects. */
    private Map<String, List<Object>> mConfigMap;

    /**
     * Container struct for built-in config object type
     */
    private static class ObjTypeInfo {
        final Class<?> mExpectedType;
        /** true if a list (ie many objects in a single config) are supported for this type */
        final boolean mIsListSupported;

        ObjTypeInfo(Class<?> expectedType, boolean isList) {
            mExpectedType = expectedType;
            mIsListSupported = isList;
        }
    }

    /**
     * Determine if given config object type name is a built in object
     *
     * @param name the config name
     * @return <code>true</code> if name is a built in object type
     */
    static boolean isBuiltInObjType(String name) {
        return getObjTypeMap().containsKey(name);
    }

    private static synchronized Map<String, ObjTypeInfo> getObjTypeMap() {
        if (sObjTypeMap == null) {
            sObjTypeMap = new HashMap<String, ObjTypeInfo>();
            sObjTypeMap.put(BUILD_PROVIDER_NAME, new ObjTypeInfo(IBuildProvider.class, false));
            sObjTypeMap.put(TARGET_PREPARER_NAME, new ObjTypeInfo(ITargetPreparer.class, true));
            sObjTypeMap.put(TEST_NAME, new ObjTypeInfo(IRemoteTest.class, true));
            sObjTypeMap.put(DEVICE_RECOVERY_NAME, new ObjTypeInfo(IDeviceRecovery.class, false));
            sObjTypeMap.put(LOGGER_NAME, new ObjTypeInfo(ILeveledLogOutput.class, false));
            sObjTypeMap.put(RESULT_REPORTER_NAME, new ObjTypeInfo(ITestInvocationListener.class,
                    true));
        }
        return sObjTypeMap;
    }

    /**
     * Creates an {@link Configuration} with default config objects.
     */
    public Configuration() {
        mConfigMap = new HashMap<String, List<Object>>();
        setBuildProvider(new StubBuildProvider());
        setTargetPreparer(new StubTargetPreparer());
        setTest(new StubTest());
        setDeviceRecovery(new WaitDeviceRecovery());
        setLogOutput(new StdoutLogger());
        setTestInvocationListener(new TextResultReporter());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IBuildProvider getBuildProvider() {
        return (IBuildProvider)getConfigurationObject(BUILD_PROVIDER_NAME);
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    @Override
    public List<ITargetPreparer> getTargetPreparers() {
        return (List<ITargetPreparer>)getConfigurationObjectList(TARGET_PREPARER_NAME);
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    @Override
    public List<IRemoteTest> getTests() {
        return (List<IRemoteTest>)getConfigurationObjectList(TEST_NAME);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IDeviceRecovery getDeviceRecovery() {
        return (IDeviceRecovery)getConfigurationObject(DEVICE_RECOVERY_NAME);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ILeveledLogOutput getLogOutput() {
        return (ILeveledLogOutput)getConfigurationObject(LOGGER_NAME);
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    @Override
    public List<ITestInvocationListener> getTestInvocationListeners() {
        return (List<ITestInvocationListener>)getConfigurationObjectList(RESULT_REPORTER_NAME);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<?> getConfigurationObjectList(String name) {
        return mConfigMap.get(name);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object getConfigurationObject(String name) {
        List<?> configObjects = getConfigurationObjectList(name);
        if (configObjects == null) {
            return null;
        }
        ObjTypeInfo typeInfo = getObjTypeMap().get(name);
        if (typeInfo != null && typeInfo.mIsListSupported) {
            throw new IllegalStateException(String.format("Wrong method call. " +
                    "Used getConfigurationObject() for a config object that is stored as a list",
                        name));
        }
        if (configObjects.size() != 1) {
            throw new IllegalStateException(String.format(
                    "Attempted to retrieve single object for %s, but %d are present",
                    name, configObjects.size()));
        }
        return configObjects.get(0);
    }

    /**
     * {@inheritDoc}
     */
    @Override
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
    @Override
    public void injectOptionValue(String optionName, String valueText)
            throws ConfigurationException {
        OptionSetter optionSetter = new OptionSetter(getAllConfigurationObjects());
        optionSetter.setOptionValue(optionName, valueText);
    }

    /**
     * Creates a shallow copy of this object.
     */
    @Override
    public Configuration clone() {
        Configuration clone = new Configuration();
        for (Map.Entry<String, List<Object>> entry : mConfigMap.entrySet()) {
            clone.setConfigurationObjectListNoThrow(entry.getKey(), entry.getValue());
        }
        return clone;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setBuildProvider(IBuildProvider provider) {
        setConfigurationObjectNoThrow(BUILD_PROVIDER_NAME, provider);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setTestInvocationListeners(List<ITestInvocationListener> listeners) {
        setConfigurationObjectListNoThrow(RESULT_REPORTER_NAME, listeners);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setTestInvocationListener(ITestInvocationListener listener) {
        setConfigurationObjectNoThrow(RESULT_REPORTER_NAME, listener);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setTest(IRemoteTest test) {
        setConfigurationObjectNoThrow(TEST_NAME, test);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setTests(List<IRemoteTest> tests) {
        setConfigurationObjectListNoThrow(TEST_NAME, tests);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setLogOutput(ILeveledLogOutput logger) {
        setConfigurationObjectNoThrow(LOGGER_NAME, logger);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setDeviceRecovery(IDeviceRecovery recovery) {
        setConfigurationObjectNoThrow(DEVICE_RECOVERY_NAME, recovery);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setTargetPreparer(ITargetPreparer preparer) {
        setConfigurationObjectNoThrow(TARGET_PREPARER_NAME, preparer);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setConfigurationObject(String name, Object configObject)
            throws ConfigurationException {
        if (configObject == null) {
            throw new IllegalArgumentException("configObject cannot be null");
        }
        mConfigMap.remove(name);
        addObject(name, configObject);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setConfigurationObjectList(String name, List<?> configList)
            throws ConfigurationException {
        if (configList == null) {
            throw new IllegalArgumentException("configList cannot be null");
        }
        mConfigMap.remove(name);
        for (Object configObject : configList) {
            addObject(name, configObject);
        }
    }

    /**
     * Adds a loaded object to this configuration.
     *
     * @param name the unique name of the configuration object
     * @param configObject the configuration object
     * @throws ConfigurationException if object was not the correct type
     */
    private void addObject(String name, Object configObject) throws ConfigurationException {
        List<Object> objList = mConfigMap.get(name);
        if (objList == null) {
            objList = new ArrayList<Object>(1);
            mConfigMap.put(name, objList);
        }
        ObjTypeInfo typeInfo = getObjTypeMap().get(name);
        if (typeInfo != null && !typeInfo.mExpectedType.isInstance(configObject)) {
            throw new ConfigurationException(String.format(
                    "The config object %s is not the correct type. Expected %s, received %s",
                    name, typeInfo.mExpectedType.getCanonicalName(),
                    configObject.getClass().getCanonicalName()));
        }
        if (typeInfo != null && !typeInfo.mIsListSupported && objList.size() > 0) {
            throw new ConfigurationException(String.format(
                    "Only one config object allowed for %s, but multiple were specified.",
                    name));
        }
        objList.add(configObject);
        if (configObject instanceof IConfigurationReceiver) {
            // TODO: this won't work properly if config object is shared among configurations
            ((IConfigurationReceiver)configObject).setConfiguration(this);
        }
    }

    /**
     * A wrapper around {@link #setConfigurationObject(String, Object)} that will not throw
     * {@link ConfigurationException}.
     * <p/>
     * Intended to be used in cases where its guaranteed that <var>configObject</var> is the
     * correct type.
     *
     * @param name
     * @param configObject
     */
    private void setConfigurationObjectNoThrow(String name, Object configObject) {
        try {
            setConfigurationObject(name, configObject);
        } catch (ConfigurationException e) {
            // should never happen
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * A wrapper around {@link #setConfigurationObjectList(String, List)} that will not throw
     * {@link ConfigurationException}.
     * <p/>
     * Intended to be used in cases where its guaranteed that <var>configObject</var> is the
     * correct type
     *
     * @param name
     * @param configObject
     */
    private void setConfigurationObjectListNoThrow(String name, List<?> configList) {
        try {
            setConfigurationObjectList(name, configList);
        } catch (ConfigurationException e) {
            // should never happen
            throw new IllegalArgumentException(e);
        }
    }
}
