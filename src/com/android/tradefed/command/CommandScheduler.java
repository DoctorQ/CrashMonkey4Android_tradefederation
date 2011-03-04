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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * A scheduler for running TradeFederation commands across all available devices.
 * <p/>
 * Will attempt to prioritize commands to run based on a total running count of their
 * execution time. e.g. infrequent or fast running commands will get prioritized over long running
 * commands.
 * <p/>
 * Runs forever in background until shutdown.
 */
public class CommandScheduler extends Thread implements ICommandScheduler {

    private static final String LOG_TAG = "CommandScheduler";
    /** the queue of commands ready to be executed. */
    private ConditionPriorityBlockingQueue<ConfigCommand> mCommandQueue;
    /**
     * The thread-safe list of all commands. Unlike mConfigQueue will contain commands currently
     * rescheduled for execution at a later time.
     */
    private List<ConfigCommand> mAllCommands;
    /**  list of active invocation threads */
    private Set<InvocationThread> mInvocationThreads;

    /** timer for scheduling commands to be re-queued for execution */
    private ScheduledThreadPoolExecutor mCommandTimer;

    /**
     * Delay time in ms for adding a command back to the queue if it failed to allocate a device.
     */
    private static final int NO_DEVICE_DELAY_TIME = 20;

    /**
     * Represents one command to be executed
     */
    private class ConfigCommand {
        private final String[] mArgs;

