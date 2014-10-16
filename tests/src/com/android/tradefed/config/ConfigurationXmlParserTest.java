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

import junit.framework.TestCase;

import org.easymock.EasyMock;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * Unit tests for {@link ConfigurationXmlParser}.
 */
public class ConfigurationXmlParserTest extends TestCase {

    private ConfigurationXmlParser xmlParser;
    private IConfigDefLoader mMockLoader;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mMockLoader = EasyMock.createMock(IConfigDefLoader.class);
        xmlParser = new ConfigurationXmlParser(mMockLoader);
    }

    /**
     * Normal case test for {@link ConfigurationXmlParser#parse(String, InputStream)}.
     */
    public void testParse() throws ConfigurationException {
        final String normalConfig =
            "<configuration description=\"desc\" >\n" +
            "  <test class=\"junit.framework.TestCase\">\n" +
            "    <option name=\"opName\" value=\"val\" />\n" +
            "  </test>\n" +
            "</configuration>";
        final String configName = "config";
        ConfigurationDef configDef = xmlParser.parse(configName, getStringAsStream(normalConfig));
        assertEquals(configName, configDef.getName());
        assertEquals("desc", configDef.getDescription());
        assertEquals("junit.framework.TestCase", configDef.getObjectClassMap().get("test").get(0));
        assertEquals("junit.framework.TestCase:1:opName", configDef.getOptionList().get(0).name);
        assertEquals("val", configDef.getOptionList().get(0).value);
    }

    /**
     * Test parsing xml with a global option
     */
    public void testParse_globalOption() throws ConfigurationException {
        final String normalConfig =
            "<configuration description=\"desc\" >\n" +
            "  <option name=\"opName\" value=\"val\" />\n" +
            "  <test class=\"junit.framework.TestCase\">\n" +
            "  </test>\n" +
            "</configuration>";
        final String configName = "config";
        ConfigurationDef configDef = xmlParser.parse(configName, getStringAsStream(normalConfig));
        assertEquals(configName, configDef.getName());
        assertEquals("desc", configDef.getDescription());
        assertEquals("junit.framework.TestCase", configDef.getObjectClassMap().get("test").get(0));
        // the non-namespaced option value should be used
        assertEquals("opName", configDef.getOptionList().get(0).name);
        assertEquals("val", configDef.getOptionList().get(0).value);
    }

    /**
     * Test parsing xml with repeated type/class pairs
     */
    public void testParse_multiple() throws ConfigurationException {
        final String normalConfig =
            "<configuration description=\"desc\" >\n" +
            "  <test class=\"junit.framework.TestCase\">\n" +
            "    <option name=\"opName\" value=\"val1\" />\n" +
            "  </test>\n" +
            "  <test class=\"junit.framework.TestCase\">\n" +
            "    <option name=\"opName\" value=\"val2\" />\n" +
            "  </test>\n" +
            "</configuration>";
        final String configName = "config";
        ConfigurationDef configDef = xmlParser.parse(configName, getStringAsStream(normalConfig));
        assertEquals(configName, configDef.getName());
        assertEquals("desc", configDef.getDescription());

        assertEquals("junit.framework.TestCase", configDef.getObjectClassMap().get("test").get(0));
        assertEquals("junit.framework.TestCase:1:opName", configDef.getOptionList().get(0).name);
        assertEquals("val1", configDef.getOptionList().get(0).value);

        assertEquals("junit.framework.TestCase", configDef.getObjectClassMap().get("test").get(1));
        assertEquals("junit.framework.TestCase:2:opName", configDef.getOptionList().get(1).name);
        assertEquals("val2", configDef.getOptionList().get(1).value);
    }

    /**
     * Test parsing a object tag missing a attribute.
     */
    public void testParse_objectMissingAttr() {
        final String config =
            "<object name=\"foo\" />";
        try {
            xmlParser.parse("name", getStringAsStream(config));
            fail("ConfigurationException not thrown");
        } catch (ConfigurationException e) {
            // expected
        }
    }

    /**
     * Test parsing a option tag missing a attribute.
     */
    public void testParse_optionMissingAttr() {
        final String config =
            "<option name=\"foo\" />";
        try {
            xmlParser.parse("name", getStringAsStream(config));
            fail("ConfigurationException not thrown");
        } catch (ConfigurationException e) {
            // expected
        }
    }

    /**
     * Test parsing a object tag.
     */
    public void testParse_object() throws ConfigurationException {
        final String config =
            "<object type=\"foo\" class=\"junit.framework.TestCase\" />";
        ConfigurationDef configDef = xmlParser.parse("name", getStringAsStream(config));
        assertEquals("junit.framework.TestCase", configDef.getObjectClassMap().get("foo").get(0));
    }

    /**
     * Test parsing a include tag.
     */
    public void testParse_include() throws ConfigurationException {
        final String includedConfig = "<object type=\"foo\" class=\"junit.framework.TestCase\" />";
        String includedName = "includeme";
        ConfigurationDef includedConfigDef = xmlParser.parse(includedName,
                getStringAsStream(includedConfig));
        EasyMock.expect(mMockLoader.getConfigurationDef(includedName)).andReturn(includedConfigDef);
        EasyMock.replay(mMockLoader);
        final String config = "<include name=\"includeme\" />";
        ConfigurationDef configDef = xmlParser.parse("name", getStringAsStream(config));
        assertEquals("junit.framework.TestCase", configDef.getObjectClassMap().get("foo").get(0));
    }

    /**
     * Test parsing a include tag where named config does not exist
     */
    public void testParse_includeMissing() throws ConfigurationException {
        String includedName = "non-existent";
        ConfigurationException exception = new ConfigurationException("I don't exist");
        EasyMock.expect(mMockLoader.getConfigurationDef(includedName)).andThrow(exception);
        EasyMock.replay(mMockLoader);
        final String config = String.format("<include name=\"%s\" />", includedName);
        try {
            xmlParser.parse("name", getStringAsStream(config));
            fail("ConfigurationException not thrown");
        } catch (ConfigurationException e) {
            // expected
        }
    }

    /**
     * Test parsing invalid xml.
     */
    public void testParse_xml() throws ConfigurationException {
        final String config = "blah";
        try {
            xmlParser.parse("name", getStringAsStream(config));
            fail("ConfigurationException not thrown");
        } catch (ConfigurationException e) {
            // expected
        }
    }

    private InputStream getStringAsStream(String input) {
        return new ByteArrayInputStream(input.getBytes());
    }
}
