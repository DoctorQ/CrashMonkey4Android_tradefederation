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

/**
 *
 */
public interface IConfigurationFactory {

    /**
     * Gets a {@link IConfiguration}
     *
     * @param name the unique name of a built-in {@link IConfiguration} or a file path to a
     *            configuration xml
     * @return the {@link IConfiguration}
     * @throws ConfigurationException if the {@link IConfiguration} could not be loaded.
     */
    public IConfiguration getConfiguration(String name) throws ConfigurationException;

    /**
     * Create the {@link IConfiguration} from command line arguments.
     * <p/>
     * Expected format is [options] <configuration name OR file path>.
     *
     * @param args the command line arguments
     * @return the loaded {@link IConfiguration}. The delegate object {@link Option} fields have
     *         been populated with values in args.
     * @throws {@link ConfigurationException} if configuration could not be loaded
     */
    public IConfiguration createConfigurationFromArgs(String[] args) throws ConfigurationException;

    /**
     * Create the {@link IConfiguration} and populate additional {@link Option} objects from command
     * line arguments.
     * <p/>
     * Expected format is [options] <configuration name OR file path>.
     *
     * @param args the command line arguments
     * @param additionalOptionSources additional objects with {@link Option} fields to set
     * @return the loaded {@link IConfiguration}. The delegate object {@link Option} fields have
     *         been populated with values in args.
     * @throws {@link ConfigurationException} if configuration could not be loaded
     */
    public IConfiguration createConfigurationFromArgs(String[] args,
            Object... additionalOptionSources) throws ConfigurationException;

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

    /**
     * Prints help output.
     * <p/>
     * If a configuration argument is specified prints the help info specific to that
     * configuration. Otherwise prints a generic help info, and lists all available configurations.
     *
     * @param args the command line arguments
     * @param additionalOptionSources additional objects with {@link Option} fields to print
     *      help for
     * @param out the {@link PrintStream} to dump output to
     */
    public void printHelp(String[] args, PrintStream out, Class<?>... additionalOptionSources);

}
