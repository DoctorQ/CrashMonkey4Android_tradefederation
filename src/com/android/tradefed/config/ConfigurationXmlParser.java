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
        private static final String INCLUDE_TAG = "include";
        private static final String CONFIG_TAG = "configuration";

        private ConfigurationDef mConfigDef;
        private String mCurrentConfigObject;
        private final IConfigDefLoader mConfigDefLoader;
        private Boolean isLocalConfig = null;

        ConfigHandler(String name, IConfigDefLoader loader) {
            mConfigDef = new ConfigurationDef(name);
            mConfigDefLoader = loader;
        }

        ConfigurationDef getParsedDef() {
            return mConfigDef;
        }

        @Override
        public void startElement(String uri, String localName, String name, Attributes attributes)
                throws SAXException {
            if (OBJECT_TAG.equals(localName)) {
                final String objectTypeName = attributes.getValue("type");
                addObject(objectTypeName, attributes);
            } else if (Configuration.isBuiltInObjType(localName)) {
                // tag is a built in local config object
                if (isLocalConfig == null) {
                    isLocalConfig = true;
                } else if (!isLocalConfig) {
                    throwException(String.format(
                            "Attempted to specify local object '%s' for global config!",
                            localName));
                }
                addObject(localName, attributes);
            } else if (GlobalConfiguration.isBuiltInObjType(localName)) {
                // tag is a built in global config object
                if (isLocalConfig == null) {
                    // FIXME: config type should be explicit rather than inferred
                    isLocalConfig = false;
                } else if (isLocalConfig) {
                    throwException(String.format(
                            "Attempted to specify global object '%s' for local config!",
                            localName));
                }
                addObject(localName, attributes);
            } else if (OPTION_TAG.equals(localName)) {
                String optionName = attributes.getValue("name");
                if (optionName == null) {
                    throwException("Missing 'name' attribute for option");
                }

                String optionKey = attributes.getValue("key");
                // Key is optional at this stage.  If it's actually required, another stage in the
                // configuration validation will throw an exception.

                String optionValue = attributes.getValue("value");
                if (optionValue == null) {
                    throwException("Missing 'value' attribute for option");
                }
                if (mCurrentConfigObject != null) {
                    // option is declared within a config object - namespace it with object class
                    // name
                    optionName = String.format("%s%c%s", mCurrentConfigObject,
                            OptionSetter.NAMESPACE_SEPARATOR, optionName);
                }
                mConfigDef.addOptionDef(optionName, optionKey, optionValue);
            } else if (CONFIG_TAG.equals(localName)) {
                String description = attributes.getValue("description");
                if (description != null) {
                    mConfigDef.setDescription(description);
                }
            } else if (INCLUDE_TAG.equals(localName)) {
                String includeName = attributes.getValue("name");
                if (includeName == null) {
                    throwException("Missing 'name' attribute for include");
                }
                try {
                    ConfigurationDef includedDef = mConfigDefLoader.getConfigurationDef(
                            includeName);
                    mConfigDef.includeConfigDef(includedDef);
                } catch (ConfigurationException e) {
                    throw new SAXException(e);
                }

            } else {
                Log.w(LOG_TAG, String.format("Unrecognized tag '%s' in configuration", localName));
            }
        }

        @Override
        public void endElement (String uri, String localName, String qName) throws SAXException {
            if (OBJECT_TAG.equals(localName) || Configuration.isBuiltInObjType(localName)) {
                mCurrentConfigObject = null;
            }
        }

        void addObject(String objectTypeName, Attributes attributes) throws SAXException {
            String className = attributes.getValue("class");
            if (className == null) {
                throwException(String.format("Missing class attribute for object %s",
                        objectTypeName));
            }
            int classCount = mConfigDef.addConfigObjectDef(objectTypeName, className);
            mCurrentConfigObject = String.format("%s%c%d", className,
                    OptionSetter.NAMESPACE_SEPARATOR, classCount);
        }

        private void throwException(String reason) throws SAXException {
            throw new SAXException(new ConfigurationException(String.format(
                    "Failed to parse config xml '%s'. Reason: %s", mConfigDef.getName(), reason)));
        }
    }

    private final IConfigDefLoader mConfigDefLoader;

    ConfigurationXmlParser(IConfigDefLoader loader) {
        mConfigDefLoader = loader;
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
    ConfigurationDef parse(String name, InputStream xmlInput) throws ConfigurationException {
        try {
            SAXParserFactory parserFactory = SAXParserFactory.newInstance();
            parserFactory.setNamespaceAware(true);
            SAXParser parser = parserFactory.newSAXParser();
            ConfigHandler configHandler = new ConfigHandler(name, mConfigDefLoader);
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
