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
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.IDeviceRecovery;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.ILeveledLogOutput;
import com.android.tradefed.log.LogRegistry;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.ITestSummaryListener;
import com.android.tradefed.result.JUnitToInvocationResultForwarder;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.TestSummary;
import com.android.tradefed.targetsetup.BuildError;
import com.android.tradefed.targetsetup.BuildInfo;
import com.android.tradefed.targetsetup.IBuildInfo;
import com.android.tradefed.targetsetup.IBuildProvider;
import com.android.tradefed.targetsetup.ITargetPreparer;
import com.android.tradefed.targetsetup.TargetSetupError;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;

import java.util.ArrayList;
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
            Log.e(LOG_TAG, e);
        } catch (ConfigurationException e) {
            Log.e(LOG_TAG, e);
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
        long startTime = System.currentTimeMillis();
        long elapsedTime = -1;
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
            Log.w(LOG_TAG, String.format("Build %d failed on device %s", info.getBuildId(),
                    device.getSerialNumber()));
            reportFailure(e, listeners, buildProvider, info);
        } catch (TargetSetupError e) {
            Log.e(LOG_TAG, e);
            reportFailure(e, listeners, buildProvider, info);
        } catch (DeviceNotAvailableException e) {
            Log.e(LOG_TAG, e);
            reportFailure(e, listeners, buildProvider, info);
            throw e;
        } catch (RuntimeException e) {
            Log.e(LOG_TAG, "Unexpected runtime exception!");
            Log.e(LOG_TAG, e);
            reportFailure(e, listeners, buildProvider, info);
            throw e;
        } finally {
            elapsedTime = System.currentTimeMillis() - startTime;
            List<TestSummary> summaries = new ArrayList<TestSummary>(listeners.size());
            for (ITestInvocationListener listener : listeners) {
                if (device != null) {
                    listener.testLog(DEVICE_LOG_NAME, LogDataType.TEXT, device.getLogcat());
                }
                listener.testLog(TRADEFED_LOG_NAME, LogDataType.TEXT, logger.getLog());

                /*
                 * For InvocationListeners (as opposed to SummaryListeners), we call
                 * invocationEnded() followed by getSummary().  If getSummary returns a non-null
                 * value, we gather it to pass to the SummaryListeners below.
                 */
                if (!(listener instanceof ITestSummaryListener)) {
                    listener.invocationEnded(elapsedTime);
                    TestSummary summary = listener.getSummary();
                    if (summary != null) {
                        summary.setSource(listener.getClass().getName());
                        summaries.add(summary);
                    }
                }
            }

            /*
             * For SummaryListeners (as opposed to InvocationListeners), we now call putSummary()
             * followed by invocationEnded().  This means that the SummaryListeners will have
             * access to the summaries (if any) when invocationEnded is called.
             */
            for (ITestInvocationListener listener : listeners) {
                if (listener instanceof ITestSummaryListener) {
                    ((ITestSummaryListener) listener).putSummary(summaries);
                    listener.invocationEnded(elapsedTime);
                }
            }
        }
    }

    private void reportFailure(Throwable exception, List<ITestInvocationListener> listeners,
            IBuildProvider buildProvider, IBuildInfo info) {
        for (ITestInvocationListener listener : listeners) {
            listener.invocationFailed(exception);
        }
        if (!(exception instanceof BuildError)) {
            buildProvider.buildNotTested(info);
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
            if (test instanceof IRemoteTest) {
                // run as a remote test, so results are forwarded directly to TestInvocationListener
                ((IRemoteTest)test).run(listeners);
            } else {
                for (ITestInvocationListener listener : listeners) {
                    listener.testRunStarted(test.getClass().getName(), test.countTestCases());
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
