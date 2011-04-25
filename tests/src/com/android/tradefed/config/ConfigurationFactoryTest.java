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

    private ConfigurationFactory mFactory;

    /** the test config name that is built into this jar */
    private static final String TEST_CONFIG = "test-config";
    /** the config descrption for {@link TEST_CONFIG} */
    private static final String TEST_DESCRIPTION =
        "test that jar plugins to Tradefed can contribute config.xml";

    /**
     * {@inheritDoc}
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mFactory = new ConfigurationFactory();
    }

    /**
     * Sanity test to ensure all configs on classpath are loadable
     */
    public void testLoadAllConfigs() throws ConfigurationException {
        mFactory.loadAllConfigs(false);
    }

    /**
     * Test that a config xml defined in this test jar can be read as a built-in
     */
    public void testGetConfiguration_extension() throws ConfigurationException {
        assertConfigValid(TEST_CONFIG);
    }

    /**
     * Test specifying a config xml by file path
     */
    public void testGetConfiguration_xmlpath() throws ConfigurationException, IOException {
        // extract the test-config.xml into a tmp file
        InputStream configStream = getClass().getResourceAsStream(
                String.format("/config/%s.xml", TEST_CONFIG));
        File tmpFile = FileUtil.createTempFile(TEST_CONFIG, ".xml");
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
        IConfiguration config = mFactory.createConfigurationFromArgs(new String[] {name});
        assertNotNull(config);
    }

    /**
     * Test calling {@link ConfigurationFactory#getConfiguration(String)} with a name that does not
     * exist.
     */
    public void testCreateConfigurationFromArgs_missing()  {
        try {
            mFactory.createConfigurationFromArgs(new String[] {"non existent"});
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
     * Test {@link ConfigurationFactory#createConfigurationFromArgs(String[])} using TEST_CONFIG
     */
    public void testCreateConfigurationFromArgs() throws ConfigurationException {
        // pick an arbitrary option to test to ensure it gets populated
        IConfiguration config = mFactory.createConfigurationFromArgs(new String[] {"--log-level",
                LogLevel.VERBOSE.getStringValue(), TEST_CONFIG});
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
                    TEST_CONFIG});
            fail("ConfigurationException not thrown");
        } catch (ConfigurationException e) {
            // expected
        }
    }

    /**
     * Test {@link ConfigurationFactory#printHelp( PrintStream))}
     */
    public void testPrintHelp() {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintStream mockPrintStream = new PrintStream(outputStream);
        mFactory.printHelp(mockPrintStream);
        // verify all the config names are present
        final String usageString = outputStream.toString();
        assertTrue(usageString.contains(TEST_CONFIG));
        assertTrue(usageString.contains(TEST_DESCRIPTION));
    }

    /**
     * Test {@link ConfigurationFactory#printHelpForConfig(String[], boolean, PrintStream))} when
     * config referenced by args exists
     */
    public void testPrintHelpForConfig_configExists() {
        String[] args = new String[] {TEST_CONFIG};
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintStream mockPrintStream = new PrintStream(outputStream);
        mFactory.printHelpForConfig(args, true, mockPrintStream);

        // verify the default configs name used is present
        final String usageString = outputStream.toString();
        assertTrue(usageString.contains(TEST_CONFIG));
        // TODO: add stricter verification
    }
}
