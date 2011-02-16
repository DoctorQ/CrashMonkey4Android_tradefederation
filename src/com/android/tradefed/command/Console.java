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
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.device.DeviceManager;
import com.android.tradefed.device.IDeviceManager;
import com.android.tradefed.invoker.ITestInvocation;
import com.android.tradefed.log.LogRegistry;
import com.android.tradefed.util.QuotationAwareTokenizer;
import com.android.tradefed.util.RegexTrie;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
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

    /* FIXME: reimplement these somewhere
     * @Option(name = "log-level-display", description =
     *         "minimum log level to display on stdout for global log")
     * private String mLogLevelDisplay = null;
     *
     * @Option(name = "log-tag-display", description =
     *     "Log tag filter for global log. Always display logs with this tag on stdout")
     * private Collection<String> mLogTagsDisplay = new HashSet<String>();
     * if (mLogLevelDisplay != null) {
     *     LogRegistry.getLogRegistry().setGlobalLogDisplayLevel(mLogLevelDisplay);
     * }
     * LogRegistry.getLogRegistry().setGlobalLogTagDisplay(mLogTagsDisplay);
     */

    private ICommandScheduler mScheduler;
    private java.io.Console mTerminal;
    private RegexTrie<Runnable> mCommandTrie = new RegexTrie<Runnable>();

    /** A convenience type for List<List<String>> */
    @SuppressWarnings("serial")
    private class CaptureList extends LinkedList<List<String>> {
        CaptureList() {
            super();
        }

        CaptureList(Collection<? extends List<String>> c) {
            super(c);
        }
    }

    /**
     * A {@link Runnable} with a {@code run} method that can take an argument
     */
    abstract class ArgRunnable<T> implements Runnable {
        @Override
        public void run() {
            run(null);
        }

        abstract public void run(T args);
    }

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
        addDefaultCommands(mCommandTrie);
    }

    void addDefaultCommands(RegexTrie<Runnable> trie) {
        final String helpPattern = "\\?|h|help";
        final String listPattern = "l(?:ist)?";
        final String dumpPattern = "d(?:ump)?";
        final String runPattern = "r(?:un)?";

        // Help commands
        trie.put(new Runnable() {
                    @Override
                    public void run() {
                        mTerminal.printf(
                                "Enter 'q' or 'exit' to exit\n" +
                                "Enter 'help list' for help with 'list' commands\n" +
                                "Enter 'help run'  for help with 'run' commands\n" +
                                "Enter 'help dump' for help with 'dump' commands\n");
                    }
                }, helpPattern);
        trie.put(new Runnable() {
                    @Override
                    public void run() {
                        mTerminal.printf(
                                "%s help:\n" +
                                "\ti[nvocations]  List all invocation threads\n" +
                                "\td[evices]      List all detected or known devices\n" +
                                "\tc[configs]     List all configs\n", listPattern);
                    }
                }, helpPattern, listPattern);
        trie.put(new Runnable() {
                    @Override
                    public void run() {
                        mTerminal.printf(
                                "%s help:\n" +
                                "\ts[tack]  Dump the stack traces of all threads\n" +
                                "\tl[ogs]   Dump the logs of all invocations to files\n",
                                dumpPattern);
                    }
                }, helpPattern, dumpPattern);
        Runnable runHelpRun = new Runnable() {
                    @Override
                    public void run() {
                        mTerminal.printf(
                                "%s help:\n" +
                                "\tcommand [options] <config>        Run the specified command\n" +
                                "\tcmdfile <cmdfile.txt>             Run the specified " +
                                    "commandfile\n" +
                                "\tsingleCommand [options] <config>  Run the specified command, " +
                                    "and run 'exit' immediately afterward\n",
                                runPattern);
                    }
                };
        trie.put(runHelpRun, helpPattern, runPattern);

        // List commands
        trie.put(new Runnable() {
                    @Override
                    public void run() {
                        Collection<ITestInvocation> invs = mScheduler.listInvocations();
                        int counter = 1;

                        for (ITestInvocation inv : invs) {
                            mTerminal.printf("Got invocation %d: %s\n", counter++, inv);
                        }
                    }
                }, listPattern, "i(?:nvocations)?");
        trie.put(new Runnable() {
                    @Override
                    public void run() {
                        IDeviceManager manager = DeviceManager.getInstance();
                        Collection<String> devices = null;

                        devices = manager.getAvailableDevices();
                        mTerminal.printf("Available devices:   %s\n", devices);
                        devices = manager.getUnavailableDevices();
                        mTerminal.printf("Unavailable devices: %s\n", devices);
                        devices = manager.getAllocatedDevices();
                        mTerminal.printf("Allocated devices:   %s\n", devices);
                    }
                }, listPattern, "d(?:evices)?");
        trie.put(new Runnable() {
                    @Override
                    public void run() {
                        Collection<String> configs = mScheduler.listConfigs();
                        int counter = 1;

                        for (String config : configs) {
                            mTerminal.printf("Got config %d: %s\n", counter++, config);
                        }
                    }
                }, listPattern, "c(?:onfigs)?");

        // Dump commands
        trie.put(new Runnable() {
                    @Override
                    public void run() {
                        dumpStacks();
                    }
                }, dumpPattern, "s(?:tacks?)?");
        trie.put(new Runnable() {
                    @Override
                    public void run() {
                        dumpLogs();
                    }
                }, dumpPattern, "l(?:ogs?)?");

        // Run commands
        ArgRunnable<CaptureList> runRunCommand = new ArgRunnable<CaptureList>() {
                    @Override
                    public void run(CaptureList args) {
                        // Skip 2 tokens to get past runPattern and "command"
                        String[] flatArgs = new String[args.size() - 2];
                        for (int i = 2; i < args.size(); i++) {
                            flatArgs[i - 2] = args.get(i).get(0);
                        }
                        mScheduler.addConfig(flatArgs);
                    }
                };
        trie.put(runRunCommand, runPattern, "(?:singleC|c)ommand", null);
        // Missing required argument: show help
        trie.put(runHelpRun, runPattern, "(?:singleC|c)ommand");

        ArgRunnable<CaptureList> runRunCmdfile = new ArgRunnable<CaptureList>() {
                    @Override
                    public void run(CaptureList args) {
                        // Skip 2 tokens to get past runPattern and "cmdfile"
                        String file = args.get(2).get(0);
                        System.out.format("Attempting to run cmdfile %s\n", file);
                        try {
                            createCommandFileParser().parseFile(new File(file), mScheduler);
                        } catch (IOException e) {
                            mTerminal.printf("Failed to run %s: %s\n", file, e);
                        } catch (ConfigurationException e) {
                            mTerminal.printf("Failed to run %s: %s\n", file, e);
                        }
                    }
                };
        trie.put(runRunCmdfile, runPattern, "cmdfile", "(.*)");
        // Missing required argument: show help
        trie.put(runHelpRun, runPattern, "cmdfile");
    }

    /**
     * Sets the terminal instance to use
     * <p/>
     * Exposed for unit testing
     */
    void setTerminal(java.io.Console terminal) {
        mTerminal = terminal;
    }

    private String getConsoleInput() throws IOException {
        String line = mTerminal.readLine(CONSOLE_PROMPT);

        return line;
    }

    /**
     * The main method to launch the console. Will keep running until shutdown command is issued.
     *
     * @param args
     */
    public void run(String[] args) {
        initLogging();
        List<String> arrrgs = new LinkedList<String>(Arrays.asList(args));

        try {
            mScheduler.start();

            if (mTerminal == null) {
                // If we're running in non-interactive mode, just wait indefinitely for the
                // scheduler to shut down
                Log.logAndDisplay(LogLevel.INFO, LOG_TAG,
                        "Running indefinitely in non-interactive mode.");
                mScheduler.join();
                cleanUp();
                return;
            }

            String input = "";
            boolean shouldExit = false;
            CaptureList groups = new CaptureList();
            String[] tokens;

            while (!shouldExit) {
                if (arrrgs.isEmpty()) {
                    input = getConsoleInput();

                    if (input == null) {
                        // Usually the result of getting EOF on the console
                        mTerminal.printf("\nReceived EOF; quitting...\n");
                        shouldExit = true;
                        break;
                    }

                    tokens = null;
                    try {
                        tokens = QuotationAwareTokenizer.tokenizeLine(input);
                    } catch (IllegalArgumentException e) {
                        mTerminal.printf("Invalid input: %s.\n", input);
                        continue;
                    }

                    if (tokens == null || tokens.length == 0) {
                        continue;
                    }
                } else {
                    mTerminal.printf("Using commandline arguments as starting command: %s\n",
                            arrrgs);
                    tokens = arrrgs.toArray(new String[0]);
                    arrrgs.clear();
                }

                // TODO: think about having the modules themselves advertise their management
                // TODO: interfaces
                Runnable command = mCommandTrie.retrieve(groups, tokens);
                if (command != null) {
                    if (command instanceof ArgRunnable) {
                        // FIXME: verify that command implements ArgRunnable<CaptureList> instead
                        // FIXME: of just ArgRunnable
                        ((ArgRunnable<CaptureList>)command).run(groups);
                    } else {
                        command.run();
                    }
                } else if ("exit".equals(tokens[0]) || "q".equals(tokens[0])) {
                    shouldExit = true;
                } else {
                    mTerminal.printf("Unknown command '%s'.  Enter 'help' for help.\n", tokens[0]);
                }

                // Special-case for singleConfig, which should run a config and then immediately
                // attempt to exit
                if (tokens.length >= 2 && "singleConfig".equals(tokens[1])) {
                    try {
                        // FIXME: This is a horrible hack to wait until there _should_ be devices
                        // FIXME: available.
                        Thread.sleep(5000 /*DeviceStateMonitor.CHECK_POLL_TIME*/ + 500);
                    } catch (InterruptedException e) {
                        // ignore
                    }
                    shouldExit = true;
                }

                Thread.sleep(100);
            }

            mScheduler.shutdown();

            mScheduler.join();
        } catch (InterruptedException e) {
            // ignore
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            cleanUp();
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
     * Closes the logs and does any other necessary cleanup before the returning from the main
     * function.
     * <p/>
     * Exposed so unit tests can mock out.
     */
    void cleanUp() {
        LogRegistry.getLogRegistry().closeAndRemoveAllLogs();
    }

    /**
     * Factory method for creating a {@link CommandFileParser}.
     * <p/>
     * Exposed for unit testing.
     */
    CommandFileParser createCommandFileParser() {
        return new CommandFileParser();
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

    private void dumpLogs() {
        LogRegistry.getLogRegistry().dumpLogs();
    }

    public static void main(final String[] mainArgs) {
        Console console = new Console();
        console.run(mainArgs);
    }
}
