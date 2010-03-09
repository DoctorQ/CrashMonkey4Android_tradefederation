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

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import junit.framework.Test;
import junit.framework.TestCase;

/**
 * Unit tests for {@link AbstractConfiguration}.
 */
public class AbstractConfigurationTest extends TestCase {

    private static final String CONFIG_OBJECT_NAME = "object_name";
    private static final String OPTION_DESCRIPTION = "bool description";
    private static final String OPTION_NAME = "bool";

    /**
     * Dummy implementation of the {@link AbstractConfiguration} class so it can be tested
     */
    private static class TestConfiguration extends AbstractConfiguration {

    }

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

    private TestConfiguration mConfig;
    private TestConfigObject mConfigObject;

    /**
     * {@inheritDoc}
     */
    protected void setUp() throws Exception {
        super.setUp();
        mConfig = new TestConfiguration();
        mConfigObject = new TestConfigObject();
        mConfig.addObject(CONFIG_OBJECT_NAME, mConfigObject);
    }

    /**
     * Test that {@link AbstractConfiguration#getConfigurationObject(String, Class)} can retrieve
     * a previously stored object.
     */
    public void testGetConfigurationObject() throws ConfigurationException {
        Object fromConfig = mConfig.getConfigurationObject(CONFIG_OBJECT_NAME, TestConfig.class);
        assertEquals(mConfigObject, fromConfig);
    }

    /**
     * Test that {@link AbstractConfiguration#getConfigurationObject(String, Class)} throws a
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
     * Basic test for {@link AbstractConfiguration#printCommandUsage(java.io.PrintStream)}.
     */
    public void testPrintCommandUsage() throws ConfigurationException {
        // dump the print stream results to the ByteArrayOutputStream, so contents can be evaluated
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintStream mockPrintStream = new PrintStream(outputStream);
        mConfig.printCommandUsage(mockPrintStream);

        // verifying exact contents would be prone to high-maintenance, so instead, just validate
        // a few critical tidbits are present
        final String usageString = outputStream.toString();
        assertTrue("Usage text does not contain option name", usageString.contains(OPTION_NAME));
        assertTrue("Usage text does not contain option description",
                usageString.contains(OPTION_DESCRIPTION));
    }
}
