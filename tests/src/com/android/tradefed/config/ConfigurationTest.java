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

import com.android.ddmlib.Log.LogLevel;
import com.android.tradefed.device.IDeviceRecovery;
import com.android.tradefed.log.ILeveledLogOutput;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.targetsetup.BuildInfo;
import com.android.tradefed.targetsetup.IBuildProvider;
import com.android.tradefed.targetsetup.ITargetPreparer;
import com.android.tradefed.targetsetup.TargetSetupError;

import org.easymock.EasyMock;

import java.util.ArrayList;
import java.util.List;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestResult;

/**
 * Unit tests for {@link Configuration}.
 */
public class ConfigurationTest extends TestCase {

    private static final String CONFIG_OBJECT_NAME = "object_name";
    private static final String OPTION_DESCRIPTION = "bool description";
    private static final String OPTION_NAME = "bool";

    /**
     * Interface for test object stored in a {@link IConfiguration}.
     */
    private static interface TestConfig {

        public boolean getBool();
    }

    private static class TestConfigObject implements TestConfig {

        @Option(name = OPTION_NAME, description = OPTION_DESCRIPTION)
        private boolean mBool;

        public boolean getBool() {
            return mBool;
        }
    }

    private Configuration mConfig;

    /**
     * {@inheritDoc}
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mConfig = new Configuration();
    }

    /**
     * Test that {@link Configuration#getConfigurationObject(String, Class)} can retrieve
     * a previously stored object.
     */
    public void testGetConfigurationObject() throws ConfigurationException {
        TestConfigObject testConfigObject = new TestConfigObject();
        mConfig.setConfigurationObject(CONFIG_OBJECT_NAME, testConfigObject);
        Object fromConfig = mConfig.getConfigurationObject(CONFIG_OBJECT_NAME);
        assertEquals(testConfigObject, fromConfig);
    }

    /**
     * Test {@link Configuration#getConfigurationObjectList(String)}
     */
    @SuppressWarnings("unchecked")
    public void testGetConfigurationObjectList() throws ConfigurationException  {
        TestConfigObject testConfigObject = new TestConfigObject();
        mConfig.setConfigurationObject(CONFIG_OBJECT_NAME, testConfigObject);
        List<TestConfig> configList = (List<TestConfig>)mConfig.getConfigurationObjectList(
                CONFIG_OBJECT_NAME);
        assertEquals(testConfigObject, configList.get(0));
    }

    /**
     * Test that {@link Configuration#getConfigurationObject(String)} with a name that does
     * not exist.
     */
    public void testGetConfigurationObject_wrongname()  {
        assertNull(mConfig.getConfigurationObject("non-existent"));
    }

    /**
     * Test that calling {@link Configuration#getConfigurationObject(String)} for a built-in config
     * type that supports lists.
     */
    public void testGetConfigurationObject_typeIsList()  {
        try {
            mConfig.getConfigurationObject(Configuration.TEST_NAME);
            fail("IllegalStateException not thrown");
        } catch (IllegalStateException e) {
            // expected
        }
    }

    /**
     * Test that calling {@link Configuration#getConfigurationObject(String)} for a config type
     * that is a list.
     */
    public void testGetConfigurationObject_forList() throws ConfigurationException  {
        List<TestConfigObject> list = new ArrayList<TestConfigObject>();
        list.add(new TestConfigObject());
        list.add(new TestConfigObject());
        mConfig.setConfigurationObjectList(CONFIG_OBJECT_NAME, list);
        try {
            mConfig.getConfigurationObject(CONFIG_OBJECT_NAME);
            fail("IllegalStateException not thrown");
        } catch (IllegalStateException e) {
            // expected
        }
    }

    /**
     * Test that setConfigurationObject throws a ConfigurationException when config object provided
     * is not the correct type
     */
    public void testSetConfigurationObject_wrongtype()  {
        try {
            // arbitrarily, use the "Test" type as expected type
            mConfig.setConfigurationObject(Configuration.TEST_NAME, new TestConfigObject());
            fail("setConfigurationObject did not throw ConfigurationException");
        } catch (ConfigurationException e) {
            // expected
        }
    }

    /**
     * Test {@link Configuration#getConfigurationObjectList(String, Class)} when config object
     * with given name does not exist.
     */
    public void testGetConfigurationObjectList_wrongname() {
        assertNull(mConfig.getConfigurationObjectList("non-existent"));
    }

