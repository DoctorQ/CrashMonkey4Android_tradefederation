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

import com.android.tradefed.config.IConfigurationFactory;
import com.android.tradefed.invoker.ITestInvocation;

import java.io.PrintWriter;

/**
 * A scheduler for running TradeFederation commands.
 */
public interface ICommandScheduler {

    /**
     * Adds a command to the scheduler.
     * <p/>
     * A command is essentially an instance of a configuration to run and its associated arguments.
     * <p/>
     * If "--help" argument is specified, or the config arguments are invalid, the help text for
     * the config will be outputed to stdout. Otherwise, the config will be added to the queue to
     * run.
     *
     * @param args the config arguments.
     * @return <code>true</code> if command was added successfully
     *
     * @see {@link IConfigurationFactory#createConfigurationFromArgs(String[])}
     */
    public boolean addCommand(String[] args);

    /**
     * An alternate {@link #addCommand(String[])} that accepts an initial total
     * execution time for the command.
     * <p/>
     * Useful when transitioning pre-existing commands from another tradefed process
     *
     * @param args the config arguments.
     * @param totalExecTime the accumulated elapsed execution time of the command
     * @return <code>true</code> if command was added successfully
     */
    public boolean addCommand(String[] args, long totalExecTime);

    /**
     * Remove all commands from scheduler
     */
    public void removeAllCommands();

    /**
     * Attempt to gracefully shutdown the command scheduler.
     * <p/>
     * Clears commands waiting to be tested, and requests that all invocations in progress
     * shut down gracefully.
     * <p/>
     * After shutdown is called, the scheduler main loop will wait for all invocations in progress
     * to complete before exiting completely.
     */
    public void shutdown();

    /**
     * Similar to {@link #shutdown()}, but will instead wait for all commands to be executed
     * before exiting.
     * <p/>
     * Note that if any commands are in loop mode, the scheduler will never exit.
     */
    public void shutdownOnEmpty();

    /**
     * Initiates a {@link #shutdown()} and handover to another tradefed process on this same host.
     * <p/>
     * The scheduler will inform the remote tradefed process listening on that port of freed devices
     * as they become available.
     *
     * @return <code>true</code> if handover initiation was successful, <code>false</code>
     * otherwise
     */
    public boolean handoverShutdown(int handoverPort);

    /**
     * Attempt to forcefully shutdown the command scheduler.
     * <p/>
     * Similar to {@link #shutdown()}, but will also forcefully kill the adb connection, in an
     * attempt to 'inspire' invocations in progress to complete quicker.
     */
    public void shutdownHard();

    /**
     * Start the {@link ICommandScheduler}.
     * <p/>
     * Will run until {@link #shutdown()} is called.
     *
     * see {@link Thread#start()}.
     */
    public void start();

    /**
     * Waits for scheduler to complete.
     *
     * @see {@link Thread#join()}.
     */
    public void join() throws InterruptedException;

    /**
     * Waits for scheduler to start running.
     */
    public void await() throws InterruptedException;

    /**
     * Displays a list of current invocations.
     *
     * @param printWriter the {@link PrintWriter} to output to.
     */
    public void displayInvocationsInfo(PrintWriter printWriter);

    /**
     * Stop a running invocation.
     *
     * @return true if the invocation was stopped, false otherwise
     * @throws {@link UnsupportedOperationException} if the implementation doesn't support this
     */
    public boolean stopInvocation(ITestInvocation invocation) throws UnsupportedOperationException;

    /**
     * Output a list of current commands.
     *
     * @param printWriter the {@link PrintWriter} to output to.
     */
    public void displayCommandsInfo(PrintWriter printWriter);

    /**
     * Output detailed debug info on state of command execution queue.
     *
     * @param printWriter
     */
    public void displayCommandQueue(PrintWriter printWriter);

    /**
     * Starts a socket to listen to remote commands.
     * <p/>
     * Used to handle handover communication. @see {@link #handoverShutdown(int)}.
     *
     * @return the TCP port of the socket or -1 if it failed to start.
     */
    public int startRemoteManager();

    /**
     * Cancels the currently running remote manager.
     */
    public void stopRemoteManager();
}
