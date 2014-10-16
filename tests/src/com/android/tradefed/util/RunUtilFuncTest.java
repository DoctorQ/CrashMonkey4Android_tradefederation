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

import com.android.tradefed.util.IRunUtil.IRunnableResult;

import junit.framework.TestCase;

/**
 * Longer running tests for {@link RunUtilFuncTest}
 */
public class RunUtilFuncTest extends TestCase {

    private abstract class MyRunnable implements IRunUtil.IRunnableResult {
        boolean mCanceled = false;

        @Override
        public void cancel() {
            mCanceled = true;
        }
    }

    /**
     * Test timeout case for {@link RunUtil#runTimed(long, IRunnableResult)}.
     */
    public void testRunTimed_timeout() {
        final long timeout = 200;
        MyRunnable mockRunnable = new MyRunnable() {
            @Override
            public boolean run() {
                try {
                    Thread.sleep(timeout*5);
                } catch (InterruptedException e) {
                    // ignore
                }
                return true;
            }
        };
        assertEquals(CommandStatus.TIMED_OUT, RunUtil.getDefault().runTimed(timeout,
                mockRunnable, true));
        assertTrue(mockRunnable.mCanceled);
    }

    /**
     * Test method for {@link RunUtil#runTimedRetry(long, long, , int, IRunnableResult)}.
     * Verify that multiple attempts are made.
     */
    public void testRunTimedRetry() {
        final int maxAttempts = 5;
        final long pollTime = 200;
        IRunUtil.IRunnableResult mockRunnable = new IRunUtil.IRunnableResult() {
            int attempts = 0;
            @Override
            public boolean run() {
                attempts++;
                return attempts == maxAttempts;
            }
            @Override
            public void cancel() {
                // ignore
            }
        };
        final long startTime = System.currentTimeMillis();
        assertTrue(RunUtil.getDefault().runTimedRetry(100, pollTime, maxAttempts, mockRunnable));
        final long actualTime = System.currentTimeMillis() - startTime;
        // assert that time actually taken is at least, and no more than twice expected
        final long expectedPollTime = pollTime * (maxAttempts-1);
        assertTrue(String.format("Expected poll time %d, got %d", expectedPollTime, actualTime),
                expectedPollTime <= actualTime && actualTime <= (2 * expectedPollTime));
    }
}
