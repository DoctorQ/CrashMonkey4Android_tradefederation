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
import com.android.tradefed.result.TestResult.TestStatus;

import junit.framework.TestCase;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;

/**
 * Unit tests for {@link CollectingTestListener}.
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
     * Test the listener under a single normal test run.
     */
    public void testSingleRun() {
        final TestIdentifier test = injectTestRun("run", "testFoo");
        TestRunResult runResult = mCollectingTestListener.getCurrentRunResults();
        assertTrue(runResult.isRunComplete());
        assertFalse(runResult.isRunFailure());
        assertEquals(1, mCollectingTestListener.getNumTotalTests());
        assertEquals(TestStatus.PASSED,
                runResult.getTestResults().get(test).getStatus());
    }

    /**
     * Test the listener where test run has failed.
     */
    public void testRunFailed() {
        mCollectingTestListener.testRunStarted("foo", 1);
        mCollectingTestListener.testRunFailed("");
        TestRunResult runResult = mCollectingTestListener.getCurrentRunResults();
        assertTrue(runResult.isRunComplete());
        assertTrue(runResult.isRunFailure());
    }

    /**
     * Test the listener when invocation is composed of two test runs.
     */
    public void testTwoRuns() {
        final TestIdentifier test1 = injectTestRun("run1", "testFoo1");
        final TestIdentifier test2 = injectTestRun("run2", "testFoo2");
        assertEquals(2, mCollectingTestListener.getNumTotalTests());
        assertEquals(2, mCollectingTestListener.getNumPassedTests());
        assertEquals(2, mCollectingTestListener.getRunResults().size());
        Iterator<TestRunResult> runIter = mCollectingTestListener.getRunResults().iterator();
        final TestRunResult runResult1 = runIter.next();
        final TestRunResult runResult2 = runIter.next();

        assertEquals("run1", runResult1.getName());
        assertEquals("run2", runResult2.getName());
        assertEquals(TestStatus.PASSED,
                runResult1.getTestResults().get(test1).getStatus());
        assertEquals(TestStatus.PASSED,
                runResult2.getTestResults().get(test2).getStatus());
    }

    /**
     * Test the listener when invocation is composed of a re-executed test run.
     */
    public void testReRun() {
        final TestIdentifier test1 = injectTestRun("run", "testFoo1");
        final TestIdentifier test2 = injectTestRun("run", "testFoo2");
        assertEquals(2, mCollectingTestListener.getNumTotalTests());
        assertEquals(2, mCollectingTestListener.getNumPassedTests());
        assertEquals(1, mCollectingTestListener.getRunResults().size());
        TestRunResult runResult = mCollectingTestListener.getCurrentRunResults();
        assertEquals(2, runResult.getNumPassedTests());
        assertTrue(runResult.getTests().contains(test1));
        assertTrue(runResult.getTests().contains(test2));
    }

    /**
     * Injects a single test run with 1 passed test into the {@link CollectingTestListener} under
     * test
     * @return the {@link TestIdentifier} of added test
     */
    private TestIdentifier injectTestRun(String runName, String testName) {
        Map<String, String> emptyMap = Collections.emptyMap();
        mCollectingTestListener.testRunStarted(runName, 1);
        final TestIdentifier test = new TestIdentifier("FooTest", testName);
        mCollectingTestListener.testStarted(test);
        mCollectingTestListener.testEnded(test, emptyMap);
        mCollectingTestListener.testRunEnded(0, emptyMap);
        return test;
    }
}
