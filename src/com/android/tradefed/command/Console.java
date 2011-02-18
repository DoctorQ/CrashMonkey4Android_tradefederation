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
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

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

    protected ICommandScheduler mScheduler;
    protected java.io.Console mTerminal;
    private RegexTrie<Runnable> mCommandTrie = new RegexTrie<Runnable>();

    /** A convenience type for List<List<String>> */
    @SuppressWarnings("serial")
    protected static class CaptureList extends LinkedList<List<String>> {
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
    protected abstract static class ArgRunnable<T> implements Runnable {
        @Override
        public void run() {
            run(null);
        }

        abstract public void run(T args);
    }

    /**
     * This is a sentinel class that will cause TF to shut down.  This enables a user to get TF to
     * shut down via the RegexTrie input handling mechanism.
     */
    private static class QuitRunnable implements Runnable {
        @Override
        public void run() {}
    }

    Console() {
        this(new CommandScheduler());
    }

    /**
     * Create a {@link Console} with given scheduler.  Also, set up console command handling
     * <p/>
     * Exposed for unit testing
     */
    Console(ICommandScheduler scheduler) {
        mScheduler = scheduler;
        mTerminal = System.console();

        List<String> genericHelp = new LinkedList<String>();
        Map<String, String> commandHelp = new LinkedHashMap<String, String>();
        addDefaultCommands(mCommandTrie, genericHelp, commandHelp);
        setCustomCommands(mCommandTrie, genericHelp, commandHelp);
        generateHelpListings(mCommandTrie, genericHelp, commandHelp);
    }

    /**
     * A customization point that subclasses can use to alter which commands are available in the
     * console.
     * <p />
     * Implementations should modify the {@code genericHelp} and {@code commandHelp} variables to
     * document what functionality they may have added, modified, or removed.
     *
     * @param trie The {@link RegexTrie} to add the commands to
     * @param genericHelp A {@link List} of lines to print when the user runs the "help" command
     *        with no arguments.
     * @param commandHelp A {@link Map} containing documentation for any new commands that may have
     *        been added.  The key is a regular expression to use as a key for {@link RegexTrie}.
     *        The value should be a String containing the help text to print for that command.
     */
    void setCustomCommands(RegexTrie<Runnable> trie, List<String> genericHelp,
            Map<String, String> commandHelp) {
        // Meant to be overridden by subclasses
    }

    /**
     * Generate help listings based on the contents of {@code genericHelp} and {@code commandHelp}.
     *
     * @param trie The {@link RegexTrie} to add the commands to
     * @param genericHelp A {@link List} of lines to print when the user runs the "help" command
     *        with no arguments.
     * @param commandHelp A {@link Map} containing documentation for any new commands that may have
     *        been added.  The key is a regular expression to use as a key for {@link RegexTrie}.
     *        The value should be a String containing the help text to print for that command.
     */
    void generateHelpListings(RegexTrie<Runnable> trie, List<String> genericHelp,
            Map<String, String> commandHelp) {
        final String genHelpString = join(genericHelp);
        final String helpPattern = "\\?|h|help";

        final ArgRunnable<CaptureList> genericHelpRunnable = new ArgRunnable<CaptureList>() {
            @Override
            public void run(CaptureList args) {
                mTerminal.printf(genHelpString);
            }
        };
        trie.put(genericHelpRunnable, helpPattern);

        // Add help entries for everything listed in the commandHelp map
        for (Map.Entry<String, String> helpPair : commandHelp.entrySet()) {
            final String key = helpPair.getKey();
            final String helpText = helpPair.getValue();

            trie.put(new Runnable() {
                    @Override
                    public void run() {
                        mTerminal.printf(helpText);
                    }
                }, helpPattern, key);
        }

        // Add a generic "not found" help message for everything else
        trie.put(new ArgRunnable<CaptureList>() {
                    @Override
                    public void run(CaptureList args) {
                        // Command will be the only capture in the second argument
                        // (first argument is helpPattern)
                        mTerminal.printf("No help for '%s'; command is unknown or undocumented\n",
                                args.get(1).get(0));
                        genericHelpRunnable.run(args);
                    }
                }, helpPattern, null);

        // Add a fallback input handler
        trie.put(new ArgRunnable<CaptureList>() {
                    @Override
                    public void run(CaptureList args) {
                        if (args.isEmpty()) {
                            // User hit <Enter> with a blank line
                            return;
                        }

                        // Command will be the only capture in the first argument
                        mTerminal.printf("Unknown command: '%s'\n", args.get(0).get(0));
                        genericHelpRunnable.run(args);
                    }
                }, (Pattern)null);
    }

    /**
     * Add commands to create the default Console experience
     * <p />
     * Adds relevant documentation to {@code genericHelp} and {@code commandHelp}.
     *
     * @param trie The {@link RegexTrie} to add the commands to
     * @param genericHelp A {@link List} of lines to print when the user runs the "help" command
     *        with no arguments.
     * @param commandHelp A {@link Map} containing documentation for any new commands that may have
     *        been added.  The key is a regular expression to use as a key for {@link RegexTrie}.
     *        The value should be a String containing the help text to print for that command.
     */
    void addDefaultCommands(RegexTrie<Runnable> trie, List<String> genericHelp,
            Map<String, String> commandHelp) {
        final String helpPattern = "\\?|h|help";
        final String listPattern = "l(?:ist)?";
        final String dumpPattern = "d(?:ump)?";
        final String runPattern = "r(?:un)?";

        // Help commands
        genericHelp.add("Enter 'q' or 'exit' to exit");
        genericHelp.add("Enter 'help list' for help with 'list' commands");
        genericHelp.add("Enter 'help run'  for help with 'run' commands");
        genericHelp.add("Enter 'help dump' for help with 'dump' commands");

        commandHelp.put(listPattern, String.format(
                "%s help:\n" +
                "\ti[nvocations]  List all invocation threads\n" +
                "\td[evices]      List all detected or known devices\n" +
                "\tc[configs]     List all configs\n", listPattern));

        commandHelp.put(dumpPattern, String.format(
                "%s help:\n" +
                "\ts[tack]  Dump the stack traces of all threads\n" +
                "\tl[ogs]   Dump the logs of all invocations to files\n",
                dumpPattern));

        commandHelp.put(runPattern, String.format(
                "%s help:\n" +
                "\tcommand [options] <config>        Run the specified command\n" +
                "\tcmdfile <cmdfile.txt>             Run the specified commandfile\n" +
                "\tsingleCommand [options] <config>  Run the specified command, and run 'exit' " +
                        "immediately afterward\n",
                runPattern));

        // Handle quit commands
        trie.put(new QuitRunnable(), "(?:q|exit)");

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
        // FIXME: fix this functionality
        // trie.put(runHelpRun, runPattern, "(?:singleC|c)ommand");

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
        // FIXME: fix this functionality
        //trie.put(runHelpRun, runPattern, "cmdfile");
    }

    /**
     * Convenience method to join string pieces into a single string, with newlines after each piece
     * FIXME: add a join implementation to Util
     */
    private static String join(List<String> pieces) {
        StringBuilder sb = new StringBuilder();
        for (String piece : pieces) {
            sb.append(piece);
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * Sets the terminal instance to use
     * <p/>
     * Exposed for unit testing
     */
    void setTerminal(java.io.Console terminal) {
        mTerminal = terminal;
    }

    /**
     * Get input from the console
     *
     * @return A {@link String} containing the input to parse and run
     */
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
                    if (command instanceof QuitRunnable) {
                        // shut down
                        shouldExit = true;
                        continue;
                    } else if (command instanceof ArgRunnable) {
                        // FIXME: verify that command implements ArgRunnable<CaptureList> instead
                        // FIXME: of just ArgRunnable
                        ((ArgRunnable<CaptureList>)command).run(groups);
                    } else {
                        command.run();
                    }
                } else {
                    mTerminal.printf("Unable to handle command '%s'.  Enter 'help' for help.\n",
                            tokens[0]);
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
