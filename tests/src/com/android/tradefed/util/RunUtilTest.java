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

import com.android.tradefed.util.CommandStatus;
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
    public void testRunTimed() throws Exception {
        IRunnableResult mockRunnable = EasyMock.createStrictMock(IRunnableResult.class);
        EasyMock.expect(mockRunnable.run()).andReturn(Boolean.TRUE);
        EasyMock.replay(mockRunnable);
        assertEquals(CommandStatus.SUCCESS, RunUtil.runTimed(100, mockRunnable));
    }

    /**
     * Test failure case for {@link RunUtil#runTimed(long, IRunnableResult)}.
     */
    public void testRunTimed_failed() throws Exception {
        IRunnableResult mockRunnable = EasyMock.createStrictMock(IRunnableResult.class);
        EasyMock.expect(mockRunnable.run()).andReturn(Boolean.FALSE);
        EasyMock.replay(mockRunnable);
        assertEquals(CommandStatus.FAILED, RunUtil.runTimed(100, mockRunnable));
    }

    /**
     * Test exception case for {@link RunUtil#runTimed(long, IRunnableResult)}.
     */
    public void testRunTimed_exception() throws Exception {
        IRunnableResult mockRunnable = EasyMock.createStrictMock(IRunnableResult.class);
        EasyMock.expect(mockRunnable.run()).andThrow(new RuntimeException());
        EasyMock.replay(mockRunnable);
        assertEquals(CommandStatus.EXCEPTION, RunUtil.runTimed(100, mockRunnable));
    }

    /**
     * Test that {@link RunUtil#runTimedCmd(long, String)} fails when given a garbage command.
     */
    public void testRunTimedCmd_failed() {
        CommandResult result = RunUtil.runTimedCmd(1000, "blahggggwarggg");
        assertEquals(CommandStatus.EXCEPTION, result.getStatus());
        assertNull(result.getStdout());
        assertNull(result.getStderr());
    }

    /**
     * Test that {@link RunUtil#runTimedCmd(long, String)} succeeds when given a simple command.
     */
    public void testRunTimedCmd_dir() {
        CommandResult result = RunUtil.runTimedCmd(1000, "dir");
        assertEquals(CommandStatus.SUCCESS, result.getStatus());
        assertTrue(result.getStdout().length() > 0);
        assertEquals(0, result.getStderr().length());
    }

    /**
     * Test that {@link RunUtil#runTimedCmd(long, String)} fails when garbage times out.
     */
    public void testRunTimedCmd_timeout() {
        // "yes" will never complete
        CommandResult result = RunUtil.runTimedCmd(100, "yes");
        assertEquals(CommandStatus.TIMED_OUT, result.getStatus());
        assertNull(result.getStdout());
        assertNull(result.getStderr());
    }
}
