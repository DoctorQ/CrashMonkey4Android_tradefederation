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
import com.android.tradefed.device.IDeviceSelection;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.IRescheduler;
import com.android.tradefed.invoker.ITestInvocation;
import com.android.tradefed.invoker.TestInvocation;
import com.android.tradefed.log.LogRegistry;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.ArrayUtil;
import com.android.tradefed.util.ConditionPriorityBlockingQueue;
import com.android.tradefed.util.TableFormatter;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * A scheduler for running TradeFederation commands across all available devices.
 * <p/>
 * Will attempt to prioritize commands to run based on a total running count of their execution
 * time. e.g. infrequent or fast running commands will get prioritized over long running commands.
 * <p/>
 * Runs forever in background until shutdown.
 */
public class CommandScheduler extends Thread implements ICommandScheduler {

    /** the queue of commands ready to be executed. */
    private ConditionPriorityBlockingQueue<ExecutableCommand> mCommandQueue;

    /**
     * The thread-safe list of all active executable commands.
     */
    private List<ExecutableCommand> mAllCommands;

    /** list of active invocation threads */
    private Set<InvocationThread> mInvocationThreads;

    /** timer for scheduling commands to be re-queued for execution */
    private ScheduledThreadPoolExecutor mCommandTimer;

    private RemoteClient mRemoteClient = null;
    private RemoteManager mRemoteManager = null;

    /** latch used to notify other threads that this thread is running */
    private final CountDownLatch mRunLatch;

    /**
     * Delay time in ms for adding a command back to the queue if it failed to allocate a device.
     */
    private static final int NO_DEVICE_DELAY_TIME = 20;

    /** used to assign unique ids to each CommandTracker created */
    private int mCurrentCommandId = 0;

    /** flag for instructing scheduler to exit when no commands are present */
    private boolean mShutdownOnEmpty = false;

    private enum CommandState {
        WAITING_FOR_DEVICE("Wait_for_device"),
        EXECUTING("Executing"),
        SLEEPING("Sleeping");

        private String mDisplayName;

        CommandState(String displayName) {
            mDisplayName = displayName;
        }

        public String getDisplayName() {
            return mDisplayName;
        }
    }

    /**
     * Represents one active command added to the scheduler. Will track total execution time of all
     * instances of this command
     */
    private static class CommandTracker {
        private final int mId;
        private final String[] mArgs;

        /** the total amount of time this command was executing. Used to prioritize */
        private long mTotalExecTime = 0;

        CommandTracker(int id, String[] args) {
            mId = id;
            mArgs = args;
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

        int getId() {
            return mId;
        }
    }

    /**
     * Represents one instance of a command to be executed.
     */
    private class ExecutableCommand {
        private final CommandTracker mCmdTracker;
        private final IConfiguration mConfig;
        private final boolean mRescheduled;
        private final long mCreationTime;
        private CommandState mState;
        private Long mSleepTime;

        private ExecutableCommand(CommandTracker tracker, IConfiguration config,
                boolean rescheduled) {
            mConfig = config;
            mCmdTracker = tracker;
            mRescheduled = rescheduled;
            mCreationTime = System.currentTimeMillis();
            mState = CommandState.WAITING_FOR_DEVICE;
        }

        /**
         * Gets the {@link IConfiguration} for this command instance
         */
        public IConfiguration getConfiguration() {
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
            mState = CommandState.EXECUTING;
            mSleepTime = null;
        }

        public void commandFinished(long elapsedTime) {
            getCommandTracker().incrementExecTime(elapsedTime);
            mAllCommands.remove(this);
        }

        public boolean isRescheduled() {
            return mRescheduled;
        }

        public long getCreationTime() {
            return mCreationTime;
        }

        public boolean isLoopMode() {
            return mConfig.getCommandOptions().isLoopMode();
        }

        public CommandState getState() {
            return mState;
        }

        public void setSleepState(Long delayTime) {
            mSleepTime = delayTime;
            mState = CommandState.SLEEPING;
        }

        public void setWaitState() {
            mState = CommandState.WAITING_FOR_DEVICE;
            mSleepTime = null;
        }

