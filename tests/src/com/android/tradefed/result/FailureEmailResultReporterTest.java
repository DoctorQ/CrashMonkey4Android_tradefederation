/*
 * Copyright (C) 2011 The Android Open Source Project
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
package com.android.tradefed.result;

import com.android.tradefed.build.BuildInfo;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.util.IEmail;
import com.android.tradefed.util.IEmail.Message;

import junit.framework.TestCase;

import org.easymock.Capture;
import org.easymock.EasyMock;

import java.io.IOException;

/**
 * Unit tests for {@link EmailResultReporter}.
 */
public class FailureEmailResultReporterTest extends TestCase {
    private IEmail mMockMailer;
    private FailureEmailResultReporter mEmailReporter;

    private class FakeFailureEmailResultReporter extends FailureEmailResultReporter {
        private boolean mHasFailedTests;
        private InvocationStatus mInvocationStatus;

        FakeFailureEmailResultReporter(boolean sendOnFailure, boolean sendOnInvFailure,
                boolean hasFailedTests, InvocationStatus invocationStatus) {
            super();

            setSendOnFailure(sendOnFailure);
            setSendOnInvocationFailure(sendOnInvFailure);
            mHasFailedTests = hasFailedTests;
            mInvocationStatus = invocationStatus;
        }

        /**
         * {@inheritDoc}
         */
        public boolean hasFailedTests() {
            return mHasFailedTests;
        }

        /**
         * {@inheritDoc}
         */
        public InvocationStatus getInvocationStatus() {
            return mInvocationStatus;
        }
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mMockMailer = EasyMock.createMock(IEmail.class);
        mEmailReporter = new FailureEmailResultReporter(mMockMailer);
    }

    /**
     * Test normal success case for {@link EmailResultReporter#invocationEnded(long)}.
     */
    public void testInvocationEnded_empty() throws IllegalArgumentException, IOException {
        EasyMock.replay(mMockMailer);
        mEmailReporter.invocationStarted(new BuildInfo("888", "mytest", "mybuild"));
        mEmailReporter.addDestination("foo");
        mEmailReporter.invocationEnded(0);
        EasyMock.verify(mMockMailer);
    }

    /**
     * Test that no email is sent if
     * {@link EmailResultReporter#setSendOnInvocationFailure(boolean)} is set
     */
    public void testInvocationEnded_onFailure() throws IllegalArgumentException, IOException {
        mEmailReporter.setSendOnInvocationFailure(true);
        EasyMock.replay(mMockMailer);
        mEmailReporter.invocationStarted(new BuildInfo("888", "mytest", "mybuild"));
        mEmailReporter.addDestination("foo");
        mEmailReporter.invocationEnded(0);
        EasyMock.verify(mMockMailer);
    }

    /**
     * Test that email is sent if
     * {@link EmailResultReporter#setSendOnInvocationFailure(boolean)} is set and invocation
     * failed
     */
    public void testInvocationEnded_invFailure() throws IllegalArgumentException, IOException {
        Capture<Message> msgCapture = new Capture<Message>();
        mMockMailer.send(EasyMock.capture(msgCapture));
        mEmailReporter.setSendOnInvocationFailure(true);
        EasyMock.replay(mMockMailer);
        mEmailReporter.invocationStarted(new BuildInfo("888", "mytest", "mybuild"));
        mEmailReporter.addDestination("foo");
        mEmailReporter.invocationFailed(new BuildError("boot failed"));
        mEmailReporter.invocationEnded(0);
        EasyMock.verify(mMockMailer);
        Message capturedMessage = msgCapture.getValue();
        // ensure invocation stack trace is present
        assertTrue(capturedMessage.getBody().contains("BuildError"));
    }

    public void testShouldSendMessage() {
        FakeFailureEmailResultReporter r;

        // Don't send email on success
        r = new FakeFailureEmailResultReporter(false, false, false, InvocationStatus.SUCCESS);
        assertFalse(r.shouldSendMessage());

        r = new FakeFailureEmailResultReporter(false, true, false, InvocationStatus.SUCCESS);
        assertFalse(r.shouldSendMessage());

        r = new FakeFailureEmailResultReporter(true, false, false, InvocationStatus.SUCCESS);
        assertFalse(r.shouldSendMessage());

        r = new FakeFailureEmailResultReporter(true, true, false, InvocationStatus.SUCCESS);
        assertFalse(r.shouldSendMessage());

        // When hasTestFailures() is true, only send email when send-only-on-failure flag is set
        r = new FakeFailureEmailResultReporter(false, false, true, InvocationStatus.SUCCESS);
        assertFalse(r.shouldSendMessage());

        r = new FakeFailureEmailResultReporter(false, true, true, InvocationStatus.SUCCESS);
        assertFalse(r.shouldSendMessage());

        r = new FakeFailureEmailResultReporter(true, false, true, InvocationStatus.SUCCESS);
        assertTrue(r.shouldSendMessage());

        r = new FakeFailureEmailResultReporter(true, true, true, InvocationStatus.SUCCESS);
        assertTrue(r.shouldSendMessage());

        // When getInvocationStatus() is not SUCCESS, only send email when send-only-on-inv-failure
        // flag is set.
        r = new FakeFailureEmailResultReporter(false, false, false, InvocationStatus.FAILED);
        assertFalse(r.shouldSendMessage());

        r = new FakeFailureEmailResultReporter(false, true, false, InvocationStatus.FAILED);
        assertTrue(r.shouldSendMessage());

        r = new FakeFailureEmailResultReporter(true, false, false, InvocationStatus.FAILED);
        assertFalse(r.shouldSendMessage());

        r = new FakeFailureEmailResultReporter(true, true, false, InvocationStatus.FAILED);
        assertTrue(r.shouldSendMessage());

        // When getInvocationStatus() is not SUCCESS, only send email when send-only-on-inv-failure
        // flag is set.
        r = new FakeFailureEmailResultReporter(false, false, true, InvocationStatus.FAILED);
        assertFalse(r.shouldSendMessage());

        r = new FakeFailureEmailResultReporter(false, true, true, InvocationStatus.FAILED);
        assertTrue(r.shouldSendMessage());

        r = new FakeFailureEmailResultReporter(true, false, true, InvocationStatus.FAILED);
        assertTrue(r.shouldSendMessage());

        r = new FakeFailureEmailResultReporter(true, true, true, InvocationStatus.FAILED);
        assertTrue(r.shouldSendMessage());
    }
}
