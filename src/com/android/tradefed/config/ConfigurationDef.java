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

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Holds a record of a configuration, its associated objects and their options.
 */
public class ConfigurationDef {

    /** a map of names to config object class names. */
    private final Map<String, String> mObjectClassMap;
    /** a list of option name/value pairs. */
    private final List<OptionDef> mOptionList;

    static class OptionDef {
        final String name;
        final String value;

        OptionDef(String optionName, String optionValue) {
            this.name = optionName;
            this.value = optionValue;
        }
    }

    /** the unique name of the configuration definition */
    private final String mName;

    /** a short description of the configuration definition */
    private String mDescription = "";

    public ConfigurationDef(String name) {
        mName = name;
        mObjectClassMap = new HashMap<String, String>();
        mOptionList = new ArrayList<OptionDef>();
    }

    /**
     * Returns a short description of the configuration
     */
    public String getDescription() {
        return mDescription;
    }

    /**
     * Sets the configuration definition description
     */
    void setDescription(String description) {
        mDescription = description;
    }

    /**
     * Adds a config object to the definition
     * @param name the config object name
     * @param className the class name of the config object
     */
    void addConfigObjectDef(String name, String className) {
        mObjectClassMap.put(name, className);
    }

    /**
     * Adds option to the definition
     * @param optionName the name of the option
     * @param optionValue the option value
     */
    void addOptionDef(String optionName, String optionValue) {
        mOptionList.add(new OptionDef(optionName, optionValue));
    }

    /**
     * Get the object name-class map.
     * <p/>
     * Exposed for unit testing
     */
    Map<String, String> getObjectClassMap() {
        return mObjectClassMap;
    }

    /**
     * Get the option name-value map.
     * <p/>
     * Exposed for unit testing
     */
    List<OptionDef> getOptionList() {
        return mOptionList;
    }

    /**
     * Creates a configuration from the info stored in this definition
     * @return the created {@link IConfiguration}
     * @throws ConfigurationException if configuration could not be created
     */
    IConfiguration createConfiguration() throws ConfigurationException {
        Map<String, Object> configObjectMap = new HashMap<String, Object>(mObjectClassMap.size());
        for (Map.Entry<String, String> objClassEntry : mObjectClassMap.entrySet()) {
            Object configObject = createObject(objClassEntry.getKey(), objClassEntry.getValue());
            configObjectMap.put(objClassEntry.getKey(), configObject);
        }
        OptionSetter setter = new OptionSetter(configObjectMap.values());
        for (OptionDef optionEntry : mOptionList) {
            setter.setOptionValue(optionEntry.name, optionEntry.value);
        }

        return new Configuration(configObjectMap);
    }

    /**
     * Gets the name of this configuration definition
     * @return
     */
    public String getName() {
        return mName;
    }

    /**
     * Outputs a command line usage help text for this configuration to given printStream.
     *
     * @param out the {@link PrintStream} to use.
     * @throws {@link ConfigurationException}
     */
    public void printCommandUsage(PrintStream out) throws ConfigurationException {
        out.println(String.format("'%s' configuration: %s", getName(), getDescription()));
        out.println();
        for (Map.Entry<String, String> configObjectEntry : mObjectClassMap.entrySet()) {
            String optionHelp = printOptionsForObject(configObjectEntry.getKey(),
                    configObjectEntry.getValue());
            // only print help for object if optionHelp is non zero length
            if (optionHelp.length() > 0) {
                out.printf("  %s options:", configObjectEntry.getKey());
                out.println();
                out.print(optionHelp);
                out.println();
            }
        }
    }

    /**
     * Prints out the available config options for given configuration object.
     *
     * @param objectName the name of the object. Used to generate more descriptive error messages
     * @param className the class name of the object to load
     * @return a {@link String} of option help text
     * @throws ConfigurationException
     */
    private String printOptionsForObject(String objectName, String objectClass)
            throws ConfigurationException {
        final Class<?> optionClass = getClassForObject(objectName, objectClass);
        return ArgsOptionParser.getOptionHelp(optionClass);
    }

    /**
     * Creates a config object associated with this definition.
     *
     * @param objectName the name of the object. Used to generate more descriptive error messages
     * @param className the class name of the object to load
     * @return the config object
     * @throws ConfigurationException if config object could not be created
     */
    private Object createObject(String objectName, String className) throws ConfigurationException {
        try {
            Class<?> objectClass = getClassForObject(objectName, className);
            Object configObject = objectClass.newInstance();
            return configObject;
        } catch (InstantiationException e) {
            throw new ConfigurationException(String.format(
                    "Could not instantiate class %s for config object name %s", className,
                    objectName), e);
        } catch (IllegalAccessException e) {
            throw new ConfigurationException(String.format(
                    "Could not access class %s for config object name %s", className, objectName),
                    e);
        }
    }

    /**
     * Loads the class for the given the config object associated with this definition.
     *
     * @param objectName the name of the object. Used to generate more descriptive error messages
     * @param className the class name of the object to load
     * @return the config object populated with default option values
     * @throws ConfigurationException if config object could not be created
     */
    private Class<?> getClassForObject(String objectName, String className)
            throws ConfigurationException {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new ConfigurationException(String.format(
                    "Could not find class %s for config object name %s", className, objectName), e);
        }
    }
}
