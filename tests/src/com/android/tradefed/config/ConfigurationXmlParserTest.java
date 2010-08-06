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

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import junit.framework.TestCase;

/**
 * Unit tests for {@link ConfigurationXmlParser}.
 */
public class ConfigurationXmlParserTest extends TestCase {

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
        ConfigurationXmlParser xmlParser = new ConfigurationXmlParser();
        final String configName = "config";
        ConfigurationDef configDef = xmlParser.parse(configName, getStringAsStream(normalConfig));
        assertEquals(configName, configDef.getName());
        assertEquals("desc", configDef.getDescription());
        assertEquals("junit.framework.TestCase", configDef.getObjectClassMap().get("test").get(0));
        assertEquals("opName", configDef.getOptionList().get(0).name);
        assertEquals("val", configDef.getOptionList().get(0).value);
    }

    /**
     * Test parsing a object tag missing a attribute.
     */
    public void testParse_objectMissingAttr() {
        final String config =
            "<object name=\"foo\" />";
        ConfigurationXmlParser xmlParser = new ConfigurationXmlParser();
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
        ConfigurationXmlParser xmlParser = new ConfigurationXmlParser();
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
            "<object name=\"foo\" class=\"junit.framework.TestCase\" />";
        ConfigurationXmlParser xmlParser = new ConfigurationXmlParser();
        ConfigurationDef configDef = xmlParser.parse("name", getStringAsStream(config));
        assertEquals("junit.framework.TestCase", configDef.getObjectClassMap().get("foo").get(0));
    }

    /**
     * Test parsing invalid xml.
     */
    public void testParse_xml() throws ConfigurationException {
        final String config = "blah";
        ConfigurationXmlParser xmlParser = new ConfigurationXmlParser();
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
