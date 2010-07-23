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
package com.android.tradefed.result;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.result.CollectingTestListener.TestStatus;

import junit.framework.TestCase;

/**
 *
 */
public class CollectingTestListenerTest extends TestCase {

    private CollectingTestListener mCollectingTestListener;

    /**
     * {@inheritDoc}
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mCollectingTestListener = new CollectingTestListener();
    }

    /**
     * Test the listener under normal test run.
     */
    public void testNormalRun() {
        mCollectingTestListener.testRunStarted(1);
        final TestIdentifier test = new TestIdentifier("FooTest", "testFoo");
        mCollectingTestListener.testStarted(test);
        mCollectingTestListener.testEnded(test);
        mCollectingTestListener.testRunEnded(0, null);
        assertTrue(mCollectingTestListener.isRunComplete());
        assertFalse(mCollectingTestListener.isRunFailure());
        assertEquals(1, mCollectingTestListener.getTestResults().size());
        assertEquals(TestStatus.PASSED,
                mCollectingTestListener.getTestResults().get(test).getStatus());
        assertTrue(mCollectingTestListener.getTests().contains(test));
    }

    /**
     * Test the listener where test run has failed.
     */
    public void testRunFailed() {
        mCollectingTestListener.testRunFailed("");
        assertTrue(mCollectingTestListener.isRunComplete());
        assertTrue(mCollectingTestListener.isRunFailure());
    }
}
