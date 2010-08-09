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
package com.android.tradefed.invoker;

import com.android.ddmlib.Log;
import com.android.ddmlib.Log.LogLevel;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IConfigurationReceiver;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.IDeviceRecovery;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.ILeveledLogOutput;
import com.android.tradefed.log.LogRegistry;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InvocationStatus;
import com.android.tradefed.result.JUnitToInvocationResultForwarder;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.targetsetup.BuildError;
import com.android.tradefed.targetsetup.BuildInfo;
import com.android.tradefed.targetsetup.IBuildInfo;
import com.android.tradefed.targetsetup.IBuildProvider;
import com.android.tradefed.targetsetup.ITargetPreparer;
import com.android.tradefed.targetsetup.TargetSetupError;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import junit.framework.Test;
import junit.framework.TestResult;

/**
 * Default implementation of {@link ITestInvocation}.
 * <p/>
 * Loads major objects based on {@link IConfiguration}
 *   - retrieves build
 *   - prepares target
 *   - runs tests
 *   - reports results
 */
public class TestInvocation implements ITestInvocation {

    private static final String LOG_TAG = "TestInvocation";
    static final String TRADEFED_LOG_NAME = "tradefed_log";
    static final String DEVICE_LOG_NAME = "device_logcat";

    /**
     * Constructs a {@link TestInvocation}
     */
    public TestInvocation() {
    }

    /**
     * {@inheritDoc}
     */
    public void invoke(ITestDevice device, IConfiguration config)
            throws DeviceNotAvailableException {
        List<ITestInvocationListener> listeners = null;
        ILeveledLogOutput logger = null;
        LogRegistry logRegistry = getLogRegistry();
        try {
            logger = config.getLogOutput();
            logRegistry.registerLogger(logger);

            IBuildProvider buildProvider = config.getBuildProvider();
            ITargetPreparer preparer = config.getTargetPreparer();
            IDeviceRecovery recovery = config.getDeviceRecovery();
            device.setRecovery(recovery);
            List<Test> tests = config.getTests();
            IBuildInfo info = buildProvider.getBuild();
            if (info != null) {
                listeners = config.getTestInvocationListeners();
                performInvocation(config, buildProvider, device, listeners, preparer, tests, info,
                        logger);
            } else {
                Log.i(LOG_TAG, "No build to test");
            }
        } catch (TargetSetupError e) {
            handleError(listeners, e);
        } catch (IllegalArgumentException e) {
            handleError(listeners, e);
        } catch (ConfigurationException e) {
            handleError(listeners, e);
        } catch (DeviceNotAvailableException e) {
            // already logged, just catch and rethrow to prevent uncaught exception logic
            throw e;
        } catch (Throwable e) {
            Log.e(LOG_TAG, "Uncaught exception!");
            handleError(listeners, e);
            // TODO: consider re-throwing ?
        } finally {
            if (logger != null) {
              logger.closeLog();
            }
            logRegistry.unregisterLogger();
        }
    }

    /**
     * Display a log message informing the user of a invocation being started.
     *
     * @param info the {@link IBuildInfo}
     * @param device the {@link ITestDevice}
     */
    private void logStartInvocation(IBuildInfo info, ITestDevice device) {
        StringBuilder msg = new StringBuilder("Starting invocation for target ");
        msg.append(info.getTestTarget());
        msg.append(" on build ");
        msg.append(info.getBuildId());
        for (String buildAttr : info.getBuildAttributes().values()) {
            msg.append(" ");
            msg.append(buildAttr);
        }
        msg.append(" on device ");
        msg.append(device.getSerialNumber());
        Log.logAndDisplay(LogLevel.INFO, LOG_TAG, msg.toString());
    }

