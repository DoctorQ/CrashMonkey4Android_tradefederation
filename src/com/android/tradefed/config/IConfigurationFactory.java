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
 * Factory for creating {@link IConfiguration}s
 */
public interface IConfigurationFactory {

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
     * Prints help output for this factory.
     * <p/>
     * Prints a generic help info, and lists all available configurations.
     *
     * @param out the {@link PrintStream} to dump output to
     */
    public void printHelp(PrintStream out);

}
