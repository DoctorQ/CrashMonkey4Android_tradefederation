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
import com.android.tradefed.device.DeviceManager;
import com.android.tradefed.device.IDeviceManager;
import com.android.tradefed.invoker.ITestInvocation;
import com.android.tradefed.log.LogRegistry;
import com.android.tradefed.util.QuotationAwareTokenizer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;

// not importing java.io.Console because of class name conflict

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

    private static final String LOG_TAG = "Console";
    private static final String CONSOLE_PROMPT = "tf >";

    @Option(name = "file", description = "the path to file of configs to run")
    private File mFile = null;

    @Option(name = "help", description = "get command line usage info")
    private boolean mHelpMode = false;

    private ICommandScheduler mScheduler;
    private java.io.Console mTerminal;

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
        mTerminal = System.console();
    }

    /**
     * Sets the config file to use
     * <p/>
     * Exposed for unit testing
     */
    void setConfigFile(File file) {
        mFile = file;
    }

    private String getConsoleInput() throws IOException {
        String line = mTerminal.readLine(CONSOLE_PROMPT);

        return line;
    }

    private String index(String[] array, int i) {
        if (i > array.length) {
            return null;
        } else {
            return array[i];
        }
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

            if (mTerminal == null) {
                // If we're running in non-interactive mode, just wait indefinitely for the
                // scheduler to shut down
                Log.logAndDisplay(LogLevel.INFO, LOG_TAG,
                        "Running indefinitely in non-interactive mode.");
                mScheduler.join();
                return;
            }

            String input = "";
            boolean shouldExit = false;
            Thread.sleep(500);  // Try to let preliminary messages scroll past

            while (!shouldExit) {
                input = getConsoleInput();
                // System.err.println("Got input line: " + input);
                String[] tokens = QuotationAwareTokenizer.tokenizeLine(input);

                if (tokens.length == 0) {
                    continue;
                }
                String cmd = tokens[0];

                // FIXME: make it easier to add command handlers and to handle shortcuts
                // FIXME: make all this stuff a bit more modular and less sucky
                // TODO: think about having the modules themselves advertise their management
                // TODO:   interfaces
                // TODO: perhaps use a prefix matching algorithm to select commands based on
                // TODO:   shortest unique prefix
                if ("exit".equals(cmd) || "q".equals(cmd)) {
                    shouldExit = true;
                } else if ("?".equals(cmd) || "help".equals(cmd) || "h".equals(cmd)) {
                    mTerminal.printf("Not-so-helpful help:\n" +
                            "'q' == 'exit'\n" +
                            "'l i' == 'list invocations'\n" +
                            "'l d' == 'list devices'\n" +
                            "'l c' == 'list configs'\n" +
                            "'d s' == 'dump stack'\n");
                } else if ("list".equals(cmd) || "l".equals(cmd)) {
                    if ("i".equals(index(tokens, 1)) || "invocations".equals(index(tokens, 1))) {
                        Collection<ITestInvocation> invs = mScheduler.listInvocations();
                        int counter = 1;

                        for (ITestInvocation inv : invs) {
                            mTerminal.printf("Got invocation %d: %s\n", counter++, inv);
                        }
                    } else if ("d".equals(index(tokens, 1)) || "devices".equals(index(tokens, 1))) {
                        IDeviceManager manager = DeviceManager.getInstance();
                        Collection<String> devices = null;

                        devices = manager.getAvailableDevices();
                        mTerminal.printf("Available devices:   %s\n", devices);
                        devices = manager.getUnavailableDevices();
                        mTerminal.printf("Unavailable devices: %s\n", devices);
                        devices = manager.getAllocatedDevices();
                        mTerminal.printf("Allocated devices:   %s\n", devices);
                    } else if ("c".equals(index(tokens, 1)) || "configs".equals(index(tokens, 1))) {
                        Collection<String> configs = mScheduler.listConfigs();
                        int counter = 1;

                        for (String config : configs) {
                            mTerminal.printf("Got config %d: %s\n", counter++, config);
                        }
                    }
                } else if ("dump".equals(cmd) || "d".equals(cmd)) {
                    if ("s".equals(index(tokens, 1)) || "stack".equals(index(tokens, 1))) {
                        dumpStacks();
                    } else {
                        mTerminal.printf("Unknown dump argument '%s'.  Enter 'help' for help.\n",
                                index(tokens, 1));
                    }
                } else {
                    mTerminal.printf("Unknown command '%s'.  Enter 'help' for help.\n", cmd);
                }

                Thread.sleep(100);
            }

            mScheduler.shutdown();

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
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // Manually exit, since there may be other threads hanging around, keeping the runtime
            // alive
            exit();
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
     * Closes the logs and exits the program.
     * <p/>
     * Exposed so unit tests can mock out.
     */
    void exit() {
        LogRegistry.getLogRegistry().closeAndRemoveAllLogs();
        System.exit(0);
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

    private void dumpStacks() {
        Map<Thread, StackTraceElement[]> threadMap = Thread.getAllStackTraces();
        for (Map.Entry<Thread, StackTraceElement[]> threadEntry : threadMap.entrySet()) {
            dumpThreadStack(threadEntry.getKey(), threadEntry.getValue());
        }
    }

    private void dumpThreadStack(Thread thread, StackTraceElement[] trace) {
        mTerminal.printf("%s\n", thread);
        for (int i=0; i < trace.length; i++) {
            mTerminal.printf("\t%s\n", trace[i]);
        }
        mTerminal.printf("\n", "");
    }

    public static void main(final String[] mainArgs) {
        Console console = new Console();
        console.run(mainArgs);
    }
}
