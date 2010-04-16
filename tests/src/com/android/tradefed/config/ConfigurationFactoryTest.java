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

import java.io.File;

import junit.framework.TestCase;

/**
 * Unit tests for {@link ConfigurationFactory}
 */
public class ConfigurationFactoryTest extends TestCase {

    /**
     * {@inheritDoc}
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    /**
     * Simple test method to ensure {@link ConfigurationFactory#getConfiguration(String)} with
     * {@link ConfigurationFactory#INSTRUMENT_CONFIG} returns a valid config.
     */
    public void testGetConfiguration_instrument() throws ConfigurationException {
        assertNotNull(ConfigurationFactory.getConfiguration(
                ConfigurationFactory.INSTRUMENT_CONFIG));
        // TODO: check that returned config is valid
    }

    /**
     * Simple test method to ensure {@link ConfigurationFactory#getConfiguration(String)} with
     * {@link ConfigurationFactory#HOST_TEST_CONFIG} returns a valid config.
     */
    public void testGetConfiguration_host() throws ConfigurationException {
        assertNotNull(ConfigurationFactory.getConfiguration(
                ConfigurationFactory.HOST_TEST_CONFIG));
        // TODO: check that returned config is valid
    }

    /**
     * Simple test method to ensure {@link ConfigurationFactory#getConfiguration(String)} with
     * {@link ConfigurationFactory#TEST_DEF_CONFIG} returns a valid config.
     */
    public void testGetConfiguration_testdef() throws ConfigurationException {
        assertNotNull(ConfigurationFactory.getConfiguration(
                ConfigurationFactory.TEST_DEF_CONFIG));
        // TODO: check that returned config is valid
    }

    /**
     * Test calling {@link ConfigurationFactory#getConfiguration(String)} with a name that does not
     * exist.
     */
    public void testGetConfiguration_missing()  {
        try {
            ConfigurationFactory.getConfiguration("non existent");
            fail("did not throw ConfigurationException");
        } catch (ConfigurationException e) {
            // expected
        }
    }

    /**
     * Placeholder Test method for {@link ConfigurationFactory#createConfigurationFromXML(File)}.
     */
    public void testCreateConfigurationFromXML() throws ConfigurationException {
        try {
            // TODO: use a mock File
            ConfigurationFactory.createConfigurationFromXML(new File("mockFile"));
            fail("did not throw UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // expected
        }
    }

    /**
     * Test passing empty arg list to
     * {@link ConfigurationFactory#createConfigurationFromArgs(String[])}.
     */
    public void testCreateConfigurationFromArgs_empty() {
        try {
            ConfigurationFactory.createConfigurationFromArgs(new String[] {});
            fail("did not throw ConfigurationException");
        } catch (ConfigurationException e) {
            // expected
        }
    }
}
