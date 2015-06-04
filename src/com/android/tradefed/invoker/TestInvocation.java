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

import com.android.ddmlib.Log.LogLevel;
import com.android.tradefed.build.BuildInfo;
import com.android.tradefed.build.BuildRetrievalError;
import com.android.tradefed.build.ExistingBuildProvider;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.IDeviceBuildProvider;
import com.android.tradefed.build.IDeviceConfigBuildProvider;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.DeviceUnresponsiveException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.TestDeviceState;
import com.android.tradefed.log.ILeveledLogOutput;
import com.android.tradefed.log.ILogRegistry;
import com.android.tradefed.log.LogRegistry;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.InvocationSummaryHelper;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.ResultForwarder;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.targetprep.ITargetCleaner;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.testtype.IResumableTest;
import com.android.tradefed.testtype.IRetriableTest;
import com.android.tradefed.testtype.IShardableTest;

import junit.framework.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Default implementation of {@link ITestInvocation}.
 * <p/>
 * Loads major objects based on {@link IConfiguration} - retrieves build -
 * prepares target - runs tests - reports results
 */
public class TestInvocation implements ITestInvocation {
	
	static final String TRADEFED_LOG_NAME = "host_log";
	public static final String DEVICE_LOG_NAME = "device_logcat";
	static final String BUILD_ERROR_BUGREPORT_NAME = "build_error_bugreport";
	static final String DEVICE_UNRESPONSIVE_BUGREPORT_NAME = "device_unresponsive_bugreport";

	private String mStatus = "(not invoked)";
	private static boolean isRepeat = false;

	/**
	 * A {@link ResultForwarder} for forwarding resumed invocations.
	 * <p/>
	 * It filters the invocationStarted event for the resumed invocation, and
	 * sums the invocation elapsed time
	 */
	private static class ResumeResultForwarder extends ResultForwarder {

		long mCurrentElapsedTime;

