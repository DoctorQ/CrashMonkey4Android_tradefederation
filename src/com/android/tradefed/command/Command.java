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
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceManager;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.DeviceSelectionOptions;
import com.android.tradefed.device.DeviceUnresponsiveException;
import com.android.tradefed.device.IDeviceManager;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.IDeviceManager.FreeDeviceState;
import com.android.tradefed.invoker.ITestInvocation;
import com.android.tradefed.invoker.TestInvocation;
import com.android.tradefed.log.LogRegistry;

/**
 * Command-line launcher for Trade Federation that runs a single configuration then exits.
 * <p/>
 * Loads the test configuration based on command line arguments, connects to available device,
 * and delegates off to {@link ITestInvocation} to perform the work of running of tests.
 */
public class Command {

    @SuppressWarnings("unused")
    private static final String LOG_TAG = "Command";
    /** the time in ms to wait for a device */
    static final long WAIT_DEVICE_TIME = 10 * 1000;

    @Option(name="help", description="get command line usage info")
    private boolean mHelpMode = false;

    public Command() {
    }

    /**
     * The main worker method that will parse the command line arguments, and invoke the test run.
     * <p/>
     * @param args the command line arguments. Expected format is:
     *   [configuration options] [--loop] configuration_name
     * where:
     *  <li> configuration_name: unique name of {@link IConfiguration} to use
     *  <li> configuration options: the {@link Option} names and associated values to provide to
     *  the {@link IConfiguration}
     */
    protected void run(String[] args) {
        initLogging();

        IDeviceManager manager = null;

        try {
            DeviceSelectionOptions deviceOptions = new DeviceSelectionOptions();
            IConfiguration config = getConfigFactory().createConfigurationFromArgs(args, this,
                    deviceOptions);

            if (mHelpMode) {
                printHelp(args);
                return;
            }

            manager = getDeviceManager();
            runInvocation(manager, deviceOptions, config);
        } catch (ConfigurationException e) {
            System.out.println(String.format("Failed to load configuration: %s", e.getMessage()));
            printHelp(args);
        } finally {
            exit(manager);
        }
    }

    /**
     * Print help text for given command line arguments.
     *
     * @param args the command line arguments
     */
    private void printHelp(String[] args) {
        getConfigFactory().printHelp(args, System.out, this.getClass(),
                DeviceSelectionOptions.class);
    }

    /**
     * Initialize logging.
     * <p/>
     * Exposed so tests can override.
     */
    void initLogging() {
        DdmPreferences.setLogLevel(LogLevel.VERBOSE.getStringValue());
        Log.setLogOutput(getLogRegistry());
    }

    /**
     * Allocate a device and runs the given configuration.
     *
     * @param manager
     * @param deviceOptions
     * @param config
     * @throws ConfigurationException
     */
    private void runInvocation(IDeviceManager manager, DeviceSelectionOptions deviceOptions,
            IConfiguration config)
            throws ConfigurationException {
        ITestDevice device = null;
        FreeDeviceState deviceState = FreeDeviceState.AVAILABLE;
        try {
            ITestInvocation instance = createRunInstance();
            device = manager.allocateDevice(WAIT_DEVICE_TIME, deviceOptions);
            if (device == null) {
                System.out.println("Could not find device to test");
                throw new DeviceNotAvailableException();
            }
            instance.invoke(device, config);
        } catch (DeviceUnresponsiveException e) {
            deviceState = FreeDeviceState.UNRESPONSIVE;
        } catch (DeviceNotAvailableException e) {
            deviceState = FreeDeviceState.UNAVAILABLE;
        } finally {
            if (manager != null && device != null) {
                manager.freeDevice(device, deviceState);
            }
        }
    }

    private void exit(IDeviceManager manager) {
        if (manager != null) {
            manager.terminate();
        }
        getLogRegistry().closeAndRemoveAllLogs();
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
     * Factory method for getting a reference to the {@link LogRegistry}
     *
     * @return the {@link LogRegistry} to use
     */
    LogRegistry getLogRegistry() {
        return LogRegistry.getLogRegistry();
    }

    /**
     * Factory method for retrieving the {@link IConfigurationFactory} to use.
     */
    IConfigurationFactory getConfigFactory() {
        return ConfigurationFactory.getInstance();
    }

    /**
     * Main entry point for TradeFederation command line launcher.
     *
     * @param args command line arguments. Expected format: [option] [config_name]
     */
    public static void main(String[] args) {
        Command cmd = new Command();
        cmd.run(args);
    }
}
