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
import com.android.tradefed.build.BuildInfo;
import com.android.tradefed.build.BuildRetrievalError;
import com.android.tradefed.build.ExistingBuildProvider;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.IBuildProvider;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.ILeveledLogOutput;
import com.android.tradefed.log.LogRegistry;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InvocationSummaryHelper;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.ResultForwarder;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.testtype.IResumableTest;
import com.android.tradefed.testtype.IShardableTest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import junit.framework.Test;

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
    static final String TRADEFED_LOG_NAME = "host_log";
    static final String DEVICE_LOG_NAME = "device_logcat";

    private ITestDevice mDevice = null;
    private String mStatus = "(not invoked)";

    @Override
    public String toString() {
        String devString = "(none)";
        if (mDevice != null) {
            devString = mDevice.getSerialNumber();
        }
        return String.format("Device %s: %s", devString, mStatus);
    }

    /**
     * A {@link ResultForwarder} for forwarding resumed invocations.
     * <p/>
     * It filters the invocationStarted event for the resumed invocation, and sums the invocation
     * elapsed time
     */
    private static class ResumeResultForwarder extends ResultForwarder {

        long mCurrentElapsedTime;

        /**
         * @param listeners
         */
        public ResumeResultForwarder(List<ITestInvocationListener> listeners,
                long currentElapsedTime) {
            super(listeners);
            mCurrentElapsedTime = currentElapsedTime;
        }

        @Override
        public void invocationStarted(IBuildInfo buildInfo) {
            // ignore
        }

        @Override
        public void invocationEnded(long newElapsedTime) {
            super.invocationEnded(mCurrentElapsedTime + newElapsedTime);
        }
    }

    /**
     * {@inheritDoc}
     */
    public void invoke(ITestDevice device, IConfiguration config, IRescheduler rescheduler)
            throws DeviceNotAvailableException {
        try {
            mDevice = device;
            mStatus = "fetching build";
            IBuildInfo info = config.getBuildProvider().getBuild();
            if (info != null) {
                injectBuild(info, config.getTests());
                if (shardConfig(config, info, rescheduler)) {
                    Log.i(LOG_TAG, String.format("Invocation for %s has been sharded, rescheduling",
                            device.getSerialNumber()));
                } else {
                    device.setRecovery(config.getDeviceRecovery());
                    performInvocation(config, device, info, rescheduler);
                }
            } else {
                mStatus = "(no build to test)";
                Log.i(LOG_TAG, "No build to test");
            }
        } catch (BuildRetrievalError e) {
            Log.e(LOG_TAG, e);
        }
    }

   /**
     * Pass the build to any {@link IBuildReceiver} tests
     * @param buildInfo
     * @param tests
     */
    private void injectBuild(IBuildInfo buildInfo, List<IRemoteTest> tests) {
        for (IRemoteTest test : tests) {
            if (test instanceof IBuildReceiver) {
                ((IBuildReceiver)test).setBuild(buildInfo);
            }
        }
    }