		/**
		 * @param listeners
		 */
		public ResumeResultForwarder(List<ITestInvocationListener> listeners, long currentElapsedTime) {
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
	@Override
	public void invoke(ITestDevice device, IConfiguration config, IRescheduler rescheduler) throws DeviceNotAvailableException, Throwable {
		try {
			mStatus = "fetching build";
			config.getLogOutput().init();
			getLogRegistry().registerLogger(config.getLogOutput());
			IBuildInfo info = null;
			if (config.getBuildProvider() instanceof IDeviceBuildProvider) {
				info = ((IDeviceBuildProvider) config.getBuildProvider()).getBuild(device);
			} else if (config.getBuildProvider() instanceof IDeviceConfigBuildProvider) {
				// 调用config获得cts.xml文件中的<build_provider>标签中对应的类,然后通过调用getBuild得到IBuildInfo对象
				info = ((IDeviceConfigBuildProvider) config.getBuildProvider()).getBuild(device, config);
			} else {
				info = config.getBuildProvider().getBuild();
			}
			if (info != null) {
				// System.out.println(String.format("setup: %s tearDown: %s",
				// config.getCommandOptions().isNeedPrepare(),
				// config.getCommandOptions().isNeedTearDown()));
				CLog.logAndDisplay(LogLevel.INFO,
						String.format("setup: %s tearDown: %s", config.getCommandOptions().isNeedPrepare(), config.getCommandOptions().isNeedTearDown()));
				// 获取<test>配置项里的测试选项,并注入到info中
				injectBuild(info, config.getTests());
				if (shardConfig(config, info, rescheduler)) {
					CLog.i("Invocation for %s has been sharded, rescheduling", device.getSerialNumber());
				} else {
					device.setRecovery(config.getDeviceRecovery());
					// 准备刷机,启动case
					performInvocation(config, device, info, rescheduler);
					// exit here, depend on performInvocation to deregister
					// logger
					
					
					return;
				}
			} else {
				mStatus = "(no build to test)";
				CLog.d("No build to test");
				rescheduleTest(config, rescheduler);
			}
		} catch (BuildRetrievalError e) {
			CLog.e(e);
			/*
			 * because this is BuildRetrievalError, so do not generate test
			 * result // report an empty invocation, so this error is sent to
			 * listeners startInvocation(config, device, e.getBuildInfo()); //
			 * don't want to use #reportFailure, since that will call
			 * buildNotTested for (ITestInvocationListener listener :
			 * config.getTestInvocationListeners()) {
			 * listener.invocationFailed(e); } reportLogs(device,
			 * config.getTestInvocationListeners(), config.getLogOutput());
			 * InvocationSummaryHelper.reportInvocationEnded(
			 * config.getTestInvocationListeners(), 0); return;
			 */
		} catch (IOException e) {
			CLog.e(e);
		}
		// save current log contents to global log
		getLogRegistry().dumpToGlobalLog(config.getLogOutput());
		getLogRegistry().unregisterLogger();
		config.getLogOutput().closeLog();
	}

	/**
	 * Pass the build to any {@link IBuildReceiver} tests
	 * 
	 * @param buildInfo
	 * @param tests
	 */
	private void injectBuild(IBuildInfo buildInfo, List<IRemoteTest> tests) {
		for (IRemoteTest test : tests) {
			if (test instanceof IBuildReceiver) {
				((IBuildReceiver) test).setBuild(buildInfo);
			}
		}
	}

	/**
	 * Attempt to shard the configuration into sub-configurations, to be
	 * re-scheduled to run on multiple resources in parallel.
	 * <p/>
	 * A successful shard action renders the current config empty, and
	 * invocation should not proceed.
	 * 
	 * @see IShardableTest
	 * @see IRescheduler
	 * 
	 * @param config
	 *            the current {@link IConfiguration}.
	 * @param info
	 *            the {@link IBuildInfo} to test
	 * @param rescheduler
	 *            the {@link IRescheduler}
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
			ShardMasterResultForwarder resultCollector = new ShardMasterResultForwarder(config.getTestInvocationListeners(), shardableTests.size());
			ShardListener origConfigListener = new ShardListener(resultCollector);
			config.setTestInvocationListener(origConfigListener);
			// report invocation started using original buildinfo
			resultCollector.invocationStarted(info);
			for (IRemoteTest testShard : shardableTests) {
				CLog.i("Rescheduling sharded config...");
				IConfiguration shardConfig = config.clone();
				shardConfig.setTest(testShard);
				shardConfig.setBuildProvider(new ExistingBuildProvider(info.clone(), config.getBuildProvider()));
				shardConfig.setTestInvocationListener(new ShardListener(resultCollector));
				shardConfig.setLogOutput(config.getLogOutput().clone());
				shardConfig.setCommandOptions(config.getCommandOptions().clone());
				// use the same {@link ITargetPreparer}, {@link IDeviceRecovery}
				// etc as original
				// config
				rescheduler.scheduleConfig(shardConfig);
			}
			// clean up original build
			config.getBuildProvider().cleanUp(info);
			return true;
		}
		return false;
	}

	/**
	 * Attempt to shard given {@link IRemoteTest}.
	 * 
	 * @param shardableTests
	 *            the list of {@link IRemoteTest}s to add to
	 * @param test
	 *            the {@link Test} to shard
	 * @return <code>true</code> if test was sharded
	 */
	private boolean shardTest(List<IRemoteTest> shardableTests, IRemoteTest test) {
		boolean isSharded = false;
		if (test instanceof IShardableTest) {
			IShardableTest shardableTest = (IShardableTest) test;
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
	 * @param info
	 *            the {@link IBuildInfo}
	 * @param device
	 *            the {@link ITestDevice}
	 */
	private void logStartInvocation(IBuildInfo info, ITestDevice device) {
		StringBuilder msg = new StringBuilder("Starting invocation for '");
		msg.append(info.getTestTag());
		msg.append("'");
		if (!info.getBuildId().equals(IBuildInfo.UNKNOWN_BUILD_ID)) {
			msg.append(" on build ");
			msg.append(getBuildDescription(info));
		}
		for (String buildAttr : info.getBuildAttributes().values()) {
			msg.append(" ");
			msg.append(buildAttr);
		}
		msg.append(" on device ");
		msg.append(device.getSerialNumber());
		CLog.logAndDisplay(LogLevel.INFO, msg.toString());
		mStatus = String.format("running %s on build %s", info.getTestTag(), getBuildDescription(info));
	}

	/**
	 * Returns a user-friendly description of the build
	 * 
	 * @param info
	 * @return
	 */
	private String getBuildDescription(IBuildInfo info) {
		return String.format("'%s'", buildSpacedString(info.getBuildBranch(), info.getBuildFlavor(), info.getBuildId()));
	}

	/**
	 * Helper method for adding space delimited sequence of strings. Will ignore
	 * null segments
	 */
	private String buildSpacedString(String... segments) {
		StringBuilder sb = new StringBuilder();
		for (String s : segments) {
			if (s != null) {
				if (sb.length() > 0) {
					sb.append(' ');
				}
				sb.append(s);
			}
		}
		return sb.toString();
	}

	/**
	 * Performs the invocation
	 * 
	 * @param config
	 *            the {@link IConfiguration}
	 * @param device
	 *            the {@link ITestDevice} to use. May be <code>null</code>
	 * @param info
	 *            the {@link IBuildInfo}
	 */
	private void performInvocation(IConfiguration config, ITestDevice device, IBuildInfo info, IRescheduler rescheduler) throws Throwable {

		boolean resumed = false;
		long startTime = System.currentTimeMillis();
		long elapsedTime = -1;

		info.setDeviceSerial(device.getSerialNumber());
		startInvocation(config, device, info);
		try {
			device.setOptions(config.getDeviceOptions());
			// 准备build和跑case
			prepareAndRun(config, device, info, rescheduler);
		} catch (BuildError e) {
			CLog.w("Build %s failed on device %s. Reason: %s", info.getBuildId(), device.getSerialNumber(), e.toString());
			takeBugreport(device, config.getTestInvocationListeners(), BUILD_ERROR_BUGREPORT_NAME);
			reportFailure(e, config.getTestInvocationListeners(), config, info, rescheduler);
		} catch (TargetSetupError e) {
			CLog.e("Caught exception while running invocation");
			CLog.e(e);
			reportFailure(e, config.getTestInvocationListeners(), config, info, rescheduler);
			// maybe test device if offline, check it
			device.waitForDeviceOnline();
		} catch (DeviceNotAvailableException e) {
			// log a warning here so its captured before reportLogs is called
			CLog.w("Invocation did not complete due to device %s becoming not available. " + "Reason: %s", device.getSerialNumber(), e.getMessage());
			if ((e instanceof DeviceUnresponsiveException) && TestDeviceState.ONLINE.equals(device.getDeviceState())) {
				// under certain cases it might still be possible to grab a
				// bugreport
				takeBugreport(device, config.getTestInvocationListeners(), DEVICE_UNRESPONSIVE_BUGREPORT_NAME);
			}
			resumed = resume(config, info, rescheduler, System.currentTimeMillis() - startTime);
			if (!resumed) {
				reportFailure(e, config.getTestInvocationListeners(), config, info, rescheduler);
			} else {
				CLog.i("Rescheduled failed invocation for resume");
			}
			throw e;
		} catch (RuntimeException e) {
			// log a warning here so its captured before reportLogs is called
			CLog.w("Unexpected exception when running invocation: %s", e.toString());
			reportFailure(e, config.getTestInvocationListeners(), config, info, rescheduler);
			throw e;
		} catch (AssertionError e) {
			CLog.w("Caught AssertionError while running invocation: ", e.toString());
			reportFailure(e, config.getTestInvocationListeners(), config, info, rescheduler);
		} finally {
			mStatus = "done running tests";
			try {
				//
				reportLogs(device, config.getTestInvocationListeners(), config.getLogOutput());
				elapsedTime = System.currentTimeMillis() - startTime;
				if (!resumed) {
					// 发送报告
					InvocationSummaryHelper.reportInvocationEnded(config.getTestInvocationListeners(), elapsedTime);
				}

			} finally {
				config.getBuildProvider().cleanUp(info);
			}
		}
	}

	/**
	 * Do setup, run the tests, then call tearDown
	 */
	private void prepareAndRun(IConfiguration config, ITestDevice device, IBuildInfo info, IRescheduler rescheduler) throws Throwable {
		// use the JUnit3 logic for handling exceptions when running tests
		Throwable exception = null;

		try {
			//
			if (config.getCommandOptions().isNeedPrepare()&&!isRepeat) {
				doSetup(config, device, info);
				//下次启动的时候,不再刷机
				isRepeat = true;
			}else{
				CLog.logAndDisplay(LogLevel.DEBUG, String.format("No need to flash,derect to run case"));
			}
			// 跑case
			runTests(device, info, config, rescheduler);
		} catch (Throwable running) {
			exception = running;
		} finally {
			try {
				if (config.getCommandOptions().isNeedTearDown()) {
					doTeardown(config, device, info, exception);
				}
			} catch (Throwable tearingDown) {
				if (exception == null) {
					exception = tearingDown;
				}
			}
		}
		if (exception != null) {
			throw exception;
		}
	}

	private void doSetup(IConfiguration config, ITestDevice device, IBuildInfo info) throws TargetSetupError, BuildError, DeviceNotAvailableException {
		for (ITargetPreparer preparer : config.getTargetPreparers()) {
			preparer.setUp(device, info);
		}
	}

	private void doTeardown(IConfiguration config, ITestDevice device, IBuildInfo info, Throwable exception) throws DeviceNotAvailableException {
		for (ITargetPreparer preparer : config.getTargetPreparers()) {
			// Note: adjusted indentation below for legibility. If preparer is
			// an
			// ITargetCleaner and we didn't hit DeviceNotAvailableException,
			// then...
			if (preparer instanceof ITargetCleaner && !(exception != null && exception instanceof DeviceNotAvailableException)) {
				ITargetCleaner cleaner = (ITargetCleaner) preparer;
				cleaner.tearDown(device, info, exception);
			}
		}
	}

	/**
	 * Starts the invocation.
	 * <p/>
	 * Starts logging, and informs listeners that invocation has been started.
	 * 
	 * @param config
	 * @param device
	 * @param info
	 */
	private void startInvocation(IConfiguration config, ITestDevice device, IBuildInfo info) {
		logStartInvocation(info, device);
		for (ITestInvocationListener listener : config.getTestInvocationListeners()) {
			try {
				listener.invocationStarted(info);
			} catch (RuntimeException e) {
				// don't let one listener leave the invocation in a bad state
				CLog.e("Caught runtime exception from ITestInvocationListener");
				CLog.e(e);
			}
		}
	}

	/**
	 * Attempt to reschedule the failed invocation to resume where it left off.
	 * <p/>
	 * 
	 * @see IResumableTest
	 * 
	 * @param config
	 * @return <code>true</code> if invocation was resumed successfully
	 */
	private boolean resume(IConfiguration config, IBuildInfo info, IRescheduler rescheduler, long elapsedTime) {
		for (IRemoteTest test : config.getTests()) {
			if (test instanceof IResumableTest) {
				IResumableTest resumeTest = (IResumableTest) test;
				if (resumeTest.isResumable()) {
					// resume this config if any test is resumable
					IConfiguration resumeConfig = config.clone();
					// reuse the same build for the resumed invocation
					IBuildInfo clonedBuild = info.clone();
					resumeConfig.setBuildProvider(new ExistingBuildProvider(clonedBuild, config.getBuildProvider()));
					// create a result forwarder, to prevent sending two
					// invocationStarted events
					resumeConfig.setTestInvocationListener(new ResumeResultForwarder(config.getTestInvocationListeners(), elapsedTime));
					resumeConfig.setLogOutput(config.getLogOutput().clone());
					resumeConfig.setCommandOptions(config.getCommandOptions().clone());
					boolean canReschedule = rescheduler.scheduleConfig(resumeConfig);
					if (!canReschedule) {
						CLog.i("Cannot reschedule resumed config for build %s. Cleaning up build.", info.getBuildId());
						resumeConfig.getBuildProvider().cleanUp(clonedBuild);
					}
					// FIXME: is it a bug to return from here, when we may not
					// have completed the
					// FIXME: config.getTests iteration?
					return canReschedule;
				}
			}
		}
		return false;
	}

	private void reportFailure(Throwable exception, List<ITestInvocationListener> listeners, IConfiguration config, IBuildInfo info, IRescheduler rescheduler) {
		for (ITestInvocationListener listener : listeners) {
			listener.invocationFailed(exception);
		}
		if (!(exception instanceof BuildError)) {
			config.getBuildProvider().buildNotTested(info);
			rescheduleTest(config, rescheduler);
		}
	}

	private void rescheduleTest(IConfiguration config, IRescheduler rescheduler) {
		for (IRemoteTest test : config.getTests()) {
			if (!config.getCommandOptions().isLoopMode() && test instanceof IRetriableTest && ((IRetriableTest) test).isRetriable()) {
				rescheduler.rescheduleCommand();
				return;
			}
		}
	}

	private void reportLogs(ITestDevice device, List<ITestInvocationListener> listeners, ILeveledLogOutput logger) {
		InputStreamSource logcatSource = null;
		InputStreamSource globalLogSource = logger.getLog();
		if (device != null) {
			logcatSource = device.getLogcat();
		}

		for (ITestInvocationListener listener : listeners) {
			if (logcatSource != null) {
				listener.testLog(DEVICE_LOG_NAME, LogDataType.TEXT, logcatSource);
			}
			listener.testLog(TRADEFED_LOG_NAME, LogDataType.TEXT, globalLogSource);
		}

		// Clean up after our ISSen
		if (logcatSource != null) {
			logcatSource.cancel();
		}
		globalLogSource.cancel();

		// once tradefed log is reported, all further log calls for this
		// invocation can get lost
		// unregister logger so future log calls get directed to the tradefed
		// global log
		getLogRegistry().dumpToGlobalLog(logger);
		getLogRegistry().unregisterLogger();
		logger.closeLog();
	}

	private void takeBugreport(ITestDevice device, List<ITestInvocationListener> listeners, String bugreportName) {
		if (device == null) {
			return;
		}

		InputStreamSource bugreport = device.getBugreport();
		try {
			for (ITestInvocationListener listener : listeners) {
				listener.testLog(bugreportName, LogDataType.TEXT, bugreport);
			}
		} finally {
			bugreport.cancel();
		}
	}

	/**
	 * Gets the {@link ILogRegistry} to use.
	 * <p/>
	 * Exposed for unit testing.
	 */
	ILogRegistry getLogRegistry() {
		return LogRegistry.getLogRegistry();
	}

	/**
	 * Runs the test.
	 * 
	 * @param device
	 *            the {@link ITestDevice} to run tests on
	 * @param buildInfo
	 *            the {@link BuildInfo} describing the build target
	 * @param config
	 *            the {@link IConfiguration} to run
	 * @param rescheduler
	 *            the {@link IRescheduler} used to reschedule the test.
	 * @throws DeviceNotAvailableException
	 */
	private void runTests(ITestDevice device, IBuildInfo buildInfo, IConfiguration config, IRescheduler rescheduler) throws DeviceNotAvailableException {
		List<ITestInvocationListener> listeners = config.getTestInvocationListeners();
		for (IRemoteTest test : config.getTests()) {
			if (test instanceof IDeviceTest) {
				((IDeviceTest) test).setDevice(device);
			}
			test.run(new ResultForwarder(listeners));
		}
	}

	@Override
	public String toString() {
		return mStatus;
	}
}
