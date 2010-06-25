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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Arrays;

/**
 * A collection of helper methods for executing operations.
 */
public class RunUtil implements IRunUtil {

    private static final String LOG_TAG = "RunUtil";
    private static final int POLL_TIME_INCREASE_FACTOR = 4;
    private static IRunUtil sInstance = null;

    private RunUtil() {
    }

    public static IRunUtil getInstance() {
        if (sInstance == null) {
            sInstance  = new RunUtil();
        }
        return sInstance;
    }

    /**
     * {@inheritDoc}
     */
    public CommandResult runTimedCmd(final long timeout, final String... command) {
        final CommandResult result = new CommandResult();
        IRunUtil.IRunnableResult osRunnable = new IRunUtil.IRunnableResult() {
            public boolean run() throws Exception {
                final String fullCmd = Arrays.toString(command);
                Log.v(LOG_TAG, String.format("Running %s", fullCmd));
                Process process = Runtime.getRuntime().exec(command);
                int rc = process.waitFor();
                result.setStdout(getStringFromStream(process.getInputStream()));
                result.setStderr(getStringFromStream(process.getErrorStream()));

                if (rc == 0) {
                    return true;
                } else {
                    Log.i(LOG_TAG, String.format("%s command failed. return code %d", fullCmd, rc));
                }
                return false;
            }

            public void cancel() {
            }
        };
        CommandStatus status = runTimed(timeout, osRunnable);
        result.setStatus(status);
        return result;
    }

    /**
     * {@inheritDoc}
     */
    public CommandStatus runTimed(long timeout, IRunUtil.IRunnableResult runnable) {
        RunnableNotifier runThread = new RunnableNotifier(runnable);
        runThread.start();
        synchronized (runThread) {
            try {
                // if runnable finishes super quick, might be done already. Only wait if
                // current status == NOT DONE which == TIMEOUT
                if (runThread.getStatus() == CommandStatus.TIMED_OUT) {
                    runThread.wait(timeout);
                }
            } catch (InterruptedException e) {
                Log.i(LOG_TAG, "runnable interrupted");
            }
            if (runThread.getStatus() == CommandStatus.TIMED_OUT ||
                    runThread.getStatus() == CommandStatus.EXCEPTION) {
                runThread.interrupt();
            }
        }
        return runThread.getStatus();
    }

    /**
     * {@inheritDoc}
     */
    public boolean runTimedRetry(long opTimeout, long pollInterval, int attempts,
            IRunUtil.IRunnableResult runnable) {
        for (int i = 0; i < attempts; i++) {
            if (runTimed(opTimeout, runnable) == CommandStatus.SUCCESS) {
                return true;
            }
            Log.d(LOG_TAG, String.format("operation failed, waiting for %d ms", pollInterval));
            sleep(pollInterval);
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    public boolean runFixedTimedRetry(final long opTimeout, final long pollInterval,
            final long maxTime, final IRunUtil.IRunnableResult runnable) {
        final long initialTime = System.currentTimeMillis();
        while (System.currentTimeMillis() < (initialTime + maxTime)) {
            if (runTimed(opTimeout, runnable) == CommandStatus.SUCCESS) {
                return true;
            }
            Log.d(LOG_TAG, String.format("operation failed, waiting for %d ms", pollInterval));
            sleep(pollInterval);
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    public boolean runEscalatingTimedRetry(final long opTimeout,
            final long initialPollInterval, final long maxPollInterval, final long maxTime,
            final IRunUtil.IRunnableResult runnable) {
        // wait an initial time provided
        long pollInterval = initialPollInterval;
        final long initialTime = System.currentTimeMillis();
        while (System.currentTimeMillis() < (initialTime + maxTime)) {
            if (runTimed(opTimeout, runnable) == CommandStatus.SUCCESS) {
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
     * {@inheritDoc}
     */
    public void sleep(long time) {
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

        private final IRunUtil.IRunnableResult mRunnable;
        private CommandStatus mStatus = CommandStatus.TIMED_OUT;

        RunnableNotifier(IRunUtil.IRunnableResult runnable) {
            mRunnable = runnable;
        }

        @Override
        public void run() {
            CommandStatus status;
            try {
                status = mRunnable.run() ? CommandStatus.SUCCESS : CommandStatus.FAILED;
            } catch (Exception e) {
                // TODO: add more meaningful error message
                Log.e(LOG_TAG, e);
                status = CommandStatus.EXCEPTION;
            }
            synchronized (this) {
                mStatus = status;
                notify();
            }
        }

        @Override
        public void interrupt() {
            mRunnable.cancel();
            super.interrupt();
        }

        synchronized CommandStatus getStatus() {
            return mStatus;
        }
    }

    private String getStringFromStream(InputStream stream) throws IOException {
        Reader ir = new BufferedReader(new InputStreamReader(stream));
        int irChar = -1;
        StringBuilder builder = new StringBuilder();
        while ((irChar = ir.read()) != -1) {
            builder.append((char)irChar);
        }
        return builder.toString();
    }
}
