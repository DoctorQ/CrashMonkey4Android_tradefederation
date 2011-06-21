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
import com.android.tradefed.log.LogUtil.CLog;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * A collection of helper methods for executing operations.
 */
public class RunUtil implements IRunUtil {

    private static final String LOG_TAG = "RunUtil";
    private static final int POLL_TIME_INCREASE_FACTOR = 4;
    private static IRunUtil sDefaultInstance = null;
    private final ProcessBuilder mProcessBuilder;

    /**
     * Create a new {@link RunUtil} object to use.
     */
    public RunUtil() {
        mProcessBuilder = new ProcessBuilder();
    }

    /**
     * Get a reference to the default {@link RunUtil} object.
     * <p/>
     * This is useful for callers who want to use IRunUtil without customization.
     * Its recommended that callers who do need a custom IRunUtil instance
     * (ie need to call either {@link #setEnvVariable(String, String)} or
     * {@link #setWorkingDir(File)} create their own copy.
     */
    public static IRunUtil getDefault() {
        if (sDefaultInstance == null) {
            sDefaultInstance  = new RunUtil();
        }
        return sDefaultInstance;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setWorkingDir(File dir) {
        mProcessBuilder.directory(dir);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setEnvVariable(String name, String value) {
        mProcessBuilder.environment().put(name, value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CommandResult runTimedCmd(final long timeout, final String... command) {
        final CommandResult result = new CommandResult();
        IRunUtil.IRunnableResult osRunnable = new RunnableResult(result, null, command);
        CommandStatus status = runTimed(timeout, osRunnable, true);
        result.setStatus(status);
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CommandResult runTimedCmdWithInput(final long timeout, String input,
            final String... command) {
        final CommandResult result = new CommandResult();
        IRunUtil.IRunnableResult osRunnable = new RunnableResult(result, input, command);
        CommandStatus status = runTimed(timeout, osRunnable, true);
        result.setStatus(status);
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CommandResult runTimedCmdSilently(final long timeout, final String... command) {
        final CommandResult result = new CommandResult();
        IRunUtil.IRunnableResult osRunnable = new RunnableResult(result, null, command);
        CommandStatus status = runTimed(timeout, osRunnable, false);
        result.setStatus(status);
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Process runCmdInBackground(final String... command) throws IOException  {
        final String fullCmd = Arrays.toString(command);
        CLog.v("Running %s", fullCmd);
        return mProcessBuilder.command(command).start();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Process runCmdInBackground(final List<String> command) throws IOException  {
        CLog.v("Running %s", command);
        return mProcessBuilder.command(command).start();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CommandStatus runTimed(long timeout, IRunUtil.IRunnableResult runnable,
            boolean logErrors) {
        RunnableNotifier runThread = new RunnableNotifier(runnable, logErrors);
        synchronized (runThread) {
            runThread.start();
            try {
                runThread.wait(timeout);
            } catch (InterruptedException e) {
                CLog.i("runnable interrupted");
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
    @Override
    public boolean runTimedRetry(long opTimeout, long pollInterval, int attempts,
            IRunUtil.IRunnableResult runnable) {
        for (int i = 0; i < attempts; i++) {
            if (runTimed(opTimeout, runnable, true) == CommandStatus.SUCCESS) {
                return true;
            }
            CLog.d("operation failed, waiting for %d ms", pollInterval);
            sleep(pollInterval);
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean runFixedTimedRetry(final long opTimeout, final long pollInterval,
            final long maxTime, final IRunUtil.IRunnableResult runnable) {
        final long initialTime = System.currentTimeMillis();
        while (System.currentTimeMillis() < (initialTime + maxTime)) {
            if (runTimed(opTimeout, runnable, true) == CommandStatus.SUCCESS) {
                return true;
            }
            CLog.d("operation failed, waiting for %d ms", pollInterval);
            sleep(pollInterval);
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean runEscalatingTimedRetry(final long opTimeout,
            final long initialPollInterval, final long maxPollInterval, final long maxTime,
            final IRunUtil.IRunnableResult runnable) {
        // wait an initial time provided
        long pollInterval = initialPollInterval;
        final long initialTime = System.currentTimeMillis();
        while (System.currentTimeMillis() < (initialTime + maxTime)) {
            if (runTimed(opTimeout, runnable, true) == CommandStatus.SUCCESS) {
                return true;
            }
            CLog.d("operation failed, waiting for %d ms", pollInterval);
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
    @Override
    public void sleep(long time) {
        if (time <= 0) {
            return;
        }
        try {
            Thread.sleep(time);
        } catch (InterruptedException e) {
            // ignore
            CLog.d("sleep interrupted");
        }
    }

    /**
     * Helper thread that wraps a runnable, and notifies when done.
     */
    private static class RunnableNotifier extends Thread {

        private final IRunUtil.IRunnableResult mRunnable;
        private CommandStatus mStatus = CommandStatus.TIMED_OUT;
        private boolean mLogErrors = true;

        RunnableNotifier(IRunUtil.IRunnableResult runnable, boolean logErrors) {
            mRunnable = runnable;
            mLogErrors = logErrors;
        }

        @Override
        public void run() {
            CommandStatus status;
            try {
                status = mRunnable.run() ? CommandStatus.SUCCESS : CommandStatus.FAILED;
            } catch (InterruptedException e) {
                CLog.i("runutil interrupted");
                status = CommandStatus.EXCEPTION;
            } catch (Exception e) {
                if (mLogErrors) {
                    CLog.e("Exception occurred when executing runnable");
                    CLog.e(e);
                }
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

    private class RunnableResult implements IRunUtil.IRunnableResult {
        private final String[] mCommand;
        private final CommandResult mCommandResult;
        private final String mInput;

        RunnableResult(final CommandResult result, final String input, final String... command) {
            mCommand = command;
            mInput = input;
            mCommandResult = result;
        }

        @Override
        public boolean run() throws Exception {
            final String fullCmd = Arrays.toString(mCommand);
            Log.v(LOG_TAG, String.format("Running %s", fullCmd));
            Process process = mProcessBuilder.command(mCommand).start();
            if (mInput != null) {
                BufferedOutputStream processStdin = new BufferedOutputStream(
                        process.getOutputStream());
                processStdin.write(mInput.getBytes("UTF-8"));
                processStdin.flush();
                processStdin.close();
            }
            int rc = process.waitFor();
            mCommandResult.setStdout(StreamUtil.getStringFromStream(process.getInputStream()));
            mCommandResult.setStderr(StreamUtil.getStringFromStream(process.getErrorStream()));

            if (rc == 0) {
                return true;
            } else {
                Log.i(LOG_TAG, String.format("%s command failed. return code %d", fullCmd, rc));
            }
            return false;
        }

        @Override
        public void cancel() {
        }
    };
}
