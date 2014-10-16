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
import com.android.tradefed.testtype.HostTest;
import com.android.tradefed.testtype.InstrumentationTest;

import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Unit tests for {@link ConfigurationDef}
 */
public class ConfigurationDefTest extends TestCase {

    private static final String CONFIG_NAME = "name";
    private static final String CONFIG_DESCRIPTION = "config description";
    private static final String OPTION_KEY = "key";
    private static final String OPTION_KEY2 = "key2";
    private static final String OPTION_VALUE = "val";
    private static final String OPTION_VALUE2 = "val2";
    private static final String OPTION_NAME = "option";
    private static final String COLLECTION_OPTION_NAME = "collection";
    private static final String MAP_OPTION_NAME = "map";
    private static final String OPTION_DESCRIPTION = "option description";

    public static class OptionTest extends StubBuildProvider {
        @Option(name=COLLECTION_OPTION_NAME, description=OPTION_DESCRIPTION)
        private Collection<String> mCollectionOption = new ArrayList<String>();

        @Option(name=MAP_OPTION_NAME, description=OPTION_DESCRIPTION)
        private Map<String, String> mMapOption = new HashMap<String, String>();

        @Option(name=OPTION_NAME, description=OPTION_DESCRIPTION)
        private String mOption;
    }

    private ConfigurationDef mConfigDef;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mConfigDef = new ConfigurationDef(CONFIG_NAME);
        mConfigDef.setDescription(CONFIG_DESCRIPTION);
        mConfigDef.addConfigObjectDef(Configuration.BUILD_PROVIDER_TYPE_NAME,
                OptionTest.class.getName());
    }

    /**
     * Test {@link ConfigurationDef#createConfiguration()} for a map option.
     */
    public void testCreateConfiguration_optionMap() throws ConfigurationException {
        mConfigDef.addOptionDef(MAP_OPTION_NAME, OPTION_KEY, OPTION_VALUE);
        mConfigDef.addOptionDef(MAP_OPTION_NAME, OPTION_KEY2, OPTION_VALUE2);
        IConfiguration config = mConfigDef.createConfiguration();
        OptionTest test = (OptionTest)config.getBuildProvider();
        assertNotNull(test.mMapOption);
        assertEquals(2, test.mMapOption.size());
        assertEquals(OPTION_VALUE, test.mMapOption.get(OPTION_KEY));
        assertEquals(OPTION_VALUE2, test.mMapOption.get(OPTION_KEY2));
    }

    /**
     * Test {@link ConfigurationDef#createConfiguration()} for a collection option.
     */
    public void testCreateConfiguration_optionCollection() throws ConfigurationException {
        mConfigDef.addOptionDef(COLLECTION_OPTION_NAME, null, OPTION_VALUE);
        mConfigDef.addOptionDef(COLLECTION_OPTION_NAME, null, OPTION_VALUE2);
        IConfiguration config = mConfigDef.createConfiguration();
        OptionTest test = (OptionTest)config.getBuildProvider();
        assertTrue(test.mCollectionOption.contains(OPTION_VALUE));
        assertTrue(test.mCollectionOption.contains(OPTION_VALUE2));
    }

    /**
     * Test {@link ConfigurationDef#createConfiguration()} for a String field.
     */
    public void testCreateConfiguration() throws ConfigurationException {
        mConfigDef.addOptionDef(OPTION_NAME, null, OPTION_VALUE);
        IConfiguration config = mConfigDef.createConfiguration();
        OptionTest test = (OptionTest)config.getBuildProvider();
        assertEquals(OPTION_VALUE, test.mOption);
    }

    /**
     * Test {@link ConfigurationDef#includeConfigDef(ConfigurationDef)} for two configs each with
     * one unique object.
     * <p/>
     */
    public void testIncludeConfigDef_combineObjects() throws ConfigurationException {
        ConfigurationDef def2 = new ConfigurationDef("def2");
        def2.addConfigObjectDef(Configuration.TEST_TYPE_NAME, HostTest.class.getName());
        mConfigDef.includeConfigDef(def2);
        IConfiguration config = mConfigDef.createConfiguration();
        assertTrue(config.getBuildProvider() instanceof OptionTest);
        assertTrue(config.getTests().get(0) instanceof HostTest);
    }

    /**
     * Test {@link ConfigurationDef#includeConfigDef(ConfigurationDef)} for two configs each with
     * defined options.
     * <p/>
     */
    public void testIncludeConfigDef_combineOptions() throws ConfigurationException {
        mConfigDef.addOptionDef(OPTION_NAME, null, OPTION_VALUE);
        ConfigurationDef def2 = new ConfigurationDef("def2");
        def2.addOptionDef(COLLECTION_OPTION_NAME, null, OPTION_VALUE);
        mConfigDef.includeConfigDef(def2);
        IConfiguration config = mConfigDef.createConfiguration();
        OptionTest test = (OptionTest)config.getBuildProvider();
        assertEquals(OPTION_VALUE, test.mOption);
        assertTrue(test.mCollectionOption.contains(OPTION_VALUE));
    }

    /**
     * Test {@link ConfigurationDef#includeConfigDef(ConfigurationDef)} for two configs each with
     * one unique object of the same type.
     * <p/>
     */
    public void testIncludeConfigDef_addObjects() throws ConfigurationException {
        mConfigDef.addConfigObjectDef(Configuration.TEST_TYPE_NAME,
                InstrumentationTest.class.getName());
        ConfigurationDef def2 = new ConfigurationDef("def2");
        def2.addConfigObjectDef(Configuration.TEST_TYPE_NAME, HostTest.class.getName());
        mConfigDef.includeConfigDef(def2);
        IConfiguration config = mConfigDef.createConfiguration();
        assertEquals(2, config.getTests().size());
        assertTrue(config.getTests().get(0) instanceof InstrumentationTest);
        assertTrue(config.getTests().get(1) instanceof HostTest);
    }

    /**
     * Test {@link ConfigurationDef#includeConfigDef(ConfigurationDef)} for two configs each with
     * one unique object of the same type - where that type only allows a single object.
     * <p/>
     * Expect {@link ConfigurationException}
     */
    public void testIncludeConfigDef_duplicateObjectsForType() throws ConfigurationException {
        ConfigurationDef def2 = new ConfigurationDef("def2");
        def2.addConfigObjectDef(Configuration.BUILD_PROVIDER_TYPE_NAME,
                StubBuildProvider.class.getName());
        mConfigDef.includeConfigDef(def2);
        try {
            mConfigDef.createConfiguration();
            fail("ConfigurationException not thrown");
        } catch (ConfigurationException e) {
            // expected
        }
    }

    /**
     * Test {@link ConfigurationDef#includeConfigDef(ConfigurationDef)} for two configs that define
     * the same class object.
     * <p/>
     * TODO: Currently this is allowed, but its somewhat undesirable since there is no way to
     * define different option values for the two objects.
     */
    public void testIncludeConfigDef_duplicateObjectsForList() throws ConfigurationException {
        mConfigDef.addConfigObjectDef(Configuration.TEST_TYPE_NAME,
                InstrumentationTest.class.getName());
        ConfigurationDef def2 = new ConfigurationDef("def2");
        def2.addConfigObjectDef(Configuration.TEST_TYPE_NAME, InstrumentationTest.class.getName());
        mConfigDef.includeConfigDef(def2);
        IConfiguration config = mConfigDef.createConfiguration();
        assertEquals(2, config.getTests().size());
        assertTrue(config.getTests().get(0) instanceof InstrumentationTest);
        assertTrue(config.getTests().get(1) instanceof InstrumentationTest);
    }
}
