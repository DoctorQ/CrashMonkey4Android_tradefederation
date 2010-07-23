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
package com.android.tradefed.testtype;

import com.android.ddmlib.Log;
import com.android.ddmlib.testrunner.ITestRunListener;
import com.android.ddmlib.testrunner.TestIdentifier;

import java.util.Map;

/**
 * A test listener that ensures tests complete within a given time.
 */
public class TestTimeoutListener implements ITestRunListener {

    private static final String LOG_TAG = "TestTimeoutListener";

    /**
     * listener for timeout events
     */
    public static interface ITimeoutCallback {

        /**
         * Called when a test has timed out.
         *
         * @param test the {@link TestIdentifier} that did not complete.
         */
        void testTimeout(TestIdentifier test);
    }

    private class TestMonitor extends Thread {

        private long mStartTime = Long.MAX_VALUE;
        private boolean mIsCanceled = false;
        private final long mTestTimeout;
        private final ITimeoutCallback mCallback;
        private TestIdentifier mCurrentTest;

        TestMonitor(long testTimeout, ITimeoutCallback callback) {
            mTestTimeout = testTimeout;
            mCallback = callback;
        }

        @Override
        public void run() {
            while (!mIsCanceled) {
                synchronized (this) {
                    final long currentTime = System.currentTimeMillis();
                    if ((currentTime - mStartTime) > mTestTimeout) {
                        mCallback.testTimeout(mCurrentTest);
                        break;
                    }

                    try {
                        wait(mTestTimeout);
                    } catch (InterruptedException e) {
                        Log.i(LOG_TAG, "interrupted");
                    }
                }
            }
        }

        public synchronized void start(TestIdentifier test) {
            mStartTime = System.currentTimeMillis();
            mCurrentTest = test;
            notify();
        }

        @SuppressWarnings("unused")
        public synchronized void reset() {
            mStartTime = Long.MAX_VALUE;
            notify();
        }

        public synchronized void cancel() {
            mIsCanceled = true;
            notify();
        }
     }

    private final TestMonitor mMonitor;

    /**
     * Creates a {@link TestTimeoutListener}.
     *
     * @param testTimeout the maximum test time to allow in ms
     * @param callback listener to be informed if a test times out
     */
    public TestTimeoutListener(long testTimeout, ITimeoutCallback callback ) {
        mMonitor = new TestMonitor(testTimeout, callback);
    }

    /**
     * {@inheritDoc}
     */
    public void testStarted(TestIdentifier test) {
        mMonitor.start(test);
    }

    /**
     * {@inheritDoc}
     */
    public void testEnded(TestIdentifier test) {
        // TODO: might not be needed. Its a fairly safe assumption that the next testStarted call
        // will immediately follow previous testEnded
        //mMonitor.reset();
    }

    /**
     * {@inheritDoc}
     */
    public void testFailed(TestFailure status, TestIdentifier test, String trace) {
        // ignore
    }

    /**
     * {@inheritDoc}
     */
    public void testRunEnded(long elapsedTime, Map<String, String> resultBundle) {
        mMonitor.cancel();
    }

    /**
     * {@inheritDoc}
     */
    public void testRunFailed(String errorMessage) {
        mMonitor.cancel();
    }

    /**
     * {@inheritDoc}
     */
    public void testRunStarted(int testCount) {
        mMonitor.start();
    }

    /**
     * {@inheritDoc}
     */
    public void testRunStopped(long elapsedTime) {
        mMonitor.cancel();
    }
}
