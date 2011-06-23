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

import org.easymock.Capture;
import org.easymock.EasyMock;

import java.io.IOException;

import junit.framework.TestCase;

/**
 * Unit tests for {@link EmailResultReporter}.
 */
public class EmailResultReporterTest extends TestCase {
    private IEmail mMockMailer;
    private EmailResultReporter mEmailReporter;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mMockMailer = EasyMock.createMock(IEmail.class);
        mEmailReporter = new EmailResultReporter(mMockMailer);
    }

    /**
     * Test normal success case for {@link EmailResultReporter#invocationEnded(long)}.
     */
    public void testInvocationEnded_empty() throws IllegalArgumentException, IOException {
        mMockMailer.send((Message)EasyMock.anyObject());
        EasyMock.replay(mMockMailer);
        mEmailReporter.invocationStarted(new BuildInfo(888, "mytest", "mybuild"));
        mEmailReporter.addDestination("foo");
        mEmailReporter.invocationEnded(0);
        EasyMock.verify(mMockMailer);
    }

    /**
     * Test that no email is sent if
     * {@link EmailResultReporter#setSendOnlyOnInvocationFailure(boolean)} is set
     */
    public void testInvocationEnded_onFailure() throws IllegalArgumentException, IOException {
        mEmailReporter.setSendOnlyOnInvocationFailure(true);
        EasyMock.replay(mMockMailer);
        mEmailReporter.invocationStarted(new BuildInfo(888, "mytest", "mybuild"));
        mEmailReporter.addDestination("foo");
        mEmailReporter.invocationEnded(0);
        EasyMock.verify(mMockMailer);
    }

    /**
     * Test that email is sent if
     * {@link EmailResultReporter#setSendOnlyOnInvocationFailure(boolean)} is set and invocation
     * failed
     */
    public void testInvocationEnded_invFailure() throws IllegalArgumentException, IOException {
        Capture<Message> msgCapture = new Capture<Message>();
        mMockMailer.send(EasyMock.capture(msgCapture));
        mEmailReporter.setSendOnlyOnInvocationFailure(true);
        EasyMock.replay(mMockMailer);
        mEmailReporter.invocationStarted(new BuildInfo(888, "mytest", "mybuild"));
        mEmailReporter.addDestination("foo");
        mEmailReporter.invocationFailed(new BuildError("boot failed"));
        mEmailReporter.invocationEnded(0);
        EasyMock.verify(mMockMailer);
        Message capturedMessage = msgCapture.getValue();
        // ensure invocation stack trace is present
        assertTrue(capturedMessage.getBody().contains("BuildError"));
    }
}
