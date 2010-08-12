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
package com.android.tradefed.command;

import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.util.QuotationAwareTokenizer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

/**
 * Parser for file that contains set of configs.
 * <p/>
 * The syntax of the given file should be series of lines. Each line is one configuration plus its
 * options, delimited by whitespace:
 * <pre>
 *   [options] config-name
 *   [options] config-name2
 *   ...
 * </pre>
 */
class ConfigFileParser {

    /**
     * Parse configs contained in file and adds them to given scheduler.
     *
     * @param file the {@link File} to parse
     * @param scheduler the {@link ICommandScheduler} to add configs to
     * @throws IOException if failed to read file
     * @throws ConfigurationException if content of file could not be parsed
     */
    public void parseFile(File file, ICommandScheduler scheduler) throws IOException,
            ConfigurationException {
        BufferedReader fileReader = createConfigFileReader(file);
        String line = null;
        while ((line = fileReader.readLine()) != null) {
            line = line.trim();
            // ignore empty or commented lines
            if (line.length() > 0 && !line.startsWith("#")) {
                try {
                    String[] args = QuotationAwareTokenizer.tokenizeLine(line);
                    scheduler.addConfig(args);
                } catch (IllegalArgumentException e) {
                    throw new ConfigurationException(e.getMessage());
                }
            }
        }
    }

    /**
     * Create a reader for the config file data.
     * <p/>
     * Exposed for unit testing.
     *
     * @param file the config data {@link File}
     * @return the {@link BufferedReader}
     * @throws IOException if failed to read data
     */
    BufferedReader createConfigFileReader(File file) throws IOException {
        return new BufferedReader(new FileReader(file));
    }
}
