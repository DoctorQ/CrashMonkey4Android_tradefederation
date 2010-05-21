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
import com.android.tradefed.util.CommandResult.CommandStatus;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Arrays;

/**
 * A collection of helper methods for executing operations.
 */
public class RunUtil {

    private static final String LOG_TAG = "RunUtil";
    private static final int POLL_TIME_INCREASE_FACTOR = 4;

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
     * Helper method to execute a system command, and aborting if it takes longer than a specified
     * time.
     *
     * @param timeout maximum time to wait in ms
     * @param command the specified system command and optionally arguments to exec
     * @return a {@link CommandResult} containing result from command run
     */
    public static CommandResult runTimedCmd(final long timeout, final String... command) {
        final CommandResult result = new CommandResult();
        IRunnableResult osRunnable = new IRunnableResult() {
            public boolean run() {
                final String fullCmd = Arrays.toString(command);
                Log.d(LOG_TAG, String.format("Running %s", fullCmd));
                try {
                    Process process = Runtime.getRuntime().exec(command);
                    int rc =  process.waitFor();
                    result.setStdout(getStringFromStream(process.getInputStream()));
                    result.setStderr(getStringFromStream(process.getErrorStream()));

                    if (rc == 0) {
                        result.setStatus(CommandStatus.SUCCESS);
                        return true;
                    } else {
                        Log.i(LOG_TAG, String.format("%s command failed. return code %d", fullCmd,
                                rc));
                    }
                } catch (IOException e) {
                    Log.e(LOG_TAG, String.format("IOException when running %s", fullCmd));
                    Log.e(LOG_TAG, e);
                } catch (InterruptedException e) {
                    Log.e(LOG_TAG, String.format("InterruptedException when running %s", fullCmd));
                    Log.e(LOG_TAG, e);
                }
                result.setStatus(CommandStatus.FAILED);
                return false;
            }
        };
        runTimed(timeout, osRunnable);
        return result;
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
            if (runThread.isAlive()) {
                runThread.interrupt();
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
        for (int i = 0; i < attempts; i++) {
            if (runTimed(opTimeout, runnable)) {
                return true;
            }
            Log.d(LOG_TAG, String.format("operation failed, waiting for %d ms", pollInterval));
            sleep(pollInterval);
        }
        return false;
    }

    /**
     * Block and executes an operation multiple times until it is successful.
     * <p/>
     * Exponentially increase the wait time between operation attempts. This is intended to be
     * used when performing an operation such as polling a server, to give it time to recover
     * in case it is temporarily down.
     *
     * @param opTimeout maximum time to wait in ms for a single operation attempt
     * @param initialPollInterval initial time to wait between operation attempts
     * @param maxPollInterval the max time to wait between operation attempts
     * @param maxTime the total approximate maximum time to keep trying the operation
     * @param runnable {@link IRunnableResult} to execute
     * @return <code>true</code> if operation completed successfully before maxTime expired
     */
    public static boolean runEscalatingTimedRetry(final long opTimeout,
            final long initialPollInterval, final long maxPollInterval, final long maxTime,
            final IRunnableResult runnable) {
        // wait an initial time provided
        long pollInterval = initialPollInterval;
        final long initialTime = System.currentTimeMillis();
        while (System.currentTimeMillis() < (initialTime + maxTime)) {
            if (runTimed(opTimeout, runnable)) {
                return true;
            }
            Log.d(LOG_TAG, String.format("operation failed, waiting for %d ms", pollInterval));
            sleep(pollInterval);
            // somewhat arbitrarily, increase the poll time by a factor of 4 for each attempt,
            // up to the previously decided maximum
            pollInterval *= POLL_TIME_INCREASE_FACTOR;
            if (pollInterval > maxPollInterval) {
                pollInterval = maxPollInterval;
            }
        }
        return false;
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

    private static String getStringFromStream(InputStream stream) throws IOException {
        Reader ir = new BufferedReader(new InputStreamReader(stream));
        int irChar = -1;
        StringBuilder builder = new StringBuilder();
        while((irChar = ir.read()) != -1) {
            builder.append((char)irChar);
        }
        return builder.toString();
    }
}
