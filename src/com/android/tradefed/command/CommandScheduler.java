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

import com.android.ddmlib.Log;
import com.android.tradefed.command.ConditionPriorityBlockingQueue.Matcher;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.ConfigurationFactory;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IConfigurationFactory;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceManager;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.DeviceUnresponsiveException;
import com.android.tradefed.device.IDeviceManager;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.IDeviceManager.FreeDeviceState;
import com.android.tradefed.invoker.ITestInvocation;
import com.android.tradefed.invoker.TestInvocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

/**
 * A scheduler for running TradeFederation configs across all available devices.
 * <p/>
 * Will attempt to prioritize configurations to run based on a total running count of their
 * execution time. e.g. infrequent or fast running configs will get prioritized over long running
 * configs.
 * <p/>
 * Runs forever in background until shutdown.
 */
public class CommandScheduler extends Thread implements ICommandScheduler {

    private static final String LOG_TAG = "ConfigScheduler";
    private ConditionPriorityBlockingQueue<ConfigCommand> mConfigQueue;
    /**  list of active invocation threads */
    private Set<InvocationThread> mInvocationThreads;

    /** timer for scheduling the configurations so invocations honor the mMinLoopTime constraint */
    private Timer mConfigTimer;
    private boolean mShutDown = false;

    /**
     * Container for common options for each config.
     */
    static class CommandOptions {
        @Option(name="help", description="display the help text")
        private boolean mHelpMode = false;

        @Option(name="min-loop-time", description=
            "the minimum invocation time in ms when in loop mode. Default is 1 minute.")
        private long mMinLoopTime = 60 * 1000;

        @Option(name="loop", description="keep running continuously")
        private boolean mLoopMode = false;

        @Option(name="serial", shortName='s', description=
            "run this config on a specific device with given serial number(s)")
        private Collection<String> mSerials = new ArrayList<String>();

        @Option(name="exclude-serial", description=
            "run this test on any device except those with this serial number(s)")
        private Collection<String> mNotSerials = new ArrayList<String>();

        @Option(name="product-type", description=
            "run this test on device with this product type")
        private Collection<String> mProductTypes = new ArrayList<String>();

        /**
         * Set the help mode for the config.
         * <p/>
         * Exposed for testing.
         */
        void setHelpMode(boolean helpMode) {
            mHelpMode = helpMode;
        }

        /**
         * Set the loop mode for the config.
         * <p/>
         * Exposed for testing.
         */
        void setLoopMode(boolean loopMode) {
            mLoopMode = loopMode;
        }

        void setMinLoopTime(long loopTime) {
            mMinLoopTime = loopTime;
        }

        void addSerial(String serialNumber) {
            mSerials.add(serialNumber);
        }

        void addNotSerial(String serialNumber) {
            mNotSerials.add(serialNumber);
        }
    }

    /**
     * Represents one config to be executed
     */
    private static class ConfigCommand {
        final String[] mArgs;

        final CommandOptions mOptions;

        /** the total amount of time this config was executing. Used to prioritize */
        private long mTotalExecTime = 0;

        ConfigCommand(String[] args, CommandOptions options) {
            mArgs = args;
            mOptions = options;
        }