        public Long getSleepTime() {
            return mSleepTime;
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
            // force loop mode to off, otherwise each rescheduled config will be treated as
            // a new command and added back to queue
            config.getCommandOptions().setLoopMode(false);
            ExecutableCommand rescheduledCmd = createExecutableCommand(mCmdTracker, config, true);
            return addExecCommandToQueue(rescheduledCmd, 0);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean rescheduleCommand() {
            try {
                IConfiguration config = getConfigFactory().createConfigurationFromArgs(
                        mCmdTracker.getArgs());
                ExecutableCommand execCmd = createExecutableCommand(mCmdTracker, config, true);
                return addExecCommandToQueue(execCmd, config.getCommandOptions().getMinLoopTime());
            } catch (ConfigurationException e) {
                // FIXME: do this with jline somehow for ANSI support
                // note: make sure not to log (aka record) this line, as (args) may contain
                // passwords.
                System.out.println(String.format("Error while processing args: %s",
                        Arrays.toString(mCmdTracker.getArgs())));
                System.out.println(e.getMessage());
                System.out.println();
                return false;
            }
        }
    }

    /**
     * Comparator for {@link ExecutableCommand}.
     * <p/>
     * Delegates to {@link CommandTrackerTimeComparator}.
     */
    private static class ExecutableCommandComparator implements Comparator<ExecutableCommand> {
        CommandTrackerTimeComparator mTrackerComparator = new CommandTrackerTimeComparator();

        /**
         * {@inheritDoc}
         */
        @Override
        public int compare(ExecutableCommand c1, ExecutableCommand c2) {
            return mTrackerComparator.compare(c1.getCommandTracker(), c2.getCommandTracker());
        }
    }

    /**
     * Comparator for {@link CommandTracker}.
     * <p/>
     * Compares by mTotalExecTime, prioritizing configs with lower execution time
     */
    private static class CommandTrackerTimeComparator implements Comparator<CommandTracker> {

        @Override
        public int compare(CommandTracker c1, CommandTracker c2) {
            if (c1.getTotalExecTime() == c2.getTotalExecTime()) {
                return 0;
            } else if (c1.getTotalExecTime() < c2.getTotalExecTime()) {
                return -1;
            } else {
                return 1;
            }
        }
    }

    /**
     * Comparator for {@link CommandTracker}.
     * <p/>
     * Compares by id.
     */
    private static class CommandTrackerIdComparator implements Comparator<CommandTracker> {

