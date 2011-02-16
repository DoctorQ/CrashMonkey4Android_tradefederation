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

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

/**
 * Factory for creating {@link IConfiguration}.
 */
public class ConfigurationFactory implements IConfigurationFactory {

    private static final String LOG_TAG = "ConfigurationFactory";
    private static IConfigurationFactory sInstance = null;

    static final String INSTRUMENT_CONFIG = "instrument";
    static final String HOST_TEST_CONFIG = "host";
    static final String TEST_DEF_CONFIG = "testdef";

    static final String[] sDefaultConfigs = {INSTRUMENT_CONFIG, HOST_TEST_CONFIG, TEST_DEF_CONFIG};

    private Map<String, ConfigurationDef> mConfigDefMap;

    private ConfigurationFactory() {
        mConfigDefMap = new Hashtable<String, ConfigurationDef>();
    }

    /**
     * Get the singleton {@link IConfigurationFactory} instance.
     */
    public static IConfigurationFactory getInstance() {
        if (sInstance == null) {
            sInstance = new ConfigurationFactory();
        }
        return sInstance;
    }

    private ConfigurationDef getConfigurationDef(String name) throws ConfigurationException {
        ConfigurationDef def = mConfigDefMap.get(name);
        if (def == null) {
            def = loadConfiguration(name);
            mConfigDefMap.put(name, def);
        }
        return def;
    }

    /**
     * Loads a configuration.
     *
     * @param name the name of a built-in configuration to load or a file path to configuration xml
     *            to load
     * @return the loaded {@link ConfigurationDef}
     * @throws ConfigurationException if a configuration with given name/file path cannot be loaded
     *             or parsed
     */
    private ConfigurationDef loadConfiguration(String name) throws ConfigurationException {
        Log.i(LOG_TAG, String.format("Loading configuration '%s'", name));
        InputStream configStream = getClass().getResourceAsStream(
                String.format("/config/%s.xml", name));
        if (configStream == null) {
            // now try to load from file
            try {
                configStream = new FileInputStream(name);
            } catch (FileNotFoundException e) {
                throw new ConfigurationException(String.format("Could not find configuration '%s'",
                        name));
            }
        }
        // buffer input for performance - just in case config file is large
        BufferedInputStream bufStream = new BufferedInputStream(configStream);
        ConfigurationXmlParser parser = new ConfigurationXmlParser();
        return parser.parse(name, bufStream);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IConfiguration createConfigurationFromArgs(String[] arrayArgs)
            throws ConfigurationException {
        if (arrayArgs.length == 0) {
            throw new ConfigurationException("Configuration to run was not specified");
        }
        List<String> listArgs = new ArrayList<String>(arrayArgs.length);
        listArgs.addAll(Arrays.asList(arrayArgs));
        // last arg is config name
        final String configName = listArgs.remove(listArgs.size()-1);
        ConfigurationDef configDef = getConfigurationDef(configName);
        IConfiguration config = configDef.createConfiguration();
        config.setOptionsFromCommandLineArgs(listArgs);

        return config;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void printHelp(PrintStream out) {
        // print general help
        out.println("Use --help <configuration_name> to get list of options for a configuration");
        out.println();
        // TODO: unfortunately, no easy way to find all available configurations
        // just print out list of loaded configurations
        out.println("See the res/config folder for available configurations.");
        out.println("Some available configurations include:");
        // load the default configs first
        loadDefaultConfigs();
        for (ConfigurationDef def: mConfigDefMap.values()) {
            out.printf("  %s: %s", def.getName(), def.getDescription());
            out.println();
        }
    }

    private void loadDefaultConfigs() {
        for (String config: sDefaultConfigs) {
            try {
                getConfigurationDef(config);
            } catch (ConfigurationException e) {
                Log.w(LOG_TAG, String.format("Could not load default config with name '%s'",
                        config));
            }
        }
    }
}
