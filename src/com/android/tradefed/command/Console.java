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

import com.android.ddmlib.DdmPreferences;
import com.android.ddmlib.Log;
import com.android.ddmlib.Log.LogLevel;
import com.android.tradefed.config.ArgsOptionParser;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.Option;
import com.android.tradefed.log.LogRegistry;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * Main TradeFederation console providing user with the interface to interact
 * <p/>
 * Current has empty implementation, but future support will include commands such as
 * <ul>
 * <li>add a configuration to test
 * <li>list devices and their state
 * <li>list invocations in progress
 * <li>list configs in queue
 * <li>dump invocation log to file/stdout
 * <li>shutdown
 * </ul>
 */
public class Console {

    @Option(name = "file", description = "the path to file of configs to run")
    private File mFile = null;

    @Option(name = "help", description = "get command line usage info")
    private boolean mHelpMode = false;

    private ICommandScheduler mScheduler;

    Console() {
        this(new CommandScheduler());
    }

    /**
     * Create a {@link Console} with given scheduler.
     * <p/>
     * Exposed for unit testing
     */
    Console(ICommandScheduler scheduler) {
        mScheduler = scheduler;
    }

    /**
     * Sets the config file to use
     * <p/>
     * Exposed for unit testing
     */
    void setConfigFile(File file) {
        mFile = file;
    }

    /**
     * The main method to launch the console. Will keep running until shutdown command is issued.
     *
     * @param args
     */
    public void run(String[] args) {
        initLogging();

        try {
            ArgsOptionParser myParser = new ArgsOptionParser(this);
            myParser.parse(args);
            if (mHelpMode) {
                printHelp();
                return;
            }
            if (mFile != null) {
                createConfigFileParser().parseFile(mFile, mScheduler);
            }

            mScheduler.start();

            // TODO: launch console ui, and process user commands

            mScheduler.join();
        } catch (ConfigurationException e) {
            System.err.println(String.format("Failed to parse options: %s", e.getMessage()));
            printHelp();
        } catch (FileNotFoundException e) {
            System.err.println(String.format("Provided file %s does not exist",
                    mFile.getAbsolutePath()));
        } catch (IOException e) {
            System.err.println(String.format("Provided file %s cannot be read",
                    mFile.getAbsolutePath()));
            e.printStackTrace();
        } catch (InterruptedException e) {
            // ignore
        }
    }

    /**
     * Initializes the ddmlib log.
     */
    void initLogging() {
        DdmPreferences.setLogLevel(LogLevel.VERBOSE.getStringValue());
        Log.setLogOutput(LogRegistry.getLogRegistry());
    }

    /**
     * Factory method for creating a {@link ConfigFileParser}.
     * <p/>
     * Exposed for unit testing.
     */
    ConfigFileParser createConfigFileParser() {
        return new ConfigFileParser();
    }

    /**
     * Output command line help info to stdout.
     */
    private void printHelp() {
        System.out.println("Run TradeFederation console.");
        System.out.println("Options:");
        System.out.print(ArgsOptionParser.getOptionHelp(this.getClass()));
    }

    public static void main(final String[] mainArgs) {
        Console console = new Console();
        console.run(mainArgs);
    }
}
