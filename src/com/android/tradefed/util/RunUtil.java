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
package com.android.tradefed.util;

import com.android.ddmlib.Log;

/**
 * A collection of helper methods for executing operations.
 */
public class RunUtil {

    private static final String LOG_TAG = "RunUtil";

    private RunUtil() {
    }

    /**
     * An interface for asynchronously executing an operation that returns a boolean status.
     */
    public static interface IRunnableResult {

        /**
         * Execute the operation.
         *
         * @return <code>true</code> if operation is performed successfully, <code>false</code>
         * otherwise
         */
        public boolean run();
    }

    /**
     * Block and executes an operation, aborting if it takes longer than a specified time.
     *
     * @param timeout maximum time to wait in ms
     * @param runnable {@link IRunnableResult} to execute
     * @return <code>true</code> if operation completed successfully before timeout reached.
     */
    public static boolean runTimed(long timeout, IRunnableResult runnable) {
        RunnableNotifier runThread = new RunnableNotifier(runnable);
        runThread.start();
        synchronized (runThread) {
            try {
                runThread.wait(timeout);
            } catch (InterruptedException e) {
                Log.i(LOG_TAG, "runnable interrupted");
            }
        }
        return runThread.isComplete();
    }

    /**
     * Block and executes an operation multiple times until it is successful.
     *
     * @param opTimeout maximum time to wait in ms for one operation attempt
     * @param pollInterval time to wait between command retries
     * @param attempts the maximum number of attempts to try
     * @param runnable {@link IRunnableResult} to execute
     * @return <code>true</code> if operation completed successfully before attempts reached.
     */
    public static boolean runTimedRetry(long opTimeout, long pollInterval, int attempts,
            IRunnableResult runnable) {
        return runEscalatingTimedRetry(opTimeout, pollInterval, 1 /* pollTimeIncreaseFactor */,
                attempts, runnable);
    }

    /**
     * Block and executes an operation multiple times until it is successful.
     * <p/>
     * Exponentially increase the wait time between operation attempts. This is intended to be
     * used when performing an operation such as polling a server, to give it time to recover
     * in case it is temporarily down.
     *
     * @param opTimeout maximum time to wait in ms for one operation attempt
     * @param initialPollInterval the initial time in ms to wait between command retries
     * @param pollIntervalScaleFactor factor to increase poll period by after each failed runnable
     * execution. eg if initialPollInterval = 1 and pollIntervalScaleFactor = 2, poll times will be
     * 1, 2, 4, 8, etc
     * @param attempts the maximum number of attempts to try
     * @param runnable {@link IRunnableResult} to execute
     * @return <code>true</code> if operation completed successfully before attempts reached.
     */
    public static boolean runEscalatingTimedRetry(long opTimeout, long initialPollInterval,
            int pollIntervalScaleFactor, int attempts, IRunnableResult runnable) {
        boolean success = false;
        long pollInterval = initialPollInterval;
        for (int i = 0; i < attempts; i++) {
            success = runTimed(opTimeout, runnable);
            if (success) {
                break;
            }
            Log.d(LOG_TAG, String.format("operation failed, waiting for %d ms", pollInterval));
            sleep(pollInterval);
            pollInterval *= pollIntervalScaleFactor;
        }
        return success;
    }

    /**
     * Helper method to sleep for given time, ignoring any exceptions.
     *
     * @param time ms to sleep
     */
    public static void sleep(long time) {
        try {
            Thread.sleep(time);
        } catch (InterruptedException e) {
            // ignore
            Log.d(LOG_TAG, "sleep interrupted");
        }
    }

    /**
     * Helper thread that wraps a runnable, and notifies when done.
     */
    private static class RunnableNotifier extends Thread {

        private final IRunnableResult mRunnable;
        private boolean mIsComplete = false;

        RunnableNotifier(IRunnableResult runnable) {
            mRunnable = runnable;
        }

        @Override
        public void run() {
            boolean complete = mRunnable.run();
            synchronized (this) {
                mIsComplete = complete;
                notify();
            }
        }

        synchronized boolean isComplete() {
            return mIsComplete;
        }
    }
}
