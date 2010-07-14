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
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceManager;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.DeviceUnresponsiveException;
import com.android.tradefed.device.IDeviceManager;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.IDeviceManager.FreeDeviceState;
import com.android.tradefed.invoker.ITestInvocation;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.PriorityBlockingQueue;

/**
 * Command-line launcher for Trade Federation that runs set of commands from a file.
 * <p/>
 * The syntax of the given file should be series of lines. Each line is one configuration plus its
 * options, delimited by whitespace:
 * <pre>
 *   [options] config-name
 *   [options] config-name2
 *   ...
 * </pre>
 * TODO: handle an option value that contains whitespace
 * This class will do the following
 * <ol>
 *     <li>for each line in file</li>
 *     <ol type="a">
 *         <li>load config for line</li>
 *         <li>allocate a device - waiting if necessary</li>
 *         <li>fork off a thread to run invocation for that config</li>
 *     </ol>
 *     <li>wait for all threads to complete</li>
 * </ol>
 */
public class CommandFile extends Command {

    private static final String LOG_TAG = "CommandFile";

    @Option(name="loop", description="keep running continuously")
    private boolean mLoopMode = false;

    @Option(name="min-loop-time", description="the minimum invocation time in ms when in loop mode")
    private long mMinLoopTime =  1 * 60 * 1000;

    @Option(name="file", description="the path to file of configs to run")
    private File mFile = null;

    private PriorityBlockingQueue<ConfigCommand> mConfigQueue;
    private List<Thread> mInvocationThreads;

    /** timer for scheduling the configurations so invocations honor the mMinLoopTime constraint */
    private Timer mConfigTimer;

    private static class ConfigCommand {
        final String[] mArgs;

        /** the total amount of time this config was executing. Used to prioritize */
        private long mTotalExecTime = 0;

        ConfigCommand(String[] args) {
            mArgs = args;
        }

        synchronized void updateExecTime(long execTime) {
            mTotalExecTime += execTime;
        }
    }

    /**
     * Comparator for ConfigCommmand.
     * <p/>
     * Compares by mTotalExecTime, prioritizing configs with lower execution time
     */
    private static class ConfigComparator implements Comparator<ConfigCommand> {

        /**
         * {@inheritDoc}
         */
        public int compare(ConfigCommand c1, ConfigCommand c2) {
            if (c1.mTotalExecTime == c2.mTotalExecTime) {
                return 0;
            } else if (c1.mTotalExecTime < c2.mTotalExecTime) {
                return -1;
            } else {
                return 1;
            }
        }
    }

    /**
     * Creates a {@link CommandFile}
     */
    CommandFile() {
        // arbitrarily set the initial queue capacity to 10
        mConfigQueue = new PriorityBlockingQueue<ConfigCommand>(10, new ConfigComparator());
        mInvocationThreads = new LinkedList<Thread>();
        mConfigTimer = new Timer("Invocation timer");
    }

    /**
     * Sets the loop mode
     * <p/>
     * Exposed for unit testing
     */
    void setLoopMode(boolean loop) {
        mLoopMode = loop;
    }

    /**
     * Sets the min loop time in ms
     * <p/>
     * Exposed for unit testing
     */
    void setMinLoopTime(long minLoopTime) {
        mMinLoopTime = minLoopTime;
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
     * The main worker method that will parse the command line arguments, and invoke the test run.
     * <p/>
     * TODO: support --help
     * @param args the command line arguments. Expected format is:
     *   [--loop] [--min-loop-time X] --file config file to run
     *
     * where:
     *  <li> --loop: keep running an invocation continuously. Each invocation of a given config
     *  will be spaced at least --min-loop-time ms apart. ie if a prior invocation has nothing to
     *  do, the next invocation for that config will not be invoked until at least --min-loop-time
     *  ms has elapsed.
     *  <li> --min-loop-time: Specifiy the amount of time in ms between invocation times. Only
     *  valid when --loop is specified.
     */
    @Override
    protected void run(String[] args) {
        DdmPreferences.setLogLevel(LogLevel.VERBOSE.getStringValue());
        Log.setLogOutput(getLogRegistry());
        IDeviceManager manager = null;

        try {
            ArgsOptionParser myParser = new ArgsOptionParser(this);
            myParser.parse(args);
            if (mFile == null) {
                throw new IllegalArgumentException("missing --file option");
            }
            parseFile(mFile);

            manager = getDeviceManager();

            ConfigCommand cmd = null;
            while ((cmd = getConfigCommand()) != null) {
                Log.i(LOG_TAG, String.format("Dequeuing config to run '%s' with total time %d ms",
                        getArgString(cmd.mArgs), cmd.mTotalExecTime));
                final IConfiguration config = getConfigFactory().createConfigurationFromArgs(
                        cmd.mArgs);
                final ITestDevice device = manager.allocateDevice(config.getDeviceRecovery());
                Thread thread = startInvocation(manager, device, config, cmd);
                if (thread != null) {
                    mInvocationThreads.add(thread);
                }
                if (mLoopMode) {
                    returnConfigToQueue(cmd);
                }
            }
        } catch (ConfigurationException e) {
            System.err.println(String.format("Failed to parse options: %s", e.getMessage()));
        } catch (FileNotFoundException e) {
            System.err.println(String.format("Provided file %s does not exist",
                    mFile.getAbsolutePath()));
        } catch (IOException e) {
            System.err.println(String.format("Provided file %s cannot be read",
                    mFile.getAbsolutePath()));
        } catch (Throwable e) {
            System.err.println("Uncaught exception");
            e.printStackTrace(System.err);
        }

        Log.i(LOG_TAG, "Waiting for invocation threads to complete");
        for (Thread thread : mInvocationThreads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                // ignore
            }
        }
        mConfigTimer.cancel();
        System.out.println("All done");
        exit(manager);
    }

