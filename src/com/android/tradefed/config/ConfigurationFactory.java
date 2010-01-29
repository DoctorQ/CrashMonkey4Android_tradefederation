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

    private ConfigurationFactory() {
    }

    /**
     * Create the default {@link IConfiguration}, whose delegates will be no-op implementations.
     */
    public static IConfiguration createDefaultConfiguration() {
        return new DefaultConfiguration();
    }

    /**
     * Create the {@link IConfiguration} from given XML file.
     */
    public static IConfiguration createConfigurationFromXML(File xmlFile)
            throws ConfigurationException {
        throw new UnsupportedOperationException();
    }

    /**
     * Create the {@link IConfiguration} from command line arguments.
     *
     * @throws {@link ConfigurationException} if configuration could not be loaded
     */
    public static IConfiguration createConfigurationFromArgs(String[] args)
            throws ConfigurationException {
        // TODO: get configuration to use from name
        IConfiguration config = createDefaultConfiguration();

        // TODO: do this for all objects, not just Test
        ArgsOptionParser parser = new ArgsOptionParser(config.getTest());
        parser.parse(args);
        return config;
    }
}
