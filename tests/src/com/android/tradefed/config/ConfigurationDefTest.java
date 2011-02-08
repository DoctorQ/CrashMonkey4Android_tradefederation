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

import com.android.tradefed.build.StubBuildProvider;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;

import junit.framework.TestCase;

/**
 * Unit tests for {@link ConfigurationDef}
 */
public class ConfigurationDefTest extends TestCase {

    private static final String CONFIG_NAME = "name";
    private static final String CONFIG_DESCRIPTION = "config description";
    private static final String OPTION_VALUE = "val";
    private static final String OPTION_VALUE2 = "val2";
    private static final String OPTION_NAME = "option";
    private static final String COLLECTION_OPTION_NAME = "collection";
    private static final String OPTION_DESCRIPTION = "option description";

    public static class OptionTest extends StubBuildProvider {
        @Option(name=COLLECTION_OPTION_NAME, description=OPTION_DESCRIPTION)
        private Collection<String> mCollectionOption = new ArrayList<String>();

        @Option(name=OPTION_NAME, description=OPTION_DESCRIPTION)
        private String mOption;
    }

    private ConfigurationDef mConfigDef;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mConfigDef = new ConfigurationDef(CONFIG_NAME);
        mConfigDef.setDescription(CONFIG_DESCRIPTION);
        mConfigDef.addConfigObjectDef(Configuration.BUILD_PROVIDER_NAME,
                OptionTest.class.getName());
    }

    /**
     * Test {@link ConfigurationDef#createConfiguration()} for a collection option.
     */
    public void testCreateConfiguration_optionCollection() throws ConfigurationException {
        mConfigDef.addOptionDef(COLLECTION_OPTION_NAME, OPTION_VALUE);
        mConfigDef.addOptionDef(COLLECTION_OPTION_NAME, OPTION_VALUE2);
        IConfiguration config = mConfigDef.createConfiguration();
        OptionTest test = (OptionTest)config.getBuildProvider();
        assertTrue(test.mCollectionOption.contains(OPTION_VALUE));
        assertTrue(test.mCollectionOption.contains(OPTION_VALUE2));
    }

    /**
     * Test {@link ConfigurationDef#createConfiguration()} for a String field.
     */
    public void testCreateConfiguration() throws ConfigurationException  {
        mConfigDef.addOptionDef(OPTION_NAME, OPTION_VALUE);
        IConfiguration config = mConfigDef.createConfiguration();
        OptionTest test = (OptionTest)config.getBuildProvider();
        assertEquals(OPTION_VALUE, test.mOption);
    }

    /**
     * Basic test for {@link ConfigurationDef#printCommandUsage(java.io.PrintStream)}.
     */
    public void testPrintCommandUsage() throws ConfigurationException {
        // dump the print stream results to the ByteArrayOutputStream, so contents can be evaluated
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintStream mockPrintStream = new PrintStream(outputStream);
        mConfigDef.printCommandUsage(mockPrintStream);

        // verifying exact contents would be prone to high-maintenance, so instead, just validate
        // all expected names are present
        final String usageString = outputStream.toString();
        assertTrue("Usage text does not contain config name", usageString.contains(CONFIG_NAME));
        assertTrue("Usage text does not contain config description", usageString.contains(
                CONFIG_DESCRIPTION));
        assertTrue("Usage text does not contain object name", usageString.contains(
                Configuration.BUILD_PROVIDER_NAME));
        assertTrue("Usage text does not contain option name", usageString.contains(OPTION_NAME));
        assertTrue("Usage text does not contain option description",
                usageString.contains(OPTION_DESCRIPTION));
    }
}