    /**
     * Helper method to return an array of {@link String} elements as a readable {@link String}
     * @param args the {@link String}[] to use
     * @return a display friendly {@link String} of args contents
     */
    private String getArgString(String[] args) {
        StringBuilder builder = new StringBuilder();
        for (String arg : args) {
            builder.append(arg);
            builder.append(" ");
        }
        return builder.toString();
    }

    /**
     * Retrieve a {@link ConfigCommand} from the queue, blocking if necessary.
     *
     * @return the ConfigCommand or <code>null</code>
     */
    private ConfigCommand getConfigCommand() {
        if (mLoopMode) {
            // blocking call
            try {
                return mConfigQueue.take();
            } catch (InterruptedException e) {
               return null;
            }
        } else {
            // non-blocking call
            return mConfigQueue.poll();
        }
    }

    /**
     * Return config to queue, with delay if necessary
     * @param cmd
     */
    private void returnConfigToQueue(final ConfigCommand cmd) {
        if (mMinLoopTime > 0) {
            // delay before adding config back to queue
            TimerTask delayConfig = new TimerTask() {
                @Override
                public void run() {
                    Log.i(LOG_TAG, String.format("Adding config '%s' back to queue",
                            getArgString(cmd.mArgs)));
                    mConfigQueue.add(cmd);
                }
            };
            Log.d(LOG_TAG, String.format("Delay adding config '%s' back to queue for %d ms",
                    getArgString(cmd.mArgs), mMinLoopTime));
            mConfigTimer.schedule(delayConfig, mMinLoopTime);
        } else {
            // return to queue immediately
            mConfigQueue.add(cmd);
        }
    }

    /**
     * Populates mConfigQueue with data in file
     * @param file
     * @throws IOException
     */
    private void parseFile(File file) throws IOException {
        BufferedReader fileReader = createConfigFileReader(file);
        String line = null;
        while ((line = fileReader.readLine()) != null) {
            line = line.trim();
            if (line.length() > 0) {
                String[] args = line.split("\\s+");
                mConfigQueue.add(new ConfigCommand(args));
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

    /**
     * Forks of thread to run invocation for given configuration
     *
     * @param manager the {@link DeviceManager} to return device too when complete
     * @param device the {@link ITestDevice}
     * @param config the {@link IConfiguration} to run
     * @return the invocation's thread or <code>null</code>
     */
    private Thread startInvocation(final IDeviceManager manager, final ITestDevice device,
            final IConfiguration config, final ConfigCommand cmd) {
        final String invocationName = String.format("Invocation-%s", device.getSerialNumber());
        // create a thread group so LoggerRegistry can identify this as an invocationThread
        ThreadGroup invocationGroup = new ThreadGroup(invocationName);
        Thread invocationThread = new Thread(invocationGroup, invocationName) {
            @Override
            public void run() {
                long startTime = System.currentTimeMillis();
                FreeDeviceState deviceState = FreeDeviceState.AVAILABLE;
                ITestInvocation instance = createRunInstance();
                try {
                    instance.invoke(device, config);
                } catch (DeviceUnresponsiveException e) {
                    Log.w(LOG_TAG, String.format("Device %s is unresponsive",
                            device.getSerialNumber()));
                    deviceState = FreeDeviceState.UNRESPONSIVE;
                } catch (DeviceNotAvailableException e) {
                    Log.w(LOG_TAG, String.format("Device %s is not available",
                            device.getSerialNumber()));
                    deviceState = FreeDeviceState.UNAVAILABLE;
                }
                manager.freeDevice(device, deviceState);
                cmd.updateExecTime(System.currentTimeMillis() - startTime);
            }
        };
        invocationThread.start();
        return invocationThread;
    }

    /**
     * Main entry point for TradeFederation command line launcher.
     *
     * @param args command line arguments.
     */
    public static void main(String[] args) {
        CommandFile cmd = new CommandFile();
        cmd.run(args);
    }
}