    /**
     * Test {@link Configuration#setConfigurationObjectList(String, List)} when config object
     * is the wrong type
     */
    public void testSetConfigurationObjectList_wrongtype() throws ConfigurationException  {
        try {
            List<TestConfigObject> myList = new ArrayList<TestConfigObject>(1);
            myList.add(new TestConfigObject());
            // arbitrarily, use the "Test" type as expected type
            mConfig.setConfigurationObjectList(Configuration.TEST_NAME, myList);
            fail("setConfigurationObject did not throw ConfigurationException");
        } catch (ConfigurationException e) {
            // expected
        }
    }

    /**
     * Test method for {@link com.android.tradefed.config.Configuration#getBuildProvider()}.
     */
    public void testGetBuildProvider() throws ConfigurationException, TargetSetupError {
        // check that the default provider is present and doesn't blow up
        assertNotNull(mConfig.getBuildProvider().getBuild());
        // check set and get
        final IBuildProvider provider = EasyMock.createMock(IBuildProvider.class);
        mConfig.setBuildProvider(provider);
        assertEquals(provider, mConfig.getBuildProvider());
    }

    /**
     * Test method for {@link Configuration#getTargetPreparer()}.
     */
    public void testGetTargetPreparers() throws Exception {
        // check that the default preparer is present and doesn't blow up
        mConfig.getTargetPreparers().get(0).setUp(null, null);
        // test set and get
        final ITargetPreparer prep = EasyMock.createMock(ITargetPreparer.class);
        mConfig.setTargetPreparer(prep);
        assertEquals(prep, mConfig.getTargetPreparers().get(0));
    }

    /**
     * Test method for {@link Configuration#getTests()}.
     */
    public void testGetTests() throws ConfigurationException {
        // check that the default test is present and doesn't blow up
        mConfig.getTests().get(0).run(new TestResult());
        Test test1 = EasyMock.createMock(Test.class);
        mConfig.setTest(test1);
        assertEquals(test1, mConfig.getTests().get(0));
    }

    /**
     * Test method for {@link Configuration#getDeviceRecovery()}.
     */
    public void testGetDeviceRecovery() throws ConfigurationException {
        // check that the default recovery is present
        assertNotNull(mConfig.getDeviceRecovery());
        final IDeviceRecovery recovery = EasyMock.createMock(IDeviceRecovery.class);
        mConfig.setDeviceRecovery(recovery);
        assertEquals(recovery, mConfig.getDeviceRecovery());
    }

    /**
     * Test method for {@link Configuration#getLogOutput()}.
     */
    public void testGetLogOutput() throws ConfigurationException {
        // check that the default logger is present and doesn't blow up
        mConfig.getLogOutput().printLog(LogLevel.INFO, "testGetLogOutput", "test");
        final ILeveledLogOutput logger = EasyMock.createMock(ILeveledLogOutput.class);
        mConfig.setLogOutput(logger);
        assertEquals(logger, mConfig.getLogOutput());
    }

    /**
     * Test method for {@link Configuration#getTestInvocationListeners()}.
     * @throws ConfigurationException
     */
    public void testGetTestInvocationListeners() throws ConfigurationException {
        // check that the default listener is present and doesn't blow up
        ITestInvocationListener defaultListener = mConfig.getTestInvocationListeners().get(0);
        defaultListener.invocationStarted(new BuildInfo());
        defaultListener.invocationEnded(1);

        final ITestInvocationListener listener1 = EasyMock.createMock(
                ITestInvocationListener.class);
        mConfig.setTestInvocationListener(listener1);
        assertEquals(listener1, mConfig.getTestInvocationListeners().get(0));
    }

    /**
     * Test {@link Configuration#setConfigurationObject(String, Object)} with a
     * {@link IConfigurationReceiver}
     */
    public void testSetConfigurationObject_configReceiver() throws ConfigurationException {
        final IConfigurationReceiver mockConfigReceiver = EasyMock.createMock(
                IConfigurationReceiver.class);
        mockConfigReceiver.setConfiguration(mConfig);
        EasyMock.replay(mockConfigReceiver);
        mConfig.setConfigurationObject("example", mockConfigReceiver);
        EasyMock.verify(mockConfigReceiver);
    }

    /**
     * Test {@link Configuration#injectOptionValue(String, String)}
     */
    public void testInjectOptionValue() throws ConfigurationException {
        TestConfigObject testConfigObject = new TestConfigObject();
        mConfig.setConfigurationObject(CONFIG_OBJECT_NAME, testConfigObject);
        mConfig.injectOptionValue(OPTION_NAME, Boolean.toString(true));
        assertTrue(testConfigObject.getBool());
    }
}
