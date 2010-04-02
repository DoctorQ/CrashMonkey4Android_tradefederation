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

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.DdmPreferences;
import com.android.ddmlib.Log;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.ConfigurationFactory;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.device.DeviceManager;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.IDeviceManager;
import com.android.tradefed.device.IDeviceRecovery;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.ITestInvocation;
import com.android.tradefed.invoker.TestInvocation;
import com.android.tradefed.log.ILeveledLogOutput;

/**
 * Command-line launcher for Trade Federation.
 * <p/>
 * Loads the test configuration based on command line arguments, connects to available device,
 * and delegates off to {@link ITestInvocation} to perform the work of running of tests.
 */
public class Command {

    /**
     *  Package private constructor so this can be created by tests.
     */
    Command() {
    }

    /**
     * The main worker method that will parse the command line arguments, and invoke the test run.
     * <p/>
     * TODO: support "--help"
     * @param args the command line arguments
     */
    void run(String[] args) {
        ILeveledLogOutput logger = null;
        try {
            IConfiguration config = createConfiguration(args);
            logger = config.getLogOutput();
            Log.setLogOutput(logger);
            DdmPreferences.setLogLevel(logger.getLogLevel());
            ITestInvocation instance = createRunInstance();
            IDeviceManager manager = getDeviceManager(config.getDeviceRecovery());
            ITestDevice device = manager.allocateDevice();
            instance.invoke(device, config);
        } catch (DeviceNotAvailableException e) {
            System.out.println("Could not find device to test");
        } catch (ConfigurationException e) {
            System.out.println(String.format("Failed to load configuration: %s", e.getMessage()));
        } catch (Exception e) {
            System.out.println("Uncaught exception!");
            e.printStackTrace();
        }
        finally {
            if (logger != null) {
                logger.closeLog();
            }
        }

        exit();
    }

    private void exit() {
        AndroidDebugBridge.terminate();
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
    IDeviceManager getDeviceManager(IDeviceRecovery recovery) {
        DeviceManager.init(recovery);
        return DeviceManager.getInstance();
    }

    /**
     * Factory method for creating a {@link IConfiguration}.
     *
     * @param args the command line arguments
     * @return the {@link IConfiguration} populated with option values supplied in args
     * @throws {@link ConfigurationException} if {@link IConfiguration} could not be loaded.
     */
    IConfiguration createConfiguration(String[] args) throws ConfigurationException {
        return ConfigurationFactory.createConfigurationFromArgs(args);
    }

    /**
     * Output the command line usage of this program to stdout.
     *
     * @param config the {@link IConfiguration} in use.
     */
    void printUsage(IConfiguration config) throws ConfigurationException {
        // TODO: Also print out list of configs from ConfigurationFactory?
        config.printCommandUsage(System.out);
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
