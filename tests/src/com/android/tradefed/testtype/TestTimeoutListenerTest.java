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
package com.android.tradefed.testtype;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.testtype.TestTimeoutListener.ITimeoutCallback;

import org.easymock.EasyMock;

import junit.framework.TestCase;

/**
 * Unit tests for {@link TestTimeoutListener}.
 */
public class TestTimeoutListenerTest extends TestCase {

    /**
     * Test normal case test start and end without timeout.
     */
    public void testEndedNormally() {
        ITimeoutCallback callback = EasyMock.createStrictMock(ITimeoutCallback.class);
        TestTimeoutListener listener = new TestTimeoutListener(1000, callback);
        final TestIdentifier test = new TestIdentifier("Foo", "testFoo");
        listener.testRunStarted(1);
        listener.testStarted(test);
        listener.testEnded(test);
        listener.testRunEnded(0, null);
    }

    /**
     * Test that a failed run cancels the timer.
     */
    public void testRunFailed() throws InterruptedException {
        ITimeoutCallback callback = EasyMock.createStrictMock(ITimeoutCallback.class);
        TestTimeoutListener listener = new TestTimeoutListener(100, callback);
        final TestIdentifier test = new TestIdentifier("Foo", "testFoo");
        listener.testRunStarted(1);
        listener.testStarted(test);
        listener.testRunFailed("");
        // wait for longer than original timeout, to ensure callback is not called
        Thread.sleep(200);
    }

    /**
     * Test that callback is called.
     */
    public void testTimeout() throws InterruptedException {
        ITimeoutCallback callback = EasyMock.createStrictMock(ITimeoutCallback.class);
        TestTimeoutListener listener = new TestTimeoutListener(100, callback);
        final TestIdentifier test = new TestIdentifier("Foo", "testFoo");
        callback.testTimeout(test);
        EasyMock.replay(callback);
        listener.testRunStarted(1);
        listener.testStarted(test);
        // wait for longer than original timeout, to ensure callback is not called
        Thread.sleep(200);
    }
}
