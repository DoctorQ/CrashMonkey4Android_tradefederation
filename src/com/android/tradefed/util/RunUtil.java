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
     * Block and executes an operation, aborting if it takes longer than a specified time.
     *
     * @param timeout maximum time to wait in ms
     * @param runnable {@link Runnable} to execute
     * @return <code>true</code> if operation completed before timeout reached.
     */
    public static boolean runTimed(long timeout, Runnable runnable) {
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
     * Helper thread that wraps a runnable, and notifies when done.
     */
    private static class RunnableNotifier extends Thread {

        private Runnable mRunnable;
        private boolean mIsComplete = false;

        RunnableNotifier(Runnable runnable) {
            mRunnable = runnable;
        }

        @Override
        public void run() {
            mRunnable.run();
            synchronized (this) {
               mIsComplete = true;
               notify();
            }
        }

        synchronized boolean isComplete() {
            return mIsComplete;
        }
    }
}
