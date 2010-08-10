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

import java.lang.UnsupportedOperationException;

import java.util.Collection;

/**
 * A scheduler for running TradeFederation configs.
 */
public interface ICommandScheduler {

    /**
     * Adds a configuration to the scheduler.
     * <p/>
     * If "--help" argument is specified, or the config arguments are invalid, the help text for
     * the config will be outputed to stdout. Otherwise, the config will be added to the queue to
     * run.
     *
     * @param args the config arguments.
     *
     * @see {@link IConfigurationFactory#createConfigurationFromArgs(String[])}
     */
    public void addConfig(String[] args);

    /**
     * Attempt to gracefully shutdown the command scheduler.
     * <p/>
     * Clears configurations waiting to be tested, and requests that all invocations in progress
     * shut down gracefully.
     * <p/>
     * After shutdown is called, the scheduler main loop will wait for all invocations in progress
     * to complete before exiting completely.
     */
    public void shutdown();

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


    // The following are optional management-related interfaces.
    /**
     * Get a list of current invocations.
     *
     * @return A list of currently-running {@link ITestInvocation}
     * @throw {@link UnsupportedOperationException} if the implementation doesn't support this
     */
    public Collection<ITestInvocation> listInvocations() throws UnsupportedOperationException;

    /**
     * Stop a running invocation.
     *
     * @return true if the invocation was stopped, false otherwise
     * @throw {@link UnsupportedOperationException} if the implementation doesn't support this
     */
    public boolean stopInvocation(ITestInvocation invocation) throws UnsupportedOperationException;
}

