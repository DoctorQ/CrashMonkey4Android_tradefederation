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

import com.android.ddmlib.Log;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.IOException;
import java.io.InputStream;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

/**
 * Parses a configuration.xml file.
 * <p/>
 * See TODO for expected format
 */
class ConfigurationXmlParser {

    private static final String LOG_TAG = "ConfigurationDef";

    /**
     * SAX callback object. Handles parsing data from the xml tags.
     */
    private static class ConfigHandler extends DefaultHandler {

        private static final String OBJECT_TAG = "object";
        private static final String OPTION_TAG = "option";
        private static final Object CONFIG_TAG = "configuration";

        private ConfigurationDef mConfigDef;

        ConfigHandler(String name) {
            mConfigDef = new ConfigurationDef(name);
        }

        ConfigurationDef getParsedDef() {
            return mConfigDef;
        }

        @Override
        public void startElement(String uri, String localName, String name, Attributes attributes)
                throws SAXException {
            if (OBJECT_TAG.equals(localName)) {
                final String objectName = attributes.getValue("name");
                addObject(objectName, attributes);
            } else if (OPTION_TAG.equals(localName)) {
                String optionName = attributes.getValue("name");
                if (optionName == null) {
                    throwException("Missing 'name' attribute for option");
                }
                String optionValue = attributes.getValue("value");
                if (optionValue == null) {
                    throwException("Missing 'value' attribute for option");
                }
                mConfigDef.addOptionDef(optionName, optionValue);
            } else if (Configuration.getConfigObjNames().contains(localName)) {
                // tag is a built in config object
                addObject(localName, attributes);
            } else if (CONFIG_TAG.equals(localName)) {
                String description = attributes.getValue("description");
                if (description != null) {
                    mConfigDef.setDescription(description);
                }
            }
            else {
                Log.w(LOG_TAG, String.format("Unrecognized tag '%s' in configuration", localName));
            }
        }

        void addObject(String objectName, Attributes attributes) throws SAXException {
            String className = attributes.getValue("class");
            if (className == null) {
                throwException(String.format("Missing class attribute for object %s", objectName));
            }
            mConfigDef.addConfigObjectDef(objectName, className);
        }

        private void throwException(String reason) throws SAXException {
            throw new SAXException(new ConfigurationException(String.format(
                    "Failed to parse config xml '%s'. Reason: %s", mConfigDef.getName(), reason)));
        }
    }

    ConfigurationXmlParser() {
    }

    /**
     * Parses out configuration data contained in given input.
     * <p/>
     * Currently performs limited error checking.
     *
     * @param name the name of the configuration
     * @param xmlInput the configuration xml to parse
     * @throws ConfigurationException if input could not be parsed or had invalid format
     */
    ConfigurationDef parse(String name, InputStream xmlInput) throws ConfigurationException  {
        try {
            SAXParserFactory parserFactory = SAXParserFactory.newInstance();
            parserFactory.setNamespaceAware(true);
            SAXParser parser;
            parser = parserFactory.newSAXParser();

            ConfigHandler configHandler = new ConfigHandler(name);
            parser.parse(new InputSource(xmlInput), configHandler);
            return configHandler.getParsedDef();
        } catch (ParserConfigurationException e) {
            throwConfigException(name, e);
        } catch (SAXException e) {
            throwConfigException(name, e);
        } catch (IOException e) {
            throwConfigException(name, e);
        }
        throw new ConfigurationException("should never reach here");
    }

    private void throwConfigException(String configName, Throwable e)
            throws ConfigurationException {
        if (e.getCause() instanceof ConfigurationException) {
            throw (ConfigurationException)e.getCause();
        }
        throw new ConfigurationException(String.format("Failed to parse config xml '%s'",
                configName), e);
    }
}
