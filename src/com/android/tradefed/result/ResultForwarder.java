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
package com.android.tradefed.result;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.build.IBuildInfo;

/**
 * ResultForwarder本身是ITestInvocationListener子类,
 * 然后有包含了其他的ITestInvocationListener的子类,这样就通过该类得到所有的监听器 A
 * {@link ITestInvocationListener} that forwards invocation results to a list of
 * other listeners.
 */
public class ResultForwarder implements ITestInvocationListener {

	private List<ITestInvocationListener> mListeners;

	/**
	 * Create a {@link ResultForwarder} with deferred listener setting. Intended
	 * only for use by subclasses.
	 */
	protected ResultForwarder() {
		mListeners = Collections.emptyList();
	}

	/**
	 * Create a {@link ResultForwarder}.
	 * 
	 * @param listeners
	 *            the real {@link ITestInvocationListener}s to forward results
	 *            to
	 */
	public ResultForwarder(List<ITestInvocationListener> listeners) {
		mListeners = listeners;
	}

	/**
	 * Alternate variable arg constructor for {@link ResultForwarder}.
	 * 
	 * @param listeners
	 *            the real {@link ITestInvocationListener}s to forward results
	 *            to
	 */
	public ResultForwarder(ITestInvocationListener... listeners) {
		mListeners = Arrays.asList(listeners);
	}

	/**
	 * Set the listeners after construction. Intended only for use by
	 * subclasses.
	 * 
	 * @param listeners
	 *            the real {@link ITestInvocationListener}s to forward results
	 *            to
	 */
	protected void setListeners(List<ITestInvocationListener> listeners) {
		mListeners = listeners;
	}

	public List<ITestInvocationListener> getListeners() {
		return mListeners;
	}

	/**
	 * Set the listeners after construction. Intended only for use by
	 * subclasses.
	 * 
	 * @param listeners
	 *            the real {@link ITestInvocationListener}s to forward results
	 *            to
	 */
	protected void setListeners(ITestInvocationListener... listeners) {
		mListeners = Arrays.asList(listeners);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void invocationStarted(IBuildInfo buildInfo) {
		for (ITestInvocationListener listener : mListeners) {
			listener.invocationStarted(buildInfo);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void invocationFailed(Throwable cause) {
		for (ITestInvocationListener listener : mListeners) {
			listener.invocationFailed(cause);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void invocationEnded(long elapsedTime) {
		InvocationSummaryHelper.reportInvocationEnded(mListeners, elapsedTime);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public TestSummary getSummary() {
		// should never be called
		return null;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void testLog(String dataName, LogDataType dataType,
			InputStreamSource dataStream) {
		// CLog.logAndDisplay(LogLevel.INFO,
		// String.format("[testLog] dataName: %s", dataName));
		for (ITestInvocationListener listener : mListeners) {
			listener.testLog(dataName, dataType, dataStream);
		}
	}

	/**
	 * {@inheritDoc}case开始时显式的调用
	 */
	@Override
	public void testRunStarted(String runName, int testCount) {
		// CLog.logAndDisplay(LogLevel.INFO, String
		// .format("[testRunStarted] runName: %s testCount:%d", runName,
		// testCount));
		for (ITestInvocationListener listener : mListeners) {
			listener.testRunStarted(runName, testCount);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void testRunFailed(String errorMessage) {
		// CLog.logAndDisplay(LogLevel.INFO,
		// String.format("[testRunFailed] errorMessage: %s", errorMessage));
		for (ITestInvocationListener listener : mListeners) {
			listener.testRunFailed(errorMessage);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void testRunStopped(long elapsedTime) {
		// CLog.logAndDisplay(LogLevel.INFO,
		// String.format("[testRunStopped] elapsedTime: %d", elapsedTime));
		for (ITestInvocationListener listener : mListeners) {
			listener.testRunStopped(elapsedTime);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void testRunEnded(long elapsedTime, Map<String, String> runMetrics) {
		// CLog.logAndDisplay(LogLevel.INFO,
		// String.format("[testRunEnded] elapsedTime: %d", elapsedTime));
		for (ITestInvocationListener listener : mListeners) {
			listener.testRunEnded(elapsedTime, runMetrics);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void testStarted(TestIdentifier test) {
		// CLog.logAndDisplay(LogLevel.INFO,
		// String.format("[testStarted] %s", test.toString()));
		for (ITestInvocationListener listener : mListeners) {
			listener.testStarted(test);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void testFailed(TestFailure status, TestIdentifier test, String trace) {
		// CLog.logAndDisplay(LogLevel.INFO,
		// String.format("[testFailed] %s", test.toString()));
		for (ITestInvocationListener listener : mListeners) {
			listener.testFailed(status, test, trace);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void testEnded(TestIdentifier test, Map<String, String> testMetrics) {
		// CLog.logAndDisplay(LogLevel.INFO,
		// String.format("[testEnded] %s", test.toString()));
		for (ITestInvocationListener listener : mListeners) {
			listener.testEnded(test, testMetrics);
		}
	}

	@Override
	protected Object clone() throws CloneNotSupportedException {
		// TODO Auto-generated method stub
		return super.clone();
	}

}
