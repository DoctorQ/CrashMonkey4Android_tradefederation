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

import com.android.tradefed.build.IBuildProvider;
import com.android.tradefed.command.ICommandOptions;
import com.android.tradefed.device.IDeviceRecovery;
import com.android.tradefed.device.IDeviceSelection;
import com.android.tradefed.device.TestDeviceOptions;
import com.android.tradefed.log.ILeveledLogOutput;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.testtype.IRemoteTest;

import java.io.PrintStream;
import java.util.List;

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
     */
    public IBuildProvider getBuildProvider();

    /**
     * Gets the {@link ITargetPreparer}s from the configuration.
     *
     * @return the {@link ITargetPreparer}s provided in order in the configuration
     */
    public List<ITargetPreparer> getTargetPreparers();

    /**
     * Gets the {@link IRemoteTest}s to run from the configuration.
     *
     * @return the tests provided in the configuration
     */
    public List<IRemoteTest> getTests();

    /**
     * Gets the {@link ITestInvocationListener}s to use from the configuration.
     *
     * @return the {@link ITestInvocationListener}s provided in the configuration.
     */
    public List<ITestInvocationListener> getTestInvocationListeners();

    /**
     * Gets the {@link IDeviceRecovery} to use from the configuration.
     *
     * @return the {@link IDeviceRecovery} provided in the configuration.
     */
    public IDeviceRecovery getDeviceRecovery();

    /**
     * Gets the {@link TestDeviceOptions} to use from the configuration.
     *
     * @return the {@link TestDeviceOptions} provided in the configuration.
     */
    public TestDeviceOptions getDeviceOptions();

    /**
     * Gets the {@link ILeveledLogOutput} to use from the configuration.
     *
     * @return the {@link ILeveledLogOutput} provided in the configuration.
     */
    public ILeveledLogOutput getLogOutput();

    /**
     * Gets the {@link ICommandOptions} to use from the configuration.
     *
     * @return the {@link ICommandOptions} provided in the configuration.
     */
    public ICommandOptions getCommandOptions();

    /**
     * Gets the {@link IDeviceSelection} to use from the configuration.
     *
     * @return the {@link IDeviceSelection} provided in the configuration.
     */
    public IDeviceSelection getDeviceRequirements();

    /**
     * Generic interface to get the configuration object with the given type name.
     *
     * @param typeName the unique type of the configuration object
     *
     * @return the configuration object or <code>null</code> if the object type with given name
     * does not exist.
     */
    public Object getConfigurationObject(String typeName);

    /**
     * Similar to {@link #getConfigurationObject(String)}, but for configuration
     * object types that support multiple objects.
     *
     * @param typeName the unique type name of the configuration object
     *
     * @return the list of configuration objects or <code>null</code> if the object type with
     * given name does not exist.
     */
    public List<?> getConfigurationObjectList(String typeName);

    /**
     * Inject a option value into the set of configuration objects.
     * <p/>
     * Useful to provide values for options that are generated dynamically.
     *
     * @param optionName the option name
     * @param optionValue the option value
     * @throws ConfigurationException if failed to set the option's value
     */
    public void injectOptionValue(String optionName, String optionValue)
            throws ConfigurationException;

    /**
     * Inject a option value into the set of configuration objects.
     * <p/>
     * Useful to provide values for options that are generated dynamically.
     *
     * @param optionName the map option name
     * @param optionKey the map option key
     * @param optionValue the map option value
     * @throws ConfigurationException if failed to set the option's value
     */
    public void injectOptionValue(String optionName, String optionKey, String optionValue)
            throws ConfigurationException;

    /**
     * Create a copy of this object.
     *
     * @return a {link IConfiguration} copy
     */
    public IConfiguration clone();

    /**
     * Replace the current {@link IBuildProvider} in the configuration.
     *
     * @param provider the new {@link IBuildProvider}
     */
    public void setBuildProvider(IBuildProvider provider);

    /**
     * Set the {@link ILeveledLogOutput}, replacing any existing value.
     *
     * @param logger
     */
    public void setLogOutput(ILeveledLogOutput logger);

    /**
     * Set the {@link IDeviceRecovery}, replacing any existing value.
     *
     * @param recovery
     */
    public void setDeviceRecovery(IDeviceRecovery recovery);

    /**
     * Set the {@link ITargetPreparer}, replacing any existing value.
     *
     * @param preparer
     */
    public void setTargetPreparer(ITargetPreparer preparer);

    /**
     * Convenience method to set a single {@link IRemoteTest} in this configuration, replacing any
     * existing values
     *
     * @param test
     */
    public void setTest(IRemoteTest test);

    /**
     * Set the list of {@link IRemoteTest}s in this configuration, replacing any
     * existing values
     *
     * @param tests
     */
    public void setTests(List<IRemoteTest> tests);

    /**
     * Set the list of {@link ITestInvocationListener}s, replacing any existing values
     *
     * @param listeners
     */
    public void setTestInvocationListeners(List<ITestInvocationListener> listeners);

    /**
     * Convenience method to set a single {@link ITestInvocationListener}
     *
     * @param listener
     */
    public void setTestInvocationListener(ITestInvocationListener listener);

    /**
     * Set the {@link ICommandOptions}, replacing any existing values
     *
     * @param cmdOptions
     */
    public void setCommandOptions(ICommandOptions cmdOptions);

    /**
     * Set the {@link IDeviceSelection}, replacing any existing values
     *
     * @param deviceSelection
     */
    public void setDeviceRequirements(IDeviceSelection deviceSelection);

    /**
     * Set the {@link TestDeviceOptions}, replacing any existing values
     */
    public void setDeviceOptions(TestDeviceOptions deviceOptions);

    /**
     * Generic method to set the config object with the given name, replacing any existing value.
     *
     * @param name the unique name of the config object type.
     * @param configObject the config object
     * @throws ConfigurationException if the configObject was not the correct type
     */
    public void setConfigurationObject(String name, Object configObject)
            throws ConfigurationException;

    /**
     * Generic method to set the config object list for the given name, replacing any existing
     * value.
     *
     * @param name the unique name of the config object type.
     * @param configList the config object list
     * @throws ConfigurationException if any objects in the list are not the correct type
     */
    public void setConfigurationObjectList(String name, List<?> configList)
            throws ConfigurationException;

    /**
     * Set the config {@link Option} fields with given set of command line arguments
     * <p/>
     * @see {@link ArgsOptionParser} for expected format
     *
     * @param listArgs the command line arguments
     */
    public void setOptionsFromCommandLineArgs(List<String> listArgs) throws ConfigurationException;

    /**
     * Outputs a command line usage help text for this configuration to given printStream.
     *
     * @param importantOnly if <code>true</code> only print help for the important options
     * @param out the {@link PrintStream} to use.
     * @throws ConfigurationException
     */
    public void printCommandUsage(boolean importantOnly, PrintStream out)
            throws ConfigurationException;

    /**
     * Validate option values.
     * <p/>
     * Currently this will just validate that all mandatory options have been set
     *
     * @throws ConfigurationException if config is not valid
     */
    public void validateOptions() throws ConfigurationException;
}
