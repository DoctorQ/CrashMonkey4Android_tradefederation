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
import com.android.ddmlib.Log.LogLevel;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.ConfigurationFactory;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IConfigurationFactory;
import com.android.tradefed.device.DeviceManager;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.DeviceSelectionMatcher;
import com.android.tradefed.device.DeviceSelectionOptions;
import com.android.tradefed.device.DeviceUnresponsiveException;
import com.android.tradefed.device.IDeviceManager;
import com.android.tradefed.device.IDeviceSelectionOptions;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.IDeviceManager.FreeDeviceState;
import com.android.tradefed.invoker.IRescheduler;
import com.android.tradefed.invoker.ITestInvocation;
import com.android.tradefed.invoker.TestInvocation;
import com.android.tradefed.util.ConditionPriorityBlockingQueue;
import com.android.tradefed.util.ConditionPriorityBlockingQueue.IMatcher;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
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
    private boolean mShutdown = false;

    /**
     * Represents one config to be executed
     */
    private class ConfigCommand {
        private final String[] mArgs;

        /** the total amount of time this config was executing. Used to prioritize */
        private long mTotalExecTime = 0;

        /** the currently loaded configuration for the command */
        protected IConfiguration mConfig;

        ConfigCommand(String[] args) throws ConfigurationException {
            mArgs = args;
            resetConfiguration();
        }

        synchronized void incrementExecTime(long execTime) {
            mTotalExecTime += execTime;
        }

        /**
         * Get the {@link ICommandOptions} associated with this command.
         * @throws ConfigurationException
         */
        ICommandOptions getCommandOptions() {
            return getConfiguration().getCommandOptions();
        }

        /**
         * Get the {@link DeviceSelectionOptions} associated with this command.
         */
        IDeviceSelectionOptions getDeviceOptions() {
            return getConfiguration().getDeviceSelectionOptions();
        }

        /**
         * Get the full list of config arguments associated with this command.
         */
        String[] getArgs() {
            return mArgs;
        }

        /**
         * Reloads a configuration for this command from args.
         * <p/>
         * Should be called once config is rescheduled for execution
         *
         * @return the newly created {@link IConfiguration}
         */
        public IConfiguration resetConfiguration() throws ConfigurationException  {
            mConfig = getConfigFactory().createConfigurationFromArgs(getArgs());
            return mConfig;
        }

        /**
         * Get the {@link IConfiguration} associated with this command.
         *
         * @return
         */
        public IConfiguration getConfiguration() {
            return mConfig;
        }
    }

    /**
     * A {@link ConfigCommand} that is a rescheduling of a previously executed command
     */
    private class RescheduledConfigCommand extends ConfigCommand {

        private final ConfigCommand mOriginalCmd;

        RescheduledConfigCommand(ConfigCommand cmd, IConfiguration config)
                throws ConfigurationException {
            super(cmd.getArgs());
            mConfig = config;
            mOriginalCmd = cmd;
            // a resumable config should never be in loop mode
            mConfig.getCommandOptions().setLoopMode(false);
        }

        @Override
        public IConfiguration resetConfiguration() throws ConfigurationException  {
            // ignore, continue to use config
            // TODO: find a cleaner solution
            return mConfig;
        }

        @Override
        public IConfiguration getConfiguration()  {
            return mConfig;
        }

        @Override
        synchronized void incrementExecTime(long execTime) {
            // add exec time to original cmd
            mOriginalCmd.incrementExecTime(execTime);
        }
    }

    /**
     * A {@link IRescheduler} that will add a config back to the queue.
     */
    private class Rescheduler implements IRescheduler {

        private ConfigCommand mOrigCmd;

        Rescheduler(ConfigCommand cmd) {
            mOrigCmd = cmd;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean scheduleConfig(IConfiguration config) {
            if (mShutdown) {
                // cannot schedule configs if shut down
                return false;
            }
            try {
                RescheduledConfigCommand rescheduledCmd = new RescheduledConfigCommand(mOrigCmd,
                        config);
                mConfigQueue.add(rescheduledCmd);
                return true;
            } catch (ConfigurationException e) {
                Log.e(LOG_TAG, e);
            }
            return false;
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
    private static class DeviceCmdMatcher implements IMatcher<ConfigCommand> {
        private final ITestDevice mDevice;

        DeviceCmdMatcher(ITestDevice device) {
            mDevice = device;
        }

        /**
         * {@inheritDoc}
         */
        public boolean matches(ConfigCommand cmd) {
            IDeviceSelectionOptions deviceOptions = cmd.getDeviceOptions();
            return DeviceSelectionMatcher.matches(mDevice.getIDevice(), deviceOptions);
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
                IConfiguration config = cmd.getConfiguration();
                instance.invoke(mDevice, config, new Rescheduler(cmd));
            } catch (DeviceUnresponsiveException e) {
                Log.w(LOG_TAG, String.format("Device %s is unresponsive",
                        mDevice.getSerialNumber()));
                deviceState = FreeDeviceState.UNRESPONSIVE;
            } catch (DeviceNotAvailableException e) {
                Log.w(LOG_TAG, String.format("Device %s is not available",
                        mDevice.getSerialNumber()));
                deviceState = FreeDeviceState.UNAVAILABLE;
            } catch (FatalHostError e) {
                Log.logAndDisplay(LogLevel.ERROR, LOG_TAG, String.format(
                        "Fatal error occurred: %s, shutting down", e.getMessage()));
                if (e.getCause() != null) {
                    Log.e(LOG_TAG, e.getCause());
                }
                shutdown();
            } catch (Throwable e) {
                Log.e(LOG_TAG, e);
            } finally {
                long elapsedTime = System.currentTimeMillis() - startTime;
                Log.i(LOG_TAG, String.format("Updating config '%s' with elapsed time %d ms",
                        getArgString(cmd.getArgs()), elapsedTime));
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
        public void shutdownInvocation() {
            if (getInvocation() == null && mIsStarted) {
                interrupt();
            }
        }
    }

    /**
     * Creates a {@link CommandScheduler}.
     */
    CommandScheduler() {
        mConfigQueue = new ConditionPriorityBlockingQueue<ConfigCommand>(new ConfigComparator());
        mInvocationThreads = new HashSet<InvocationThread>();
        // Don't hold TF alive if there are no other threads running
        setDaemon(true);
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
        while (!isShutdown()) {
            Log.d(LOG_TAG, "Waiting for device to test");
            // Spawn off a thread for each allocated device.
            // The retrieval of a config to run on the device is done on this separate thread, to
            // prevent configs which only run on a specific device from blocking the rest
            final ITestDevice device = manager.allocateDevice();
            if (device != null) {
                InvocationThread invThread = startInvocation(manager, device);
                addInvocationThread(invThread);
            }
        }
        Log.i(LOG_TAG, "Waiting for invocation threads to complete");
        List<InvocationThread> threadListCopy;
        synchronized (this) {
            threadListCopy = new ArrayList<InvocationThread>(
                    mInvocationThreads.size());
            threadListCopy.addAll(mInvocationThreads);
        }
        for (Thread thread : threadListCopy) {
            waitForThread(thread);
        }
        Log.logAndDisplay(LogLevel.INFO, LOG_TAG, "All done");
        exit(manager);
    }

    private void waitForThread(Thread thread) {
        try {
            thread.join();
        } catch (InterruptedException e) {
            // ignore
            waitForThread(thread);
        }
    }

    private void exit(IDeviceManager manager) {
        if (manager != null) {
            manager.terminate();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addConfig(String[] args) {
        try {
            ConfigCommand cmd = new ConfigCommand(args);
            if (cmd.getCommandOptions().isHelpMode()) {
                cmd.getConfiguration().printCommandUsage(System.out);
            } else {
                mConfigQueue.add(cmd);
            }
        } catch (ConfigurationException e) {
            System.out.println(String.format("Unrecognized arguments: %s", e.getMessage()));
            getConfigFactory().printHelp(System.out);
        }
    }

    /**
     * Dequeue the highest priority config from the queue that can run against the provided device.
     *
     * @param device the {@link ITestDevice} to run against
     * @return the {@link ConfigCommand} or <code>null</code>
     */
    private ConfigCommand dequeueConfigCommand(ITestDevice device) {
        if (isShutdown()) {
            return null;
        }
        ConfigCommand cmd = null;
        try {
            cmd = mConfigQueue.take(new DeviceCmdMatcher(device));
            if (cmd.getCommandOptions().isLoopMode()) {
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
        final long minLoopTime = cmd.getCommandOptions().getMinLoopTime();
        if (minLoopTime > 0) {
            // delay before adding config back to queue
            TimerTask delayConfig = new TimerTask() {
                @Override
                public void run() {
                    Log.d(LOG_TAG, String.format("Adding config '%s' back to queue",
                            getArgString(cmd.getArgs())));
                    try {
                        cmd.resetConfiguration();
                        mConfigQueue.add(cmd);
                    } catch (ConfigurationException e) {
                        Log.e(LOG_TAG, e);
                    }
                }
            };
            Log.d(LOG_TAG, String.format("Delay adding config '%s' back to queue for %d ms",
                    getArgString(cmd.getArgs()), minLoopTime));
            mConfigTimer.schedule(delayConfig, minLoopTime);
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

    private synchronized boolean isShutdown() {
        return mShutdown;
    }

    /**
     * {@inheritDoc}
     */
    public synchronized void shutdown() {
        if (!mShutdown) {
            mShutdown = true;
            mConfigQueue.clear();
            if (mConfigTimer != null) {
                mConfigTimer.cancel();
            }
            // interrupt current thread in case its blocked on allocateDevice call
            interrupt();

            for (InvocationThread invThread : mInvocationThreads) {
                invThread.shutdownInvocation();
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public synchronized void shutdownHard() {
        shutdown();
        Log.logAndDisplay(LogLevel.WARN, LOG_TAG, "Force killing adb connection");
        getDeviceManager().terminateHard();
    }

    // Implementations of the optional management interfaces
    /**
     * {@inheritDoc}
     */
    public Collection<ITestInvocation> listInvocations() throws UnsupportedOperationException {
        Collection<ITestInvocation> invs = new ArrayList<ITestInvocation>(mInvocationThreads.size());

        if (mInvocationThreads == null) {
            return null;
        }

        for (InvocationThread invThread : mInvocationThreads) {
            invs.add(invThread.getInvocation());
        }

        return invs;
    }

    /**
     * {@inheritDoc}
     */
    public boolean stopInvocation(ITestInvocation invocation) throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     */
    public Collection<String> listConfigs() throws UnsupportedOperationException {
        Iterator<ConfigCommand> configIter = mConfigQueue.iterator();
        Collection<String> stringConfigs = new ArrayList<String>();
        ConfigCommand config;

        while (configIter.hasNext()) {
            config = configIter.next();
            stringConfigs.add(getArgString(config.getArgs()));
        }

        return stringConfigs;
    }

    /**
     * Helper method for unit testing. Blocks until config queue is empty
     *
     * @throws InterruptedException
     */
    void waitForEmptyQueue() throws InterruptedException {
        while (mConfigQueue.size() > 0) {
            Thread.sleep(10);
        }
    }
}
