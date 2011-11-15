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
import com.android.tradefed.config.ConfigurationFactory;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IConfigurationFactory;
import com.android.tradefed.device.DeviceManager;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.DeviceUnresponsiveException;
import com.android.tradefed.device.IDeviceManager;
import com.android.tradefed.device.IDeviceManager.FreeDeviceState;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.IRescheduler;
import com.android.tradefed.invoker.ITestInvocation;
import com.android.tradefed.invoker.TestInvocation;
import com.android.tradefed.log.LogRegistry;
import com.android.tradefed.util.ConditionPriorityBlockingQueue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
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
    private ConditionPriorityBlockingQueue<ExecutableCommand> mCommandQueue;
    /**
     * The thread-safe list of all active commands that are being tracked.
     */
    private List<CommandTracker> mAllCommands;
    /**  list of active invocation threads */
    private Set<InvocationThread> mInvocationThreads;

    /** timer for scheduling commands to be re-queued for execution */
    private ScheduledThreadPoolExecutor mCommandTimer;

    /**
     * Delay time in ms for adding a command back to the queue if it failed to allocate a device.
     */
    private static final int NO_DEVICE_DELAY_TIME = 20;

    /**
     * Represents one active command added to the scheduler. Will track total execution time of
     * all instances of this command
     */
    private class CommandTracker {
        private final String[] mArgs;
        private final ICommandOptions mCmdOptions;
        private final ICommandListener mListener;

        /** the total amount of time this command was executing. Used to prioritize */
        private long mTotalExecTime = 0;

        CommandTracker(String[] args, ICommandOptions cmdOptions, ICommandListener listener) {
            mArgs = args;
            mCmdOptions = cmdOptions;
            mListener = listener;
        }

        synchronized void incrementExecTime(long execTime) {
            mTotalExecTime += execTime;
        }

        /**
         * @return the total amount of execution time for this command.
         */
        synchronized long getTotalExecTime() {
            return mTotalExecTime;
        }

        /**
         * Get the full list of config arguments associated with this command.
         */
        String[] getArgs() {
            return mArgs;
        }

        ICommandOptions getCommandOptions() {
            return mCmdOptions;
        }

        /**
         * Callback to inform listener that command has started execution.
         */
        synchronized void commandStarted() {
            if (mListener != null) {
                mListener.commandStarted();
            }
        }
    }

    /**
     * Represents one instance of a command to be executed.
     */
    private class ExecutableCommand {
        private final CommandTracker mCmdTracker;
        private final IConfiguration mConfig;

        ExecutableCommand(CommandTracker tracker, IConfiguration config) {
            mConfig = config;
            mCmdTracker = tracker;
        }

        /**
         * Gets the {@link IConfiguration} for this command instance
         */
        public IConfiguration getConfiguration()  {
            return mConfig;
        }

        /**
         * Gets the associated {@link CommandTracker}.
         */
        CommandTracker getCommandTracker() {
            return mCmdTracker;
        }

        /**
         * Callback to inform listener that command has started execution.
         */
        void commandStarted() {
            mCmdTracker.commandStarted();
        }
    }

    /**
     * A {@link IRescheduler} that will add a command back to the queue.
     */
    private class Rescheduler implements IRescheduler {

        private CommandTracker mCmdTracker;

        Rescheduler(CommandTracker cmdTracker) {
            mCmdTracker = cmdTracker;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean scheduleConfig(IConfiguration config) {
            ExecutableCommand rescheduledCmd = new ExecutableCommand(mCmdTracker, config);
            return addExecCommandToQueue(rescheduledCmd, 0);
        }
    }

    /**
     * Comparator for ConfigCommmand.
     * <p/>
     * Compares by mTotalExecTime, prioritizing configs with lower execution time
     */
    private static class ConfigComparator implements Comparator<ExecutableCommand> {

        /**
         * {@inheritDoc}
         */
        public int compare(ExecutableCommand c1, ExecutableCommand c2) {
            if (c1.getCommandTracker().getTotalExecTime() == c2.getCommandTracker()
                    .getTotalExecTime()) {
                return 0;
            } else if (c1.getCommandTracker().getTotalExecTime() < c2.getCommandTracker()
                    .getTotalExecTime()) {
                return -1;
            } else {
                return 1;
            }
        }
    }

    private class InvocationThread extends Thread {
        private final IDeviceManager mManager;
        private final ITestDevice mDevice;
        private final ExecutableCommand mCmd;
        private ITestInvocation mInvocation = null;
        private long mStartTime = -1;

        public InvocationThread(String name, IDeviceManager manager, ITestDevice device,
                ExecutableCommand command) {
            // create a thread group so LoggerRegistry can identify this as an invocationThread
            super (new ThreadGroup(name), name);
            mManager = manager;
            mDevice = device;
            mCmd = command;
        }

        private synchronized ITestInvocation createInvocation() {
            mInvocation = createRunInstance();
            return mInvocation;
        }

        public long getStartTime() {
            return mStartTime;
        }

        @Override
        public void run() {
            FreeDeviceState deviceState = FreeDeviceState.AVAILABLE;
            mStartTime = System.currentTimeMillis();
            ITestInvocation instance = createInvocation();
            IConfiguration config = mCmd.getConfiguration();
            try {
                mCmd.commandStarted();
                instance.invoke(mDevice, config, new Rescheduler(mCmd.getCommandTracker()));
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
                long elapsedTime = System.currentTimeMillis() - mStartTime;
                Log.i(LOG_TAG, String.format("Updating command '%s' with elapsed time %d ms",
                        getArgString(mCmd.getCommandTracker().getArgs()), elapsedTime));
                mCmd.getCommandTracker().incrementExecTime(elapsedTime);
                if (!mCmd.getCommandTracker().getCommandOptions().isLoopMode()) {
                    mAllCommands.remove(mCmd.getCommandTracker());
                }
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
     * <p />
     * Note: logging is initialized here.  We assume that {@link CommandScheduler#start} will be
     * called, so that we can clean logs up at the end of the {@link CommandScheduler#run} method.
     * In particular, this means that a leak will result if a {@link CommandScheduler} instance is
     * instantiated but not started.
     */
    public CommandScheduler() {
        initLogging();

        // initialize the device manager
        getDeviceManager().init();

        mCommandQueue = new ConditionPriorityBlockingQueue<ExecutableCommand>(
                new ConfigComparator());
        mAllCommands = Collections.synchronizedList(new LinkedList<CommandTracker>());
        mInvocationThreads = new HashSet<InvocationThread>();
        // use a ScheduledThreadPoolExecutorTimer as a single-threaded timer. This class
        // is used instead of a java.util.Timer because it offers advanced shutdown options
        mCommandTimer = new ScheduledThreadPoolExecutor(1);
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
        while (!isShutdown()) {
            ExecutableCommand cmd = dequeueConfigCommand();
            if (cmd != null) {
                ITestDevice device = manager.allocateDevice(0,
                        cmd.getConfiguration().getDeviceRequirements());
                if (device != null) {
                    // Spawn off a thread to perform the invocation
                    InvocationThread invThread = startInvocation(manager, device, cmd);
                    addInvocationThread(invThread);
                    if (cmd.getCommandTracker().getCommandOptions().isLoopMode()) {
                        addNewExecCommandToQueue(cmd.getCommandTracker());
                    }
                }
                else {
                    // no device available for command, put back in queue
                    // increment exec time to ensure fair scheduling among commands when devices are
                    // scarce
                    cmd.getCommandTracker().incrementExecTime(1);
                    addExecCommandToQueue(cmd, NO_DEVICE_DELAY_TIME);
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
        cleanUp();
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
    public boolean addCommand(String[] args) {
        return addCommand(args, null);
   }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean addCommand(String[] args, ICommandListener listener) {
        try {
            IConfiguration config = getConfigFactory().createConfigurationFromArgs(args);
            if (config.getCommandOptions().isHelpMode()) {
                getConfigFactory().printHelpForConfig(args, true, System.out);
            } else if (config.getCommandOptions().isFullHelpMode()) {
                getConfigFactory().printHelpForConfig(args, false, System.out);
            } else {
                CommandTracker cmdTracker = new CommandTracker(args, config.getCommandOptions(),
                        listener);
                ExecutableCommand cmdInstance = new  ExecutableCommand(cmdTracker, config);
                mCommandQueue.add(cmdInstance);
                mAllCommands.add(cmdTracker);
                return true;
            }
        } catch (ConfigurationException e) {
            // FIXME: do this with jline somehow for ANSI support
            System.out.println();
            System.out.println(e.getMessage());
            System.out.println();
        }
        return false;
    }

    /**
     * Dequeue the highest priority command from the queue.
     *
     * @return the {@link ExecutableCommand} or <code>null</code>
     */
    private ExecutableCommand dequeueConfigCommand() {
        try {
            // poll for a command, rather than block indefinitely, to handle shutdown case
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
     * Creates a new {@link ExecutableCommand}, and adds it to queue
     * @param commandTracker
     */
    private void addNewExecCommandToQueue(CommandTracker commandTracker) {
        try {
            IConfiguration config = getConfigFactory().createConfigurationFromArgs(
                    commandTracker.getArgs());
            ExecutableCommand execCmd = new ExecutableCommand(commandTracker, config);
            addExecCommandToQueue(execCmd, commandTracker.getCommandOptions().getMinLoopTime());
        } catch (ConfigurationException e) {
            Log.e(LOG_TAG, e);
        }
    }

    /**
     * Return command to queue, with delay if necessary
     *
     * @param cmd the {@link ExecutableCommand} to return to queue
     * @param delayTime the time in ms to delay before adding command to queue
     * @return <code>true</code> if command will be added to queue, <code>false</code> otherwise
     */
    private synchronized boolean addExecCommandToQueue(final ExecutableCommand cmd,
            long delayTime) {
        if (isShutdown()) {
            return false;
        }
        if (delayTime > 0) {
            // delay before adding command back to queue
            Runnable delayCommand = new Runnable() {
                @Override
                public void run() {
                    mCommandQueue.add(cmd);
                }
            };
            mCommandTimer.schedule(delayCommand, delayTime, TimeUnit.MILLISECONDS);
        } else {
            // return to queue immediately
            mCommandQueue.add(cmd);
        }
        return true;
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
     * @param cmd the {@link ExecutableCommand} to execute
     * @return the invocation's thread
     */
    private InvocationThread startInvocation(IDeviceManager manager, ITestDevice device,
            ExecutableCommand cmd) {
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
        return mCommandTimer.isShutdown();
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
            mAllCommands.clear();
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

    /**
     * Initializes the ddmlib log.
     * <p />
     * Exposed so unit tests can mock.
     */
    void initLogging() {
        DdmPreferences.setLogLevel(LogLevel.VERBOSE.getStringValue());
        Log.setLogOutput(LogRegistry.getLogRegistry());
    }

    /**
     * Closes the logs and does any other necessary cleanup before we quit.
     * <p />
     * Exposed so unit tests can mock.
     */
    void cleanUp() {
        LogRegistry.getLogRegistry().closeAndRemoveAllLogs();
    }


    // Implementations of the optional management interfaces
    /**
     * {@inheritDoc}
     */
    public Collection<String> listInvocations() throws UnsupportedOperationException {
        if (mInvocationThreads == null) {
            return null;
        }

        Collection<String> invs = new ArrayList<String>(mInvocationThreads.size());
        long curTime = System.currentTimeMillis();

        for (InvocationThread invThread : mInvocationThreads) {
            long duration = (curTime - invThread.getStartTime()) / 1000;
            long secs = duration % 60;
            long mins = (duration / 60) % 60;
            long hrs = duration / (60 * 60);
            String time = "unknown";
            if (hrs > 0) {
                time = String.format("%dh:%02d:%02d", hrs, mins, secs);
            } else {
                time = String.format("%dm:%02d", mins, secs);
            }

            invs.add(String.format("[%s] %s", time, invThread.getInvocation()));
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
            for (CommandTracker cmdTracker : mAllCommands) {
                stringCommands.add(getArgString(cmdTracker.getArgs()));
            }
        }
        return stringCommands;
    }
}