        /** the total amount of time this command was executing. Used to prioritize */
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
         * Should be called once command is rescheduled for execution
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
            // a resumable command should never be in loop mode
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
     * A {@link IRescheduler} that will add a command back to the queue.
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
            if (isShutdown()) {
                // cannot schedule configs if shut down
                return false;
            }
            try {
                RescheduledConfigCommand rescheduledCmd = new RescheduledConfigCommand(mOrigCmd,
                        config);
                mCommandQueue.add(rescheduledCmd);
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

    private class InvocationThread extends Thread {
        private final IDeviceManager mManager;
        private final ITestDevice mDevice;
        private final ConfigCommand mCmd;
        private ITestInvocation mInvocation = null;

        public InvocationThread(String name, IDeviceManager manager, ITestDevice device,
                ConfigCommand command) {
            // create a thread group so LoggerRegistry can identify this as an invocationThread
            super (new ThreadGroup(name), name);
            mManager = manager;
            mDevice = device;
            mCmd = command;
        }

        private synchronized ITestInvocation createInvocation() {
            mInvocation  = createRunInstance();
            return mInvocation;
        }

        @Override
        public void run() {
            FreeDeviceState deviceState = FreeDeviceState.AVAILABLE;
            long startTime = System.currentTimeMillis();
            ITestInvocation instance = createInvocation();
            try {
                IConfiguration config = mCmd.getConfiguration();
                instance.invoke(mDevice, config, new Rescheduler(mCmd));
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
                Log.i(LOG_TAG, String.format("Updating command '%s' with elapsed time %d ms",
                        getArgString(mCmd.getArgs()), elapsedTime));
                mCmd.incrementExecTime(elapsedTime);
                mManager.freeDevice(mDevice, deviceState);
                removeInvocationThread(this);
            }
        }

        private synchronized ITestInvocation getInvocation() {
            return mInvocation;
        }
    }

    /**
     * Creates a {@link CommandScheduler}.
     */
    CommandScheduler() {
        mCommandQueue = new ConditionPriorityBlockingQueue<ConfigCommand>(new ConfigComparator());
        mAllCommands =  Collections.synchronizedList(new LinkedList<ConfigCommand>());
        mInvocationThreads = new HashSet<InvocationThread>();
        // use a ScheduledThreadPoolExecutorTimer as a single-threaded timer. This class
        // is used instead of a java.util.Timer because it offers advanced shutdown options
        mCommandTimer = new ScheduledThreadPoolExecutor(1);
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

        IDeviceManager manager = getDeviceManager();
        manager.init();
        while (!isShutdown()) {
            ConfigCommand cmd = dequeueConfigCommand();
            if (cmd != null) {
                ITestDevice device = manager.allocateDevice(0, cmd.getDeviceOptions());
                if (device != null) {
                    // Spawn off a thread to perform the invocation
                    InvocationThread invThread = startInvocation(manager, device, cmd);
                    addInvocationThread(invThread);
                    if (cmd.getCommandOptions().isLoopMode()) {
                        try {
                            cmd.resetConfiguration();
                            returnCommandToQueue(cmd, cmd.getCommandOptions().getMinLoopTime());
                        } catch (ConfigurationException e) {
                            Log.e(LOG_TAG, e);
                        }
                    }
                }
                else {
                    // no device available for command, put back in queue
                    // increment exec time to ensure fair scheduling among commands when devices are
                    // scarce
                    cmd.incrementExecTime(1);
                    returnCommandToQueue(cmd, NO_DEVICE_DELAY_TIME);
                }
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
    public void addCommand(String[] args) {
        try {
            ConfigCommand cmd = new ConfigCommand(args);
            if (cmd.getCommandOptions().isHelpMode()) {
                cmd.getConfiguration().printCommandUsage(System.out);
            } else {
                mCommandQueue.add(cmd);
            }
        } catch (ConfigurationException e) {
            System.out.println(String.format("Unrecognized arguments: %s", e.getMessage()));
            getConfigFactory().printHelp(System.out);
        }
    }

    /**
     * Dequeue the highest priority command from the queue.
     *
     * @return the {@link ConfigCommand} or <code>null</code>
     */
    private ConfigCommand dequeueConfigCommand() {
        try {
            // poll for a commmand, rather than block indefinitely, to handle shutdown case
            return mCommandQueue.poll(getCommandPollTimeMs(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Log.i(LOG_TAG, "Waiting for command interrupted");
        }
        return null;
    }

    /**
     * Get the poll time to wait for a command to execute.
     * <p/>
     * Exposed so unit tests can mock.
     * @return
     */
    long getCommandPollTimeMs() {
        return 1000;
    }

    /**
     * Return command to queue, with delay if necessary
     *
     * @param cmd the {@link ConfigCommand} to return to queue
     * @param delayTime the time in ms to delay before adding command to queue
     */
    private void returnCommandToQueue(final ConfigCommand cmd, long delayTime) {
        if (isShutdown()) {
            return;
        }
        if (delayTime > 0) {
            // delay before adding command back to queue
            Runnable delayCommand = new Runnable() {
                @Override
                public void run() {
                    try {
                        cmd.resetConfiguration();
                        mCommandQueue.add(cmd);
                    } catch (ConfigurationException e) {
                        Log.e(LOG_TAG, e);
                    }
                }
            };
            mCommandTimer.schedule(delayCommand, delayTime, TimeUnit.MILLISECONDS);
        } else {
            // return to queue immediately
            mCommandQueue.add(cmd);
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
     * Spawns off thread to run invocation for given device
     *
     * @param manager the {@link IDeviceManager} to return device to when complete
     * @param device the {@link ITestDevice}
     * @param cmd the {@link ConfigCommand} to execute
     * @return the invocation's thread
     */
    private InvocationThread startInvocation(IDeviceManager manager, ITestDevice device,
            ConfigCommand cmd) {
        final String invocationName = String.format("Invocation-%s", device.getSerialNumber());
        InvocationThread invocationThread = new InvocationThread(invocationName, manager, device,
                cmd);
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
        return mCommandTimer.isTerminated();
    }

    /**
     * {@inheritDoc}
     */
    public synchronized void shutdown() {
        if (!isShutdown()) {
            mCommandQueue.clear();
            if (mCommandTimer != null) {
                mCommandTimer.shutdownNow();
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
    public Collection<String> listCommands() throws UnsupportedOperationException {
        Collection<String> stringCommands = new ArrayList<String>();
        synchronized (mAllCommands) {
            for (ConfigCommand cmd : mAllCommands) {
                stringCommands.add(getArgString(cmd.getArgs()));
            }
        }
        return stringCommands;
    }


}
