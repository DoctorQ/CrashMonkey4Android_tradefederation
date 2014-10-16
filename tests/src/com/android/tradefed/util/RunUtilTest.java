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

import org.easymock.EasyMock;

/**
 * Unit tests for {@link RunUtilTest}
 */
public class RunUtilTest extends TestCase {

    private RunUtil mRunUtil;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mRunUtil = new RunUtil();
    }

    /**
     * Test success case for {@link RunUtil#runTimed(long, IRunnableResult, boolean)}.
     */
    public void testRunTimed() throws Exception {
        IRunUtil.IRunnableResult mockRunnable = EasyMock.createStrictMock(
                IRunUtil.IRunnableResult.class);
        EasyMock.expect(mockRunnable.run()).andReturn(Boolean.TRUE);
        EasyMock.replay(mockRunnable);
        assertEquals(CommandStatus.SUCCESS, mRunUtil.runTimed(100, mockRunnable, true));
    }

    /**
     * Test failure case for {@link RunUtil#runTimed(long, IRunnableResult, boolean)}.
     */
    public void testRunTimed_failed() throws Exception {
        IRunUtil.IRunnableResult mockRunnable = EasyMock.createStrictMock(
                IRunUtil.IRunnableResult.class);
        EasyMock.expect(mockRunnable.run()).andReturn(Boolean.FALSE);
        EasyMock.replay(mockRunnable);
        assertEquals(CommandStatus.FAILED, mRunUtil.runTimed(100, mockRunnable, true));
    }

    /**
     * Test exception case for {@link RunUtil#runTimed(long, IRunnableResult, boolean)}.
     */
    public void testRunTimed_exception() throws Exception {
        IRunUtil.IRunnableResult mockRunnable = EasyMock.createStrictMock(
                IRunUtil.IRunnableResult.class);
        EasyMock.expect(mockRunnable.run()).andThrow(new RuntimeException());
        mockRunnable.cancel();
        EasyMock.replay(mockRunnable);
        assertEquals(CommandStatus.EXCEPTION, mRunUtil.runTimed(100, mockRunnable, true));
    }

    /**
     * Test that {@link RunUtil#runTimedCmd(long, String)} fails when given a garbage command.
     */
    public void testRunTimedCmd_failed() {
        CommandResult result = mRunUtil.runTimedCmd(1000, "blahggggwarggg");
        assertEquals(CommandStatus.EXCEPTION, result.getStatus());
        assertNull(result.getStdout());
        assertNull(result.getStderr());
    }

    /**
     * Test that {@link RunUtil#runTimedCmd(long, String)} fails when garbage times out.
     */
    public void testRunTimedCmd_timeout() {
        // "yes" will never complete
        CommandResult result = mRunUtil.runTimedCmd(100, "yes");
        assertEquals(CommandStatus.TIMED_OUT, result.getStatus());
        assertNull(result.getStdout());
        assertNull(result.getStderr());
    }
}