/**
    * Attempt to shard the configuration into sub-configurations, to be re-scheduled to run on
    * multiple resources in parallel.
    * <p/>
    * A successful shard action renders the current config empty, and invocation should not proceed.
    *
    * @see {@link IShardableTest}, {@link IRescheduler}
    *
    * @param config the current {@link IConfiguration}.
    * @param info the {@link IBuildInfo} to test
    * @param rescheduler the {@link IRescheduler}
    * @return true if test was sharded. Otherwise return <code>false</code>
    */
    private boolean shardConfig(IConfiguration config, IBuildInfo info, IRescheduler rescheduler) {
        mStatus = "sharding";
        List<IRemoteTest> shardableTests = new ArrayList<IRemoteTest>();
        boolean isSharded = false;
        for (IRemoteTest test : config.getTests()) {
            isSharded |= shardTest(shardableTests, test);
        }
        if (isSharded) {
            ShardMasterResultForwarder resultCollector = new ShardMasterResultForwarder(
                    config.getTestInvocationListeners(), shardableTests.size());
            ShardListener origConfigListener = new ShardListener(resultCollector);
            config.setTestInvocationListener(origConfigListener);
            Log.logAndDisplay(LogLevel.INFO, LOG_TAG, "Sending build " + info);
            // report invocation started using original buildinfo
            resultCollector.invocationStarted(info);
            for (IRemoteTest testShard : shardableTests) {
                Log.i(LOG_TAG, String.format("Rescheduling sharded config..."));
                IConfiguration shardConfig = config.clone();
                shardConfig.setTest(testShard);
                shardConfig.setBuildProvider(new ExistingBuildProvider(info.clone(),
                        config.getBuildProvider()));
                shardConfig.setTestInvocationListener(new ShardListener(resultCollector));
                shardConfig.setLogOutput(config.getLogOutput().clone());
                // use the same {@link ITargetPreparer}, {@link IDeviceRecovery} etc as original
                // config
                rescheduler.scheduleConfig(shardConfig);
            }
            return true;
        }
        return false;
    }

    /**
     * Attempt to shard given {@link IRemoteTest}.
     *
     * @param shardableTests the list of {@link IRemoteTest}s to add to
     * @param test the {@link Test} to shard
     * @return <code>true</code> if test was sharded
     */
    private boolean shardTest(List<IRemoteTest> shardableTests, IRemoteTest test) {
        boolean isSharded = false;
        if (test instanceof IShardableTest) {
            IShardableTest shardableTest = (IShardableTest)test;
            Collection<IRemoteTest> shards = shardableTest.split();
            if (shards != null) {
                shardableTests.addAll(shards);
                isSharded = true;
            }
        }
        if (!isSharded) {
            shardableTests.add(test);
        }
        return isSharded;
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
        mStatus = String.format("running %s on build %d", info.getTestTarget(), info.getBuildId());
    }

    /**
     * Performs the invocation
     *
     * @param config the {@link IConfiguration}
     * @param device the {@link ITestDevice} to use. May be <code>null</code>
     * @param info the {@link IBuildInfo}
     *
     * @throws DeviceNotAvailableException
     * @throws ConfigurationException
     */
    private void performInvocation(IConfiguration config, ITestDevice device, IBuildInfo info,
            IRescheduler rescheduler) throws DeviceNotAvailableException {

        boolean resumed = false;
        long startTime = System.currentTimeMillis();
        long elapsedTime = -1;

        getLogRegistry().registerLogger(config.getLogOutput());
        logStartInvocation(info, device);
        for (ITestInvocationListener listener : config.getTestInvocationListeners()) {
            listener.invocationStarted(info);
        }
        try {
            // TODO: find a cleaner way to add this info
            if (device != null) {
                info.addBuildAttribute("device_serial", device.getSerialNumber());
            }
            for (ITargetPreparer preparer : config.getTargetPreparers()) {
                preparer.setUp(device, info);
            }
            runTests(device, info, config, rescheduler);
        } catch (BuildError e) {
            Log.w(LOG_TAG, String.format("Build %d failed on device %s", info.getBuildId(),
                    device.getSerialNumber()));
            reportFailure(e, config.getTestInvocationListeners(), config.getBuildProvider(), info);
        } catch (TargetSetupError e) {
            Log.e(LOG_TAG, e);
            reportFailure(e, config.getTestInvocationListeners(), config.getBuildProvider(), info);
        } catch (DeviceNotAvailableException e) {
            Log.e(LOG_TAG, e);
            resumed = resume(config, info, rescheduler, System.currentTimeMillis() - startTime);
            if (!resumed) {
                reportFailure(e, config.getTestInvocationListeners(), config.getBuildProvider(),
                        info);
            } else {
                Log.i(LOG_TAG, "Rescheduled failed invocation for resume");
            }
            throw e;
        } catch (RuntimeException e) {
            Log.e(LOG_TAG, "Unexpected runtime exception!");
            Log.e(LOG_TAG, e);
            reportFailure(e, config.getTestInvocationListeners(), config.getBuildProvider(), info);
            throw e;
        } finally {
            mStatus = "done running tests";
            reportLogs(device, config.getTestInvocationListeners(), config.getLogOutput());
            elapsedTime = System.currentTimeMillis() - startTime;
            try {
                if (!resumed) {
                    InvocationSummaryHelper.reportInvocationEnded(
                            config.getTestInvocationListeners(), elapsedTime);
                }
            } finally {
                config.getBuildProvider().cleanUp(info);
            }
        }
    }

    /**
     * Attempt to reschedule the failed invocation to resume where it left off.
     *
     * @see {@link IResumableTest}
     *
     * @param config
     * @return <code>true</code> if invocation was resumed successfully
     */
    private boolean resume(IConfiguration config, IBuildInfo info, IRescheduler rescheduler,
            long elapsedTime) {
        for (IRemoteTest test : config.getTests()) {
            if (test instanceof IResumableTest) {
                IResumableTest resumeTest = (IResumableTest)test;
                if (resumeTest.isResumable()) {
                    // resume this config if any test is resumable
                    IConfiguration resumeConfig = config.clone();
                    // reuse the same build for the resumed invocation
                    resumeConfig.setBuildProvider(new ExistingBuildProvider(info.clone(),
                            config.getBuildProvider()));
                    // create a result forwarder, to prevent sending two invocationStarted events
                    resumeConfig.setTestInvocationListener(new ResumeResultForwarder(
                            config.getTestInvocationListeners(), elapsedTime));
                    resumeConfig.setLogOutput(config.getLogOutput().clone());
                    return rescheduler.scheduleConfig(resumeConfig);
                }
            }
        }
        return false;
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

    private void reportLogs(ITestDevice device, List<ITestInvocationListener> listeners,
            ILeveledLogOutput logger) {
        for (ITestInvocationListener listener : listeners) {
            if (device != null) {
                listener.testLog(DEVICE_LOG_NAME, LogDataType.TEXT, device.getLogcat());
            }
            listener.testLog(TRADEFED_LOG_NAME, LogDataType.TEXT, logger.getLog());
        }
        // once tradefed log is reported, all further log calls for this invocation can get lost
        // unregister logger so future log calls get directed to the tradefed global log
        getLogRegistry().unregisterLogger();
        logger.closeLog();
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
     * @param device the {@link ITestDevice} to run tests on
     * @param buildInfo the {@link BuildInfo} describing the build target
     * @param tests the {@link Test}s to run
     * @param listeners the {@link ITestInvocationListener}s that listens for test results in real
     *            time
     * @throws DeviceNotAvailableException
     */
    private void runTests(ITestDevice device, IBuildInfo buildInfo, IConfiguration config,
            IRescheduler rescheduler)
            throws DeviceNotAvailableException {
        List<ITestInvocationListener> listeners = config.getTestInvocationListeners();
        for (IRemoteTest test : config.getTests()) {
            if (test instanceof IDeviceTest) {
                ((IDeviceTest)test).setDevice(device);
            }
            test.run(new ResultForwarder(listeners));
        }
    }
}
