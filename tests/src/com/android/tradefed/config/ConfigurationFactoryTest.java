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
import com.android.tradefed.log.ILeveledLogOutput;
import com.android.tradefed.util.FileUtil;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;

import junit.framework.TestCase;

/**
 * Unit tests for {@link ConfigurationFactory}
 */
public class ConfigurationFactoryTest extends TestCase {

    private IConfigurationFactory mFactory;

    private static final String OPTION_DESCRIPTION = "bool description";
    private static final String OPTION_NAME = "bool";

    private static class TestConfigObject {
        @SuppressWarnings("unused")
        @Option(name = OPTION_NAME, description = OPTION_DESCRIPTION)
        private boolean mBool;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mFactory = ConfigurationFactory.getInstance();
    }

    /**
     * Simple test method to ensure {@link ConfigurationFactory#getConfiguration(String)} return a
     * valid configuration for all the default configs
     */
    public void testGetConfiguration_defaults() throws ConfigurationException {
        for (String config : ConfigurationFactory.sDefaultConfigs) {
            assertConfigValid(config);
        }
    }

    /**
     * Test that a config xml defined in this test jar can be read as a built-in
     */
    public void testGetConfiguration_extension() throws ConfigurationException {
        assertConfigValid("test-config");
    }

    /**
     * Test specifying a config xml by file path
     */
    public void testGetConfiguration_xmlpath() throws ConfigurationException, IOException {
        // extract the test-config.xml into a tmp file
        InputStream configStream = getClass().getResourceAsStream(
                "/config/test-config.xml");
        File tmpFile = FileUtil.createTempFile("test-config", ".xml");
        try {
            FileUtil.writeToFile(configStream, tmpFile);
            assertConfigValid(tmpFile.getAbsolutePath());
            // check reading it again - should grab the cached version
            assertConfigValid(tmpFile.getAbsolutePath());
        } finally {
            tmpFile.delete();
        }
    }

    /**
     * Checks all config attributes are non-null
     */
    private void assertConfigValid(String name) throws ConfigurationException {
        IConfiguration config = mFactory.getConfiguration(name);
        assertNotNull(config);
        assertNotNull(config.getBuildProvider());
        assertNotNull(config.getDeviceRecovery());
        assertNotNull(config.getLogOutput());
        assertNotNull(config.getTargetPreparers());
        assertNotNull(config.getTests());
        assertNotNull(config.getTestInvocationListeners());
    }

    /**
     * Test calling {@link ConfigurationFactory#getConfiguration(String)} with a name that does not
     * exist.
     */
    public void testGetConfiguration_missing()  {
        try {
            mFactory.getConfiguration("non existent");
            fail("did not throw ConfigurationException");
        } catch (ConfigurationException e) {
            // expected
        }
    }

    /**
     * Test passing empty arg list to
     * {@link ConfigurationFactory#createConfigurationFromArgs(String[])}.
     */
    public void testCreateConfigurationFromArgs_empty() {
        try {
            mFactory.createConfigurationFromArgs(new String[] {});
            fail("did not throw ConfigurationException");
        } catch (ConfigurationException e) {
            // expected
        }
    }

    /**
     * Test {@link ConfigurationFactory#createConfigurationFromArgs(String[])} using host
     */
    public void testCreateConfigurationFromArgs() throws ConfigurationException {
        // pick an arbitrary option to test to ensure it gets populated
        IConfiguration config = mFactory.createConfigurationFromArgs(new String[] {"--log-level",
                LogLevel.VERBOSE.getStringValue(), ConfigurationFactory.HOST_TEST_CONFIG});
        ILeveledLogOutput logger = config.getLogOutput();
        assertEquals(LogLevel.VERBOSE.getStringValue(), logger.getLogLevel());
    }

    /**
     * Test {@link ConfigurationFactory#createConfigurationFromArgs(String[])} when extra positional
     * arguments are supplied
     */
    public void testCreateConfigurationFromArgs_unprocessedArgs() throws ConfigurationException {
        try {
            mFactory.createConfigurationFromArgs(new String[] {"--log-level",
                    LogLevel.VERBOSE.getStringValue(), "blah",
                    ConfigurationFactory.HOST_TEST_CONFIG});
            fail("ConfigurationException not thrown");
        } catch (ConfigurationException e) {
            // expected
        }
    }

    /**
     * Test {@link ConfigurationFactory#printHelp(String[], PrintStream))} with no args specified
     */
    public void testPrintHelp() {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintStream mockPrintStream = new PrintStream(outputStream);
        mFactory.printHelp(new String[] {"--help"}, mockPrintStream);
        // verify all the default configs names are present
        final String usageString = outputStream.toString();
        for (String config : ConfigurationFactory.sDefaultConfigs) {
            assertTrue(usageString.contains(config));
        }
    }

    /**
     * Test {@link ConfigurationFactory#printHelp(String[], PrintStream, Class...))}
     */
    public void testPrintHelp_additional() {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintStream mockPrintStream = new PrintStream(outputStream);
        mFactory.printHelp(new String[] {"--help", ConfigurationFactory.sDefaultConfigs[0]},
                mockPrintStream, TestConfigObject.class);
        // verify TestConfigObject options present
        final String usageString = outputStream.toString();
        assertTrue(usageString.contains(OPTION_NAME));
        assertTrue(usageString.contains(OPTION_DESCRIPTION));
    }
}
