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

import org.easymock.EasyMock;

import java.util.List;

import junit.framework.Test;
import junit.framework.TestCase;

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
    private TestConfigObject mConfigObject;

    /**
     * {@inheritDoc}
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mConfig = new Configuration();
        mConfigObject = new TestConfigObject();
        mConfig.addObject(CONFIG_OBJECT_NAME, mConfigObject);
    }

    /**
     * Test that {@link Configuration#getConfigurationObject(String, Class)} can retrieve
     * a previously stored object.
     */
    public void testGetConfigurationObject() throws ConfigurationException {
        Object fromConfig = mConfig.getConfigurationObject(CONFIG_OBJECT_NAME, TestConfig.class);
        assertEquals(mConfigObject, fromConfig);
    }

    /**
     * Test that {@link Configuration#getConfigurationObject(String, Class)} throws a
     * {@link ConfigurationException} when config object with given name does not exist.
     */
    public void testGetConfigurationObject_wrongname()  {
        try {
            mConfig.getConfigurationObject("non-existent", TestConfig.class);
            fail("getConfigurationObject did not throw ConfigurationException");
        } catch (ConfigurationException e) {
            // expected
        }
    }

    /**
     * Test that getConfigurationObject throws a ConfigurationException when config object exists,
     * but it not the expected type.
     */
    public void testGetConfigurationObject_wrongtype()  {
        try {
            // arbitrarily, use the "Test" interface as expected type
            mConfig.getConfigurationObject(CONFIG_OBJECT_NAME, Test.class);
            fail("getConfigurationObject did not throw ConfigurationException");
        } catch (ConfigurationException e) {
            // expected
        }
    }

    /**
     * Test {@link Configuration#getConfigurationObjectList(String, Class)}
     */
    @SuppressWarnings("unchecked")
    public void testGetConfigurationObjectList() throws ConfigurationException  {
        List<TestConfig> configList = (List<TestConfig>)mConfig.getConfigurationObjectList(
                CONFIG_OBJECT_NAME, TestConfig.class);
        assertEquals(mConfigObject, configList.get(0));
    }

    /**
     * Test {@link Configuration#getConfigurationObjectList(String, Class)} when config object
     * with given name does not exist.
     */
    public void testGetConfigurationObjectList_wrongname() throws ConfigurationException  {
        try {
            mConfig.getConfigurationObjectList("non-existent", TestConfig.class);
            fail("ConfigurationException not thrown");
        } catch (ConfigurationException e) {
            // expected
        }
    }

    /**
     * Test {@link Configuration#getConfigurationObjectList(String, Class)} when config object
     * exists but is the wrong type
     */
    public void testGetConfigurationObjectList_wrongtype() throws ConfigurationException  {
        // add a object of the wrong type
        mConfig.addObject(CONFIG_OBJECT_NAME, new Object());
        try {

            mConfig.getConfigurationObjectList(CONFIG_OBJECT_NAME, TestConfig.class);
            fail("ConfigurationException not thrown");
        } catch (ConfigurationException e) {
            // expected
        }
    }

    /**
     * Test method for {@link com.android.tradefed.config.Configuration#getBuildProvider()}.
     */
    public void testGetBuildProvider() throws ConfigurationException {
        final IBuildProvider provider = EasyMock.createMock(IBuildProvider.class);
        mConfig.addObject(Configuration.BUILD_PROVIDER_NAME, provider);
        assertEquals(provider, mConfig.getBuildProvider());
    }

    /**
     * Test method for {@link Configuration#getTargetPreparer()}.
     */
    public void testGetTargetPreparer() throws ConfigurationException {
        final ITargetPreparer prep = EasyMock.createMock(ITargetPreparer.class);
        mConfig.addObject(Configuration.TARGET_PREPARER_NAME, prep);
        assertEquals(prep, mConfig.getTargetPreparer());
    }

    /**
     * Test method for {@link Configuration#getTests()}.
     * @throws ConfigurationException
     */
    public void testGetTests() throws ConfigurationException {
        final Test test1 = EasyMock.createMock(Test.class);
        final Test test2 = EasyMock.createMock(Test.class);
        mConfig.addObject(Configuration.TEST_NAME, test1);
        mConfig.addObject(Configuration.TEST_NAME, test2);
        assertTrue(mConfig.getTests().contains(test1));
        assertTrue(mConfig.getTests().contains(test2));
    }

    /**
     * Test method for {@link Configuration#getDeviceRecovery()}.
     * @throws ConfigurationException
     */
    public void testGetDeviceRecovery() throws ConfigurationException {
        final IDeviceRecovery recovery = EasyMock.createMock(IDeviceRecovery.class);
        mConfig.addObject(Configuration.DEVICE_RECOVERY_NAME, recovery);
        assertEquals(recovery, mConfig.getDeviceRecovery());
    }

    /**
     * Test method for {@link Configuration#getLogOutput()}.
     * @throws ConfigurationException
     */
    public void testGetLogOutput() throws ConfigurationException {
        final ILeveledLogOutput logger = EasyMock.createMock(ILeveledLogOutput.class);
        mConfig.addObject(Configuration.LOGGER_NAME, logger);
        assertEquals(logger, mConfig.getLogOutput());
    }

    /**
     * Test method for {@link Configuration#getTestInvocationListeners()}.
     * @throws ConfigurationException
     */
    public void testGetTestInvocationListeners() throws ConfigurationException {
        final ITestInvocationListener listener1 = EasyMock.createMock(
                ITestInvocationListener.class);
        final ITestInvocationListener listener2 = EasyMock.createMock(
                ITestInvocationListener.class);
        mConfig.addObject(Configuration.RESULT_REPORTER_NAME, listener1);
        mConfig.addObject(Configuration.RESULT_REPORTER_NAME, listener2);
        assertTrue(mConfig.getTestInvocationListeners().contains(listener1));
        assertTrue(mConfig.getTestInvocationListeners().contains(listener2));
    }
}