        synchronized void incrementExecTime(long execTime) {
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
     * Class that matches a device against a {@link ConfigCommand}
     */
    private static class DeviceMatcher implements Matcher<ConfigCommand> {
        private final ITestDevice mDevice;

        DeviceMatcher(ITestDevice device) {
            mDevice = device;
        }

        /**
         * {@inheritDoc}
         */
        public boolean matches(ConfigCommand cmd) {
            if (!cmd.mOptions.mSerials.isEmpty() &&
                    !cmd.mOptions.mSerials.contains(mDevice.getSerialNumber())) {
                return false;
            }
            if (cmd.mOptions.mNotSerials.contains(mDevice.getSerialNumber())) {
                return false;
            }
            if (!cmd.mOptions.mProductTypes.isEmpty() &&
                    !cmd.mOptions.mProductTypes.contains(getDeviceProductType(mDevice))) {
                return false;
            }
            return true;
        }

        private String getDeviceProductType(ITestDevice device) {
            try {
                return device.getProductType();
            } catch (DeviceNotAvailableException e) {
                Log.w(LOG_TAG, String.format(
                        "Tried to retrieve product type for device %s but it is unavailable",
                        device.getSerialNumber()));
                return "unknown";
            }
        }
    }

    private class InvocationThread extends Thread {
        private IDeviceManager mManager;
        private ITestDevice mDevice;
        private ITestInvocation mInvocation = null;
        private boolean mIsStarted = false;

        public InvocationThread(String name, IDeviceManager manager, ITestDevice device) {
            // create a thread group so LoggerRegistry can identify this as an invocationThread
            super (new ThreadGroup(name), name);
            mManager = manager;
            mDevice = device;
        }

        private synchronized ITestInvocation createInvocation() {
            mInvocation  = createRunInstance();
            return mInvocation;
        }

        @Override
        public void run() {
            mIsStarted = true;
            FreeDeviceState deviceState = FreeDeviceState.AVAILABLE;
            ConfigCommand cmd = dequeueConfigCommand(mDevice);
            if (cmd == null) {
                Log.d(LOG_TAG, String.format("No configs to test for device %s.",
                        mDevice.getSerialNumber()));
                mManager.freeDevice(mDevice, deviceState);
                removeInvocationThread(this);
                return;
            }
            long startTime = System.currentTimeMillis();
            ITestInvocation instance = createInvocation();
            try {
                IConfiguration config = getConfigFactory().createConfigurationFromArgs(
                        cmd.mArgs, new CommandOptions());
                instance.invoke(mDevice, config);
            } catch (DeviceUnresponsiveException e) {
                Log.w(LOG_TAG, String.format("Device %s is unresponsive",
                        mDevice.getSerialNumber()));
                deviceState = FreeDeviceState.UNRESPONSIVE;
            } catch (DeviceNotAvailableException e) {
                Log.w(LOG_TAG, String.format("Device %s is not available",
                        mDevice.getSerialNumber()));
                deviceState = FreeDeviceState.UNAVAILABLE;
            } catch (ConfigurationException e) {
                Log.e(LOG_TAG, e);
            } catch (Throwable e) {
                Log.e(LOG_TAG, e);
            } finally {
                long elapsedTime = System.currentTimeMillis() - startTime;
                Log.i(LOG_TAG, String.format("Updating config '%s' with elapsed time %d ms",
                        getArgString(cmd.mArgs), elapsedTime));
                cmd.incrementExecTime(elapsedTime);
                mManager.freeDevice(mDevice, deviceState);
                removeInvocationThread(this);
            }
        }

        private synchronized ITestInvocation getInvocation() {
            return mInvocation;
        }

        /**
         * Attempt to gracefully shut down this invocation.
         * <p/>
         * There's three possible cases to handle:
         * <ol>
         * <li>Thread has not started yet: We want to wait for it to start, so it can free its
         * allocated device
         * <li>Thread has started but invocation has not been started (ie thread is blocked waiting
         * for a config): Interrupt the thread in this case
         * <li>Thread is running the invocation: Do nothing - wait for it to complete normally
         * </ol>
         */
        public void shutdown() {
            if (getInvocation() == null && mIsStarted) {
                interrupt();
            }
        }
    }

    /**
     * Creates a {@link CommandScheduler}
     */
    CommandScheduler() {
        mConfigQueue = new ConditionPriorityBlockingQueue<ConfigCommand>(new ConfigComparator());
        mInvocationThreads = new HashSet<InvocationThread>();

    }

    /**
     * Factory method for creating a {@link TestInvocation}.
     *
     * @return the {@link ITestInvocation} to use
     */
    ITestInvocation createRunInstance() {
        return new TestInvocation();
    }

    /**
     * Factory method for getting a reference to the {@link IDeviceManager}
     *
     * @return the {@link IDeviceManager} to use
     */
    IDeviceManager getDeviceManager() {
        return DeviceManager.getInstance();
    }

    /**
     * Factory method for getting a reference to the {@link IConfigurationFactory}
     *
     * @return the {@link IConfigurationFactory} to use
     */
    IConfigurationFactory getConfigFactory() {
        return ConfigurationFactory.getInstance();
    }

    /**
     * The main execution block of this thread.
     */
    @Override
    public void run() {
        mConfigTimer = new Timer("config timer");
        IDeviceManager manager = getDeviceManager();
        while (!mShutDown) {
            Log.d(LOG_TAG, "Waiting for device to test");
            // Spawn off a thread for each allocated device.
            // The retrieval of a config to run on the device is done on this separate thread, to
            // prevent configs which only run on a specific device from blocking the rest
            final ITestDevice device = manager.allocateDevice();
            if (device == null) {
                break;
            }
            InvocationThread invThread = startInvocation(manager, device);
            addInvocationThread(invThread);
        }
        Log.i(LOG_TAG, "Waiting for invocation threads to complete");
        List<InvocationThread> threadListCopy = new ArrayList<InvocationThread>(
                mInvocationThreads.size());
        threadListCopy.addAll(mInvocationThreads);
        for (Thread thread : threadListCopy) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                // ignore
            }
        }
        System.out.println("All done");
        exit(manager);
    }

