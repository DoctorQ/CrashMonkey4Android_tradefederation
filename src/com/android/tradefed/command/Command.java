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
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.ConfigurationFactory;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceManager;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.IDeviceManager;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.ITestInvocation;
import com.android.tradefed.invoker.TestInvocation;
import com.android.tradefed.log.ILeveledLogOutput;
import com.android.tradefed.util.RunUtil;

/**
 * Command-line launcher for Trade Federation.
 * <p/>
 * Loads the test configuration based on command line arguments, connects to available device,
 * and delegates off to {@link ITestInvocation} to perform the work of running of tests.
 */
public class Command {

    private static final String LOG_TAG = "Command";
    /** the minimum invocation time in ms when in loop mode */
    private static final long MIN_LOOP_TIME = 2 * 60 * 1000;
    /** the time in ms to wait for a device */
    private static final long WAIT_DEVICE_TIME = 10 * 1000;

    public Command() {
    }

    /**
     * The main worker method that will parse the command line arguments, and invoke the test run.
     * <p/>
     * TODO: support "--help"
     * @param args the command line arguments. Expected format is:
     *   [configuration options] [--loop] configuration_name
     * where:
     *  <li> configuration_name: unique name of {@link IConfiguration} to use
     *  <li> configuration options: the {@link Option} names and associated values to provide to
     *  the {@link IConfiguration}
     *  <li> --loop: keep running an invocation continuously. Each invocation will be spaced at
     *  least 2 minutes apart. ie if a prior invocation has nothing to do, the program will sleep
     *  until 2 minutes has elapsed before starting next invocation.
     */
    protected void run(String[] args) {
        // TODO: look at better way of parsing arguments specific to this class
        boolean loopMode = false;
        for (String arg : args) {
            if (arg.equals("--loop")) {
                loopMode = true;
            }
        }
        IDeviceManager manager = null;
        try {
            manager = getDeviceManager();
            if (loopMode) {
                while (true) {
                    long startTime = System.currentTimeMillis();
                    Log.i(LOG_TAG, "Starting new invocation");
                    runInvocation(manager, args);
                    long stopTime = System.currentTimeMillis();
                    long sleepTime = MIN_LOOP_TIME - (stopTime - startTime);
                    if (sleepTime > 0) {
                        Log.i(LOG_TAG, String.format("Sleeping for %d ms", sleepTime));
                        RunUtil.sleep(sleepTime);
                    }
                }
            } else {
                runInvocation(manager, args);
            }
        } catch (DeviceNotAvailableException e) {
            System.out.println("Could not find device to test");
        } catch (ConfigurationException e) {
            System.out.println(String.format("Failed to load configuration: %s", e.getMessage()));
        } catch (Throwable e) {
            System.out.println("Uncaught exception!");
            e.printStackTrace();
        }
        exit(manager);
    }

    protected void runInvocation(IDeviceManager manager, String[] args)
            throws ConfigurationException, DeviceNotAvailableException {
        ILeveledLogOutput logger = null;
        ITestDevice device = null;
        try {
            IConfiguration config = createConfiguration(args);
            logger = config.getLogOutput();
            Log.setLogOutput(logger);
            DdmPreferences.setLogLevel(logger.getLogLevel());
            ITestInvocation instance = createRunInstance();
            device = manager.allocateDevice(config.getDeviceRecovery(), WAIT_DEVICE_TIME);
            instance.invoke(device, config);
        } finally {
            if (manager != null && device != null) {
                manager.freeDevice(device);
            }
            if (logger != null) {
                logger.closeLog();
            }
        }
    }

    private void exit(IDeviceManager manager) {
        if (manager != null) {
            manager.terminate();
        }
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
     * Factory method for creating a {@link IConfiguration}.
     *
     * @param args the command line arguments
     * @return the {@link IConfiguration} populated with option values supplied in args
     * @throws {@link ConfigurationException} if {@link IConfiguration} could not be loaded.
     */
    protected IConfiguration createConfiguration(String[] args) throws ConfigurationException {
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
