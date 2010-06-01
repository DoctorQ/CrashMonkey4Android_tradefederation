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
import java.io.PrintStream;

/**
 *
 */
public interface IConfigurationFactory {

    /**
     * Gets the built-in {@link IConfiguration} with given name.
     *
     * @param name the unique name of the {@link IConfiguration}
     * @return the {@link IConfiguration}
     * @throws ConfigurationException if the {@link IConfiguration} could not be loaded.
     */
    public IConfiguration getConfiguration(String name) throws ConfigurationException;

    /**
     * Create the {@link IConfiguration} from given XML file.
     *
     * @param xmlFile the {@link File} to read configuration from
     * @return the loaded {@link IConfiguration}.
     * @throws {@link ConfigurationException} if configuration could not be loaded
     */
    public IConfiguration createConfigurationFromXML(File xmlFile) throws ConfigurationException;

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
    public IConfiguration createConfigurationFromArgs(String[] args) throws ConfigurationException;

    /**
     * Populate given configuration with arg values
     *
     * @param args
     * @param config
     * @return
     * @throws ConfigurationException
     */
    public IConfiguration populateConfigWithArgs(String[] args, IConfiguration config)
            throws ConfigurationException;

    /**
     * Prints help output.
     * <p/>
     * If a configuration argument is specified prints the help info specific to that
     * configuration. Otherwise prints a generic help info, and lists all available configurations.
     *
     * @param args the command line arguments
     * @param out the {@link PrintStream} to dump output to
     */
    public void printHelp(String[] args, PrintStream out);

}