    private void exit(IDeviceManager manager) {
        if (manager != null) {
            manager.terminate();
        }
    }

    /**
     * {@inheritDoc}
     */
    public void addConfig(String[] args) {
        CommandOptions options = createCommandOptions();
        try {
            // load a config to parse options and validate arguments up front
            getConfigFactory().createConfigurationFromArgs(args, options);
            if (options.mHelpMode) {
                getConfigFactory().printHelp(args, System.out, CommandOptions.class);
            } else {
                ConfigCommand cmd = new ConfigCommand(args, options);
                mConfigQueue.add(cmd);
            }
        } catch (ConfigurationException e) {
            System.out.println(String.format("Unrecognized arguments: %s", e.getMessage()));
            getConfigFactory().printHelp(args, System.out, CommandOptions.class);
        }
    }

    /**
     * Factory method for creating {@link CommandOptions}.
     * <p/>
     * Exposed for testing.
     */
    CommandOptions createCommandOptions() {
        return new CommandOptions();
    }

    /**
     * Dequeue the highest priority config from the queue that can run against the provided device.
     *
     * @param device the {@link ITestDevice} to run against
     * @return the {@link ConfigCommand} or <code>null</code>
     */
    private ConfigCommand dequeueConfigCommand(ITestDevice device) {
        if (mShutDown) {
            return null;
        }
        ConfigCommand cmd = null;
        try {
            cmd = mConfigQueue.take(new DeviceMatcher(device));
            if (cmd.mOptions.mLoopMode) {
                returnConfigToQueue(cmd);
            }
        } catch (InterruptedException e) {
            Log.i(LOG_TAG, "Waiting for config command interrupted");
        }
        return cmd;
    }

    /**
     * Return config to queue, with delay if necessary
     *
     * @param cmd the {@link ConfigCommand} to return to queue
     */
    private void returnConfigToQueue(final ConfigCommand cmd) {
        if (cmd.mOptions.mMinLoopTime > 0) {
            // delay before adding config back to queue
            TimerTask delayConfig = new TimerTask() {
                @Override
                public void run() {
                    Log.d(LOG_TAG, String.format("Adding config '%s' back to queue",
                            getArgString(cmd.mArgs)));
                    mConfigQueue.add(cmd);
                }
            };
            Log.d(LOG_TAG, String.format("Delay adding config '%s' back to queue for %d ms",
                    getArgString(cmd.mArgs), cmd.mOptions.mMinLoopTime));
            mConfigTimer.schedule(delayConfig, cmd.mOptions.mMinLoopTime);
        } else {
            // return to queue immediately
            mConfigQueue.add(cmd);
        }
    }

    /**
     * Helper method to return an array of {@link String} elements as a readable {@link String}
     *
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
     * Spawns off thread to run invocation for given configuration
     *
     * @param manager the {@link IDeviceManager} to return device to when complete
     * @param device the {@link ITestDevice}
     * @param config the {@link IConfiguration} to run
     * @return the invocation's thread
     */
    private InvocationThread startInvocation(IDeviceManager manager, ITestDevice device) {
        final String invocationName = String.format("Invocation-%s", device.getSerialNumber());
        InvocationThread invocationThread = new InvocationThread(invocationName, manager, device);
        invocationThread.start();
        return invocationThread;
    }

    /**
     * Removes a {@link InvocationThread} from the active list.
     */
    private synchronized void removeInvocationThread(InvocationThread invThread) {
        mInvocationThreads.remove(invThread);
    }

    /**
     * Adds a {@link InvocationThread} to the active list.
     */
    private synchronized void addInvocationThread(InvocationThread invThread) {
        mInvocationThreads.add(invThread);
    }

    /**
     * {@inheritDoc}
     */
    public synchronized void shutdown() {
        mShutDown = true;
        mConfigQueue.clear();
        if (mConfigTimer != null) {
            mConfigTimer.cancel();
        }
        for (InvocationThread invThread : mInvocationThreads) {
            invThread.shutdown();
        }
    }
}
