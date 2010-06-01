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

import java.io.File;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.Hashtable;
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
        if (sInstance  == null) {
            sInstance = new ConfigurationFactory();
        }
        return sInstance;
    }

    /**
     * {@inheritDoc}
     */
    public IConfiguration getConfiguration(String name)
            throws ConfigurationException {
        ConfigurationDef def = getConfigurationDef(name);
        return def.createConfiguration();
    }

    private ConfigurationDef getConfigurationDef(String name) throws ConfigurationException {
        ConfigurationDef def = mConfigDefMap.get(name);
        if (def == null) {
            def = loadConfiguration(name);
            mConfigDefMap.put(name, def);
        }
        return def;
    }

    private ConfigurationDef loadConfiguration(String name) throws ConfigurationException {
        Log.i(LOG_TAG, String.format("Loading configuration '%s'", name));
        InputStream configStream = getClass().getResourceAsStream(
                String.format("/config/%s.xml", name));
        if (configStream == null) {
            throw new ConfigurationException(String.format("Could not find configuration '%s'",
                    name));
        }
        ConfigurationXmlParser parser = new ConfigurationXmlParser();
        return parser.parse(name, configStream);
    }

    /**
     * {@inheritDoc}
     */
    public IConfiguration createConfigurationFromXML(File xmlFile)
            throws ConfigurationException {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     */
    public IConfiguration createConfigurationFromArgs(String[] args)
            throws ConfigurationException {
        if (args.length == 0) {
            throw new ConfigurationException("Configuration to run was not specified");
        }
        IConfiguration config = getConfiguration(getConfigNameFromArgs(args));
        return populateConfigWithArgs(args, config);
    }

    /**
     * Retrieve the configuration name for an array of input arguments.
     *
     * @param args the input arguments. Must be non-empty
     * @return the configuration name
     */
    private String getConfigNameFromArgs(String[] args) {
        // last argument is config name
        return args[args.length-1];
    }

    /**
     * {@inheritDoc}
     */
    public IConfiguration populateConfigWithArgs(String[] args, IConfiguration config)
            throws ConfigurationException {
        ArgsOptionParser parser = new ArgsOptionParser(config.getConfigurationObjects());
        parser.parse(args);
        return config;
    }

    /**
     * {@inheritDoc}
     */
    public void printHelp(String[] args, PrintStream out) {
        out.println("Usage: [options] <configuration_name>");
        out.println();
        // expected args is either just "--help", or "--help <configname>"
        if (args.length > 1) {
            String configName = getConfigNameFromArgs(args);
            try {
                ConfigurationDef def = getConfigurationDef(configName);
                def.printCommandUsage(out);
                return;
            } catch (ConfigurationException e) {
                out.println(String.format("Could not load help for config with name '%s'",
                        configName));
            }
        }
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
