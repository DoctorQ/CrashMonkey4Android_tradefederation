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

import com.android.tradefed.util.RunUtil.IRunnableResult;

import org.easymock.EasyMock;

import junit.framework.TestCase;

/**
 * Unit tests for {@link RunUtilTest}
 */
public class RunUtilTest extends TestCase {

    /**
     * Test success case for {@link RunUtil#runTimed(long, IRunnableResult)}.
     */
    public void testRunTimed() {
        IRunnableResult mockRunnable = EasyMock.createStrictMock(IRunnableResult.class);
        EasyMock.expect(mockRunnable.run()).andReturn(Boolean.TRUE);
        EasyMock.replay(mockRunnable);
        assertTrue(RunUtil.runTimed(100, mockRunnable));
    }

    /**
     * Test failure case for {@link RunUtil#runTimed(long, IRunnableResult)}.
     */
    public void testRunTimed_failed() {
        IRunnableResult mockRunnable = EasyMock.createStrictMock(IRunnableResult.class);
        EasyMock.expect(mockRunnable.run()).andReturn(Boolean.FALSE);
        EasyMock.replay(mockRunnable);
        assertFalse(RunUtil.runTimed(100, mockRunnable));
    }

    /**
     * Test timeout case for {@link RunUtil#runTimed(long, IRunnableResult)}.
     */
    public void testRunTimed_timeout() {
        final long timeout = 200;
        IRunnableResult mockRunnable = new IRunnableResult() {
            public boolean run() {
                try {
                    Thread.sleep(timeout*5);
                } catch (InterruptedException e) {
                    // ignore
                }
                return true;
            }
        };
        assertFalse(RunUtil.runTimed(timeout, mockRunnable));
    }

    /**
     * Test method for {@link RunUtil#runTimedRetry(long, long, , int, IRunnableResult)}.
     * Verify that multiple attempts are made.
     */
    public void testRunTimedRetry() {
        final int maxAttempts = 5;
        final long pollTime = 200;
        IRunnableResult mockRunnable = new IRunnableResult() {
            int attempts = 0;
            public boolean run() {
                attempts++;
                return attempts == maxAttempts;
            }
        };
        final long startTime = System.currentTimeMillis();
        assertTrue(RunUtil.runTimedRetry(100, pollTime, maxAttempts, mockRunnable));
        final long actualTime = System.currentTimeMillis() - startTime;
        // assert that time actually taken is at least, and no more than twice expected
        final long expectedPollTime = pollTime * maxAttempts;
        assertTrue(String.format("Expected poll time %d, got %d", expectedPollTime, actualTime),
                expectedPollTime <= actualTime && actualTime <= (2 * expectedPollTime));
    }

    /**
     * Test that {@link RunUtil#runTimedCmd(long, String)} fails when given a garbage command.
     */
    public void testRunTimedCmd_failed() {
        assertFalse(RunUtil.runTimedCmd(1000, "blahggggwarggg"));
    }

    /**
     * Test that {@link RunUtil#runTimedCmd(long, String)} succeeds when given a simple command.
     */
    public void testRunTimedCmd_dir() {
        assertTrue(RunUtil.runTimedCmd(1000, "dir"));
    }
}
