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
import com.android.tradefed.device.DeviceUnresponsiveException;
import com.android.tradefed.device.IDeviceManager;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.IDeviceManager.FreeDeviceState;
import com.android.tradefed.invoker.ITestInvocation;
import com.android.tradefed.invoker.TestInvocation;
import com.android.tradefed.log.LogRegistry;
import com.android.tradefed.util.IRunUtil;
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
    static final long WAIT_DEVICE_TIME = 10 * 1000;

    @Option(name="loop", description="keep running continuously")
    private boolean mLoopMode = false;

    @Option(name="help", description="get command line usage info")
    private boolean mHelpMode = false;

    @Option(name="serial", shortName='s', description="serial number of device to run tests on")
    private String mSerial = null;

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
        DdmPreferences.setLogLevel(LogLevel.VERBOSE.getStringValue());
        Log.setLogOutput(getLogRegistry());

        IDeviceManager manager = null;

        try {
            IConfiguration config = createConfigurationAndParseArgs(args);

            if (mHelpMode) {
                getConfigFactory().printHelp(args, System.out);
                return;
            }
            if (mSerial != null) {
                throw new ConfigurationException("serial not supported yet");
            }

            manager = getDeviceManager();
            if (mLoopMode) {
                while (true) {
                    long startTime = System.currentTimeMillis();
                    Log.i(LOG_TAG, "Starting new invocation");
                    runInvocation(manager, config);
                    long stopTime = System.currentTimeMillis();
                    long sleepTime = MIN_LOOP_TIME - (stopTime - startTime);
                    if (sleepTime > 0) {
                        Log.i(LOG_TAG, String.format("Sleeping for %d ms", sleepTime));
                        getRunUtil().sleep(sleepTime);
                    }
                    // recreate config otherwise state can accumlate
                    config = createConfigurationAndParseArgs(args);
                }
            } else {
                runInvocation(manager, config);
            }
        } catch (ConfigurationException e) {
            System.out.println(String.format("Failed to load configuration: %s", e.getMessage()));
            getConfigFactory().printHelp(args, System.out);
        } catch (Throwable e) {
            System.out.println("Uncaught exception!");
            e.printStackTrace();
        }
        exit(manager);
    }

    /**
     * Get the {@link RunUtil} instance to use.
     * <p/>
     * Exposed for unit testing.
     */
    protected IRunUtil getRunUtil() {
        return RunUtil.getInstance();
    }

    protected void runInvocation(IDeviceManager manager, IConfiguration config)
            throws ConfigurationException {
        ITestDevice device = null;
        FreeDeviceState deviceState = FreeDeviceState.AVAILABLE;
        try {
            ITestInvocation instance = createRunInstance();
            device = manager.allocateDevice(config.getDeviceRecovery(), WAIT_DEVICE_TIME);
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

    protected void exit(IDeviceManager manager) {
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
    protected ITestInvocation createRunInstance() {
        return new TestInvocation();
    }

    /**
     * Factory method for getting a reference to the {@link IDeviceManager}
     *
     * @return the {@link IDeviceManager} to use
     */
    protected IDeviceManager getDeviceManager() {
        return DeviceManager.getInstance();
    }

    /**
     * Factory method for getting a reference to the {@link LogRegistry}
     *
     * @return the {@link LogRegistry} to use
     */
    protected LogRegistry getLogRegistry() {
        return LogRegistry.getLogRegistry();
    }

    /**
     * Factory method for creating a {@link IConfiguration}.
     *
     * @param args the command line arguments
     * @return the {@link IConfiguration} populated with option values supplied in args
     * @throws {@link ConfigurationException} if {@link IConfiguration} could not be loaded.
     */
    protected IConfiguration createConfigurationAndParseArgs(String[] args)
            throws ConfigurationException {
        return getConfigFactory().createConfigurationFromArgs(args, this);
    }

    protected IConfigurationFactory getConfigFactory() {
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