    /**
     * Performs the invocation
     *
     * @param config the {@link IConfiguration}
     * @param buildProvider the {@link IBuildProvider}
     * @param device the {@link ITestDevice} to use. May be <code>null</code>
     * @param listener the {@link ITestInvocationListener} to report results to
     * @param preparer the {@link ITargetPreparer}
     * @param test the {@link Test} to run
     * @param info the {@link IBuildInfo}
     * @param logger the {@link ILeveledLogOutput}
     * @throws DeviceNotAvailableException
     */
    private void performInvocation(IConfiguration config, IBuildProvider buildProvider,
            ITestDevice device, List<ITestInvocationListener> listeners, ITargetPreparer preparer,
            List<Test> tests, IBuildInfo info, ILeveledLogOutput logger)
            throws DeviceNotAvailableException {
        Throwable error = null;
        InvocationStatus status = InvocationStatus.SUCCESS;
        long startTime = System.currentTimeMillis();
        logStartInvocation(info, device);
        for (ITestInvocationListener listener : listeners) {
            listener.invocationStarted(info);
        }
        try {
            // TODO: find a cleaner way to add this info
            if (device != null) {
                info.addBuildAttribute("device_serial", device.getSerialNumber());
            }
            preparer.setUp(device, info);
            runTests(config, device, info, tests, listeners);
        } catch (BuildError e) {
            error = e;
            status = InvocationStatus.BUILD_ERROR;
            Log.w(LOG_TAG, String.format("Build %d failed on device %s", info.getBuildId(),
                    device.getSerialNumber()));
        } catch (TargetSetupError e) {
            error = e;
            status = InvocationStatus.FAILED;
            Log.e(LOG_TAG, e);
        } catch (DeviceNotAvailableException e) {
            error = e;
            status = InvocationStatus.FAILED;
            Log.e(LOG_TAG, e);
            throw e;
        } catch (Throwable e) {
            error = e;
            status = InvocationStatus.FAILED;
            Log.e(LOG_TAG, "Unexpected exception!");
            Log.e(LOG_TAG, e);
            // TODO: consider re-throwing
            // throw e;
        } finally {
            for (ITestInvocationListener listener : listeners) {
                if (device != null) {
                    listener.testLog(DEVICE_LOG_NAME, LogDataType.TEXT, device.getLogcat());
                }
                listener.testLog(TRADEFED_LOG_NAME, LogDataType.TEXT, logger.getLog());
                switch (status) {
                    case SUCCESS:
                        listener.invocationEnded(System.currentTimeMillis() - startTime);
                        break;
                    case BUILD_ERROR:
                        listener.invocationBuildError(System.currentTimeMillis() - startTime,
                                error.getMessage());
                        break;
                    case FAILED:
                        listener.invocationFailed(System.currentTimeMillis() - startTime,
                                error.getMessage(), error);
                        buildProvider.buildNotTested(info);
                        break;
                }
            }
        }

    }

    /**
     * Gets the {@link LogRegistry} to use.
     * <p/>
     * Exposed for unit testing.
     */
    LogRegistry getLogRegistry() {
        return LogRegistry.getLogRegistry();
    }

    /**
     * Handle an exception that occurred during test run
     * @param listener
     */
    private void handleError(List<ITestInvocationListener> listeners, Throwable e) {
        Log.e(LOG_TAG, e);
        if (listeners != null) {
            for (ITestInvocationListener listener : listeners) {
                // TODO: pass in elapsedTime
                listener.invocationFailed(0, e.getMessage(), e);
            }
        }
    }

    /**
     * Runs the test.
     *
     * @param config the {@link IConfiguration}
     * @param device the {@link ITestDevice} to run tests on
     * @param buildInfo the {@link BuildInfo} describing the build target
     * @param tests the {@link Test}s to run
     * @param listeners the {@link ITestInvocationListener}s that listens for test results in real
     *            time
     * @throws DeviceNotAvailableException
     */
    private void runTests(IConfiguration config, ITestDevice device, IBuildInfo buildInfo,
            List<Test> tests, List<ITestInvocationListener> listeners)
            throws DeviceNotAvailableException {
        for (Test test : tests) {
            if (test instanceof IDeviceTest) {
                ((IDeviceTest)test).setDevice(device);
            }
            if (test instanceof IConfigurationReceiver) {
                ((IConfigurationReceiver)test).setConfiguration(config);
            }
            if (test instanceof IRemoteTest) {
                // run as a remote test, so results are forwarded directly to TestInvocationListener
                ((IRemoteTest)test).run(listeners);
            } else {
                for (ITestInvocationListener listener : listeners) {
                    listener.testRunStarted(test.countTestCases());
                }
                long startTime = System.currentTimeMillis();
                // forward the JUnit results to the invocation listener
                JUnitToInvocationResultForwarder resultForwarder =
                    new JUnitToInvocationResultForwarder(listeners);
                TestResult result = new TestResult();
                result.addListener(resultForwarder);
                test.run(result);
                Map<String, String> emptyMap = Collections.emptyMap();
                for (ITestInvocationListener listener : listeners) {
                    listener.testRunEnded(System.currentTimeMillis() - startTime, emptyMap);
                }
            }
        }
    }
}
