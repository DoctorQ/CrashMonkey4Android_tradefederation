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

package com.android.tradefed.config;

import com.android.tradefed.device.IDeviceRecovery;
import com.android.tradefed.log.ILeveledLogOutput;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.targetsetup.IBuildProvider;
import com.android.tradefed.targetsetup.ITargetPreparer;

import java.io.PrintStream;
import java.util.Collection;

import junit.framework.Test;

/**
 * Configuration information for a TradeFederation invocation.
 *
 * Each TradeFederation invocation has a single {@link IConfiguration}. An {@link IConfiguration}
 * stores all the delegate objects that should be used during the invocation, and their associated
 * {@link Option}'s
 */
public interface IConfiguration {

    /**
     * Gets the {@link IBuildProvider} from the configuration.
     *
     * @return the {@link IBuildProvider} provided in the configuration
     * @throws ConfigurationException if config object could not be fully loaded, or was not the
     * correct type
     */
    public IBuildProvider getBuildProvider() throws ConfigurationException;

    /**
     * Gets the {@link ITargetPreparer} from the configuration.
     *
     * @return the {@link IBuildProvider} provided in the configuration
     * @throws ConfigurationException if config object could not be fully loaded, or was not the
     * correct type
     */
    public ITargetPreparer getTargetPreparer() throws ConfigurationException;

    /**
     * Gets the {@link Test} to run from the configuration.
     *
     * @return the {@link Test} provided in the configuration
     * @throws ConfigurationException if config object could not be fully loaded, or was not the
     * correct type
     */
    public Test getTest()  throws ConfigurationException;

    /**
     * Gets the {@link ITestInvocationListener} to use from the configuration.
     *
     * @return the {@link Test} provided in the configuration
     * @throws ConfigurationException if config object could not be fully loaded, or was not the
     * correct type
     */
    public ITestInvocationListener getTestInvocationListener() throws ConfigurationException;

    /**
     * Gets the {@link IDeviceRecovery} to use from the configuration.
     *
     * @return the {@link IDeviceRecovery} provided in the configuration.
     * @throws ConfigurationException if config object could not be fully loaded, or was not the
     * correct type
     */
    public IDeviceRecovery getDeviceRecovery() throws ConfigurationException;

    /**
     * Gets the {@link ILeveledLogOutput} to use from the configuration.
     *
     * @return the {@link ILeveledLogOutput} provided in the configuration.
     * @throws ConfigurationException if config object could not be fully loaded, or was not the
     * correct type
     */
    public ILeveledLogOutput getLogOutput() throws ConfigurationException;

    /**
     * Generic interface to get the configuration object with the given name.
     *
     * This will create the object if necessary, and set all of its {@link Option} fields with
     * values supplied in the configuration.
     *
     * In addition, if the current {@link IConfiguration} will be injected if the object
     * extends {@link IConfigurationReceiver}.
     *
     * @param name the unique name of the configuration object
     * @param expectedType the expected object type
     *
     * @return the configuration object, with all its {@link Option} fields set
     *
     * @throws ConfigurationException if config object could not be fully loaded, or was not the
     * correct type
     */
    public Object getConfigurationObject(String name, Class<?> expectedType)
            throws ConfigurationException;

    /**
     * Gets a list of all configuration objects.
     *
     * @return a {@link Collection} of all configuration objects
     * @throws {@link ConfigurationException} if the config objects could not be fully loaded
     */
    public Collection<? extends Object> getConfigurationObjects() throws ConfigurationException;

    /**
     * Outputs a command line usage help text to given printStream.
     *
     * @param out the {@link PrintStream} to use.
     * @throws {@link ConfigurationException}
     */
    public void printCommandUsage(PrintStream out) throws ConfigurationException;
}
