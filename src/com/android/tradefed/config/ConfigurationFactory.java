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

import java.io.File;

/**
 * Factory for creating {@link IConfiguration}'s.
 */
public final class ConfigurationFactory {

    static final String INSTRUMENT_CONFIG = "instrument";
    static final String HOST_TEST_CONFIG = "host";

    private ConfigurationFactory() {
    }

    /**
     * Gets the built-in {@link IConfiguration} with given name
     *
     * @param name the unique name of the {@link IConfiguration}
     * @return the {@link IConfiguration}
     * @throws ConfigurationException if the {@link IConfiguration} could not be loaded.
     */
    public static IConfiguration getConfiguration(String name) throws ConfigurationException {
        // TODO: hardcoded names for now
        if (INSTRUMENT_CONFIG.equals(name)) {
            return new InstrumentConfiguration();
        } else if (HOST_TEST_CONFIG.equals(name)) {
            return new HostTestConfiguration();
        }
        throw new ConfigurationException(String.format("Could not find configuration with name %s",
                name));
    }


    /**
     * Create the {@link IConfiguration} from given XML file.
     *
     * @param xmlFile the {@link File} to read configuration from
     * @return the loaded {@link IConfiguration}.
     * @throws {@link ConfigurationException} if configuration could not be loaded
     */
    public static IConfiguration createConfigurationFromXML(File xmlFile)
            throws ConfigurationException {
        throw new UnsupportedOperationException();
    }

    /**
     * Create the {@link IConfiguration} from command line arguments.
     * <p/>
     * Expected format is [options] <configuration name>.
     *
     * @param args the command line arguments
     * @return the loaded {@link IConfiguration}. The delegate object {@link Option} fields have
     * been populated with values in args.
     * @throws {@link ConfigurationException} if configuration could not be loaded
     */
    public static IConfiguration createConfigurationFromArgs(String[] args)
            throws ConfigurationException {
        if (args.length == 0) {
            throw new ConfigurationException("Configuration to run was not specified");
        }
        // last argument is config name
        final String configName = args[args.length-1];
        IConfiguration config = getConfiguration(configName);

        for (Object configObject : config.getConfigurationObjects()) {
            ArgsOptionParser parser = new ArgsOptionParser(configObject);
            parser.parse(args);
        }
        // TODO: it would be nice to at least print a warning about unused args, or duplicate option
        // names across objects
        return config;
    }
}