        @Override
        public int compare(CommandTracker c1, CommandTracker c2) {
            if (c1.getId() == c2.getId()) {
                return 0;
            } else if (c1.getId() < c2.getId()) {
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
        private final ITestInvocation mInvocation;
        private long mStartTime = -1;

        public InvocationThread(String name, IDeviceManager manager, ITestDevice device,
                ExecutableCommand command) {
            // create a thread group so LoggerRegistry can identify this as an invocationThread
            super(new ThreadGroup(name), name);
            mManager = manager;
            mDevice = device;
            mCmd = command;
            mInvocation = createRunInstance();
        }

        public long getStartTime() {
            return mStartTime;
        }

        @Override
        public void run() {
            FreeDeviceState deviceState = FreeDeviceState.AVAILABLE;
            mStartTime = System.currentTimeMillis();
            ITestInvocation instance = getInvocation();
            IConfiguration config = mCmd.getConfiguration();
            try {
                mCmd.commandStarted();
                instance.invoke(mDevice, config, new Rescheduler(mCmd.getCommandTracker()));
            } catch (DeviceUnresponsiveException e) {
                CLog.w("Device %s is unresponsive. Reason: %s", mDevice.getSerialNumber(),
                        e.getMessage());
                deviceState = FreeDeviceState.UNRESPONSIVE;
            } catch (DeviceNotAvailableException e) {
                CLog.w("Device %s is not available. Reason: %s", mDevice.getSerialNumber(),
                        e.getMessage());
                deviceState = FreeDeviceState.UNAVAILABLE;
            } catch (FatalHostError e) {
                CLog.logAndDisplay(LogLevel.ERROR, "Fatal error occurred: %s, shutting down",
                        e.getMessage());
                if (e.getCause() != null) {
                    CLog.e(e.getCause());
                }
                shutdown();
            } catch (Throwable e) {
                CLog.e(e);
            } finally {
                long elapsedTime = System.currentTimeMillis() - mStartTime;
                CLog.i("Updating command '%s' with elapsed time %d ms",
                        getArgString(mCmd.getCommandTracker().getArgs()), elapsedTime);
                mCmd.commandFinished(elapsedTime);
                mManager.freeDevice(mDevice, deviceState);
                remoteFreeDevice(mDevice);
                removeInvocationThread(this);
            }
        }

        ITestInvocation getInvocation() {
            return mInvocation;
        }

        ITestDevice getDevice() {
            return mDevice;
        }
    }

    /**
     * Creates a {@link CommandScheduler}.
     * <p />
     * Note: logging is initialized here. We assume that {@link CommandScheduler#start} will be
     * called, so that we can clean logs up at the end of the {@link CommandScheduler#run} method.
     * In particular, this means that a leak will result if a {@link CommandScheduler} instance is
     * instantiated but not started.
     */
    public CommandScheduler() {
        initLogging();
        initDeviceManager();

        mCommandQueue = new ConditionPriorityBlockingQueue<ExecutableCommand>(
                new ExecutableCommandComparator());
        mAllCommands = Collections.synchronizedList(new LinkedList<ExecutableCommand>());
        mInvocationThreads = new HashSet<InvocationThread>();
        // use a ScheduledThreadPoolExecutorTimer as a single-threaded timer. This class
        // is used instead of a java.util.Timer because it offers advanced shutdown options
        mCommandTimer = new ScheduledThreadPoolExecutor(1);
        mRunLatch = new CountDownLatch(1);
    }

    /**
     * Initialize the device manager, optionally using a global device filter if specified.
     */
    void initDeviceManager() {
        getDeviceManager().init();
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
        try {
            // Notify other threads that we're running.
            mRunLatch.countDown();
            
            IDeviceManager manager = getDeviceManager();
            while (!isShutdown()) {
                ExecutableCommand cmd = dequeueConfigCommand();
                if (cmd != null) {
                	IDeviceSelection options = cmd.getConfiguration().getDeviceRequirements();
                    ITestDevice device = manager.allocateDevice(0, options);
                    if (device != null) {
                        // Spawn off a thread to perform the invocation
                        InvocationThread invThread = startInvocation(manager, device, cmd);
                        addInvocationThread(invThread);
                        if (cmd.isLoopMode()) {
                            addNewExecCommandToQueue(cmd.getCommandTracker());
                        }
                    } else {
                        // no device available for command, put back in queue
                        // increment exec time to ensure fair scheduling among commands when devices
                        // are scarce
                        cmd.getCommandTracker().incrementExecTime(1);
                        addExecCommandToQueue(cmd, NO_DEVICE_DELAY_TIME);
                        //CLog.logAndDisplay(LogLevel.ERROR,String.format("Can't find device %s.",options.getSerials()));
                    }
                }
            }
            mCommandTimer.shutdown();
            CLog.i("Waiting for invocation threads to complete");
            List<InvocationThread> threadListCopy;
            synchronized (this) {
                threadListCopy = new ArrayList<InvocationThread>(mInvocationThreads.size());
                threadListCopy.addAll(mInvocationThreads);
            }
            for (Thread thread : threadListCopy) {
                waitForThread(thread);
            }
            closeRemoteClient();
            if (mRemoteManager != null) {
                mRemoteManager.cancel();
            }
            exit(manager);
            cleanUp();
            CLog.logAndDisplay(LogLevel.INFO, "All done");
        } finally {
            // Make sure that we don't quit with messages still in the buffers
            System.err.flush();
            System.out.flush();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void await() throws InterruptedException {
        while (mRunLatch.getCount() > 0) {
            mRunLatch.await();
        }
    }

    private void closeRemoteClient() {
        if (mRemoteClient != null) {
            try {
                mRemoteClient.sendClose();
                mRemoteClient.close();
            } catch (IOException e) {
                // ignore
            }
        }
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
        return addCommand(args, 0);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean addCommand(String[] args, long totalExecTime) {
        try {
        	//得到cts配置文件的信息
            IConfiguration config = getConfigFactory().createConfigurationFromArgs(args);
            //打印帮助信息,只打印Importance类型的option信息
            if (config.getCommandOptions().isHelpMode()) {
                getConfigFactory().printHelpForConfig(args, true, System.out);
                System.out.flush();
                //打印所有option信息
            } else if (config.getCommandOptions().isFullHelpMode()) {
                getConfigFactory().printHelpForConfig(args, false, System.out);
            } else if (config.getCommandOptions().isDryRunMode()) {
                if (config.getCommandOptions().isNoisyDryRunMode()) {
                    CLog.logAndDisplay(LogLevel.DEBUG, "DRY RUN: %s", Arrays.toString(args));
                } else {
                    CLog.d("Dry run mode; skipping adding command: %s", Arrays.toString(args));
                }
            } else {
                config.validateOptions();

                if (config.getCommandOptions().runOnAllDevices()) {
                    addCommandForAllDevices(totalExecTime, args);
                } else {
                    CommandTracker cmdTracker = createCommandTracker(args);
                    cmdTracker.incrementExecTime(totalExecTime);
                    ExecutableCommand cmdInstance = createExecutableCommand(cmdTracker, config, false);
                    addExecCommandToQueue(cmdInstance, 0);
                }
                return true;
            }
        } catch (ConfigurationException e) {
        	e.printStackTrace();
            // FIXME: do this with jline somehow for ANSI support
            // note: make sure not to log (aka record) this line, as (args) may contain passwords.
            System.out.println(String.format("Error while processing args: %s",
                    Arrays.toString(args)));
            System.out.println(e.getMessage());
            System.out.println();
        }
        return false;
    }

    /**
     * Creates a new command for each connected device, and adds each to the queue.
     * <p/>
     * Note this won't have the desired effect if user has specified other
     * conflicting {@link IConfiguration#getDeviceRequirements()}in the command.
     */
    private void addCommandForAllDevices(long totalExecTime, String[] args)
            throws ConfigurationException {
        Set<String> devices = new HashSet<String>();
        devices.addAll(getDeviceManager().getAvailableDevices());
        devices.addAll(getDeviceManager().getAllocatedDevices());
        // schedule for for unavailable devices, just in case they come back online
        devices.addAll(getDeviceManager().getUnavailableDevices());

        for (String device : devices) {
            String[] argsWithDevice = Arrays.copyOf(args, args.length + 2);
            argsWithDevice[argsWithDevice.length - 2] = "-s";
            argsWithDevice[argsWithDevice.length - 1] = device;
            CommandTracker cmdTracker = createCommandTracker(argsWithDevice);
            cmdTracker.incrementExecTime(totalExecTime);
            IConfiguration config = getConfigFactory().createConfigurationFromArgs(
                    cmdTracker.getArgs());
            CLog.logAndDisplay(LogLevel.INFO, "Scheduling '%s' on '%s'", cmdTracker.getArgs()[0],
                    device);
            config.getDeviceRequirements().setSerial(device);
            ExecutableCommand execCmd = createExecutableCommand(cmdTracker, config, false);
            addExecCommandToQueue(execCmd, 0);
        }
    }

    /**
     * Creates a new {@link CommandTracker} with a unique id.
     */
    private synchronized CommandTracker createCommandTracker(String[] args) {
        mCurrentCommandId++;
        return new CommandTracker(mCurrentCommandId, args);
    }

    /**
     * Creates a new {@link ExecutableCommand}, and adds it to the all commands tracking list.
     */
    private ExecutableCommand createExecutableCommand(CommandTracker cmdTracker,
            IConfiguration config, boolean rescheduled) {
        ExecutableCommand cmd = new ExecutableCommand(cmdTracker, config, rescheduled);
        mAllCommands.add(cmd);
        return cmd;
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
            CLog.i("Waiting for command interrupted");
        }
        return null;
    }

    /**
     * Get the poll time to wait to retrieve a command to execute.
     * <p/>
     * Exposed so unit tests can mock.
     */
    long getCommandPollTimeMs() {
        return 1000;
    }

    /**
     * Creates a new {@link ExecutableCommand}, and adds it to queue
     *
     * @param commandTracker
     */
    private void addNewExecCommandToQueue(CommandTracker commandTracker) {
        try {
            IConfiguration config = getConfigFactory().createConfigurationFromArgs(
                    commandTracker.getArgs());
            ExecutableCommand execCmd = createExecutableCommand(commandTracker, config, false);
            addExecCommandToQueue(execCmd, config.getCommandOptions().getMinLoopTime());
        } catch (ConfigurationException e) {
            CLog.e(e);
        }
    }

    /**
     * Adds executable command instance to queue, with optional delay.
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
            cmd.setSleepState(delayTime);
            // delay before making command active
            Runnable delayCommand = new Runnable() {
                @Override
                public void run() {
                    synchronized (CommandScheduler.this) {
                        cmd.setWaitState();
                        mCommandQueue.add(cmd);
                    }
                }
            };
            mCommandTimer.schedule(delayCommand, delayTime, TimeUnit.MILLISECONDS);
        } else {
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
        return ArrayUtil.join(" ", (Object[])args);
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
        return mCommandTimer.isShutdown() || (mShutdownOnEmpty && mAllCommands.isEmpty());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void shutdown() {
        if (!isShutdown()) {
            clearWaitingCommands();
            if (mCommandTimer != null) {
                mCommandTimer.shutdownNow();
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void shutdownOnEmpty() {
        if (!isShutdown()) {
            mShutdownOnEmpty = true;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void removeAllCommands() {
        if (mCommandTimer != null) {
            for (Runnable task : mCommandTimer.getQueue()) {
                mCommandTimer.remove(task);
            }
        }
        clearWaitingCommands();
    }

    /**
     * Clears all {@link ExecutableCommand} not currently executing.
     */
    private void clearWaitingCommands() {
        mCommandQueue.clear();
        synchronized (mAllCommands) {
            ListIterator<ExecutableCommand> cmdIter = mAllCommands.listIterator();
            while (cmdIter.hasNext()) {
                ExecutableCommand cmd = cmdIter.next();
                if (!cmd.getState().equals(CommandState.EXECUTING)) {
                    cmdIter.remove();
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized boolean handoverShutdown(int handoverPort) {
        if (mRemoteClient != null) {
            CLog.e("A handover has already been initiated");
            return false;
        }
        try {
            mRemoteClient = RemoteClient.connect(handoverPort);
            CLog.d("connected to remote manager at %d", handoverPort);
            // inform remote manager of the devices we are still using
            for (String deviceInUse : getDeviceManager().getAllocatedDevices()) {
                if (!mRemoteClient.sendFilterDevice(deviceInUse)) {
                    CLog.e("Failed to send command to remote manager");
                    return false;
                }
                CLog.d("Sent filter device %s command", deviceInUse);
            }
            // now send command info
            List<CommandTracker> cmdCopy = getCommandTrackers();
            // sort so high priority commands are sent first
            Collections.sort(cmdCopy, new CommandTrackerTimeComparator());
            for (CommandTracker cmd : cmdCopy) {
                mRemoteClient.sendAddCommand(cmd.getTotalExecTime(), cmd.mArgs);
            }
            shutdown();
            return true;

        } catch (IOException e) {
            CLog.e("Failed to connect to remote manager at port %d", handoverPort);
        }
        return false;
    }

    /**
     * @return the list of active {@link CommandTracker}. 'Active' here means all commands added
     * to the scheduler that are either executing, waiting for a device to execute on, or looping.
     */
    private List<CommandTracker> getCommandTrackers() {
        List<ExecutableCommand> cmdCopy = new ArrayList<ExecutableCommand>(mAllCommands);
        Set<CommandTracker> cmdTrackers = new LinkedHashSet<CommandTracker>();
        for (ExecutableCommand cmd : cmdCopy) {
            cmdTrackers.add(cmd.getCommandTracker());
        }
        return new ArrayList<CommandTracker>(cmdTrackers);
    }

    /**
     * Inform the remote listener of the freed device. Has no effect if there is no remote listener.
     *
     * @param device the freed {@link ITestDevice}
     */
    private void remoteFreeDevice(ITestDevice device) {
        // TODO: send freed device state too
        if (mRemoteClient != null) {
            try {
                mRemoteClient.sendUnfilterDevice(device.getSerialNumber());
            } catch (IOException e) {
                CLog.e("Failed to send unfilter device %s to remote manager",
                        device.getSerialNumber());
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void shutdownHard() {
        shutdown();
        CLog.logAndDisplay(LogLevel.WARN, "Force killing adb connection");
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

    /**
     * {@inheritDoc}
     */
    @Override
    public void displayInvocationsInfo(PrintWriter printWriter) {
        if (mInvocationThreads == null || mInvocationThreads.size() == 0) {
            return;
        }
        List<InvocationThread> copy = new ArrayList<InvocationThread>(mInvocationThreads);
        ArrayList<List<String>> displayRows = new ArrayList<List<String>>();
        displayRows.add(Arrays.asList("Command Id", "Exec Time", "Device", "State"));
        long curTime = System.currentTimeMillis();

        for (InvocationThread invThread : copy) {
            displayRows.add(Arrays.asList(
                    Integer.toString(invThread.mCmd.getCommandTracker().getId()),
                    getTimeString(curTime - invThread.getStartTime()),
                    invThread.getDevice().getSerialNumber(),
                    invThread.getInvocation().toString()));
        }
        new TableFormatter().displayTable(displayRows, printWriter);
    }

    private String getTimeString(long elapsedTime) {
        long duration = elapsedTime / 1000;
        long secs = duration % 60;
        long mins = (duration / 60) % 60;
        long hrs = duration / (60 * 60);
        String time = "unknown";
        if (hrs > 0) {
            time = String.format("%dh:%02d:%02d", hrs, mins, secs);
        } else {
            time = String.format("%dm:%02d", mins, secs);
        }
        return time;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean stopInvocation(ITestInvocation invocation) throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void displayCommandsInfo(PrintWriter printWriter) {
        List<CommandTracker> cmds = getCommandTrackers();
        Collections.sort(cmds, new CommandTrackerIdComparator());
        for (CommandTracker cmd : cmds) {
            String cmdDesc = String.format("Command %d: [%s] %s", cmd.getId(),
                    getTimeString(cmd.getTotalExecTime()), getArgString(cmd.getArgs()));
            printWriter.println(cmdDesc);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void displayCommandQueue(PrintWriter printWriter) {
        if (mAllCommands.isEmpty()) {
            return;
        }
        ArrayList<List<String>> displayRows = new ArrayList<List<String>>();
        displayRows.add(Arrays.asList("Id", "Config", "Created", "State", "Sleep time",
                "Rescheduled", "Loop"));
        long curTime = System.currentTimeMillis();
        List<ExecutableCommand> cmdCopy = new ArrayList<ExecutableCommand>(mAllCommands);
        for (ExecutableCommand cmd : cmdCopy) {
            dumpCommand(curTime, cmd, displayRows);
        }
        new TableFormatter().displayTable(displayRows, printWriter);
    }

    private void dumpCommand(long curTime, ExecutableCommand cmd,
            ArrayList<List<String>> displayRows) {
        String sleepTime = cmd.getSleepTime() == null ? "N/A" : getTimeString(cmd.getSleepTime());
        displayRows.add(Arrays.asList(
                Integer.toString(cmd.getCommandTracker().getId()),
                cmd.getCommandTracker().getArgs()[0],
                getTimeString(curTime - cmd.getCreationTime()),
                cmd.getState().getDisplayName(),
                sleepTime,
                Boolean.toString(cmd.isRescheduled()),
                Boolean.toString(cmd.isLoopMode())));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int startRemoteManager() {
        if (mRemoteManager != null && !mRemoteManager.isCanceled()) {
            CLog.logAndDisplay(LogLevel.INFO, "A remote manager is already running at port %d",
                    mRemoteManager.getPort());
            return -1;
        }
        mRemoteManager = new RemoteManager(getDeviceManager(), this);
        mRemoteManager.start();
        int port = mRemoteManager.getPort();
        if (port == -1) {
            // set mRemoteManager to null so it can be started again if necessary
            mRemoteManager = null;
        }
        return port;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stopRemoteManager() {
        if (mRemoteManager == null) {
            CLog.logAndDisplay(LogLevel.INFO, "A remote manager is not running");
            return;
        }
        mRemoteManager.cancel();
        mRemoteManager = null;
    }
}
