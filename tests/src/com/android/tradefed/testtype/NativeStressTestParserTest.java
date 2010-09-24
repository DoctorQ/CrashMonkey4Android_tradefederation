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

import com.android.ddmlib.testrunner.ITestRunListener;

import org.easymock.Capture;
import org.easymock.EasyMock;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import junit.framework.TestCase;

/**
 * Unit tests for {@link NativeStressTestParser}.
 */
public class NativeStressTestParserTest extends TestCase {

    private static final String RUN_NAME = "run-name";
    private ITestRunListener mMockListener;
    private NativeStressTestParser mParser;
    private Capture<Map<String, String>> mCapturedMetricMap;

    /**
     * {@inheritDoc}
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mMockListener = EasyMock.createMock(ITestRunListener.class);
        Collection<ITestRunListener> listeners = new ArrayList<ITestRunListener>(1);
        listeners.add(mMockListener);
        mParser = new NativeStressTestParser(RUN_NAME, listeners);
        mCapturedMetricMap = new Capture<Map<String, String>>();
        // expect this call
        mMockListener.testRunStarted(RUN_NAME, 0);
        mMockListener.testRunEnded(EasyMock.anyLong(), EasyMock.capture(mCapturedMetricMap));
        EasyMock.replay(mMockListener);
    }

    /**
     * Test a run with no content.
     * <p/>
     * Expect 0 iterations to be reported
     */
    public void testParse_empty() {
        mParser.processNewLines(new String[] {});
        mParser.done();
        verifyExpectedIterations(0);
    }

    /**
     * Test a run with garbage content.
     * <p/>
     * Expect 0 iterations to be reported
     */
    public void testParse_garbage() {
        mParser.processNewLines(new String[] {"blah blah", "more blah"});
        mParser.done();
        verifyExpectedIterations(0);
    }

    /**
     * Test a run with valid one iteration.
     * <p/>
     * Expect 1 iterations to be reported
     */
    public void testParse_oneIteration() {
        mParser.processNewLines(new String[] {"==== pass 0", "Successfully completed 1 passes"});
        mParser.done();
        verifyExpectedIterations(1);
    }

    /**
     * Test that iteration data with varying whitespace is parsed successfully.
     * <p/>
     * Expect 2 iterations to be reported
     */
    public void testParse_whitespace() {
        mParser.processNewLines(new String[] {"====pass0", "====   pass   1",
                "Successfully completed 2 passes"});
        mParser.done();
        verifyExpectedIterations(2);
    }

    /**
     * Test that non-sequential iteration data is handled
     * <p/>
     * Expect the missing iterations to be ignored: ie the final iteration count will be reported
     */
    public void testParse_skippedIteration() {
        mParser.processNewLines(new String[] {"==== pass 1", "==== pass 49",
                "Successfully completed 50 passes"});
        mParser.done();
        verifyExpectedIterations(50);
    }

    /**
     * Test that a run where 'Successfully completed' output is missing.
     * <p/>
     * Expect the last valid iteration count will be reported
     */
    public void testParse_missingComplete() {
        mParser.processNewLines(new String[] {"==== pass 0", "==== pass 1"});
        mParser.done();
        verifyExpectedIterations(1);
    }

    /**
     * Test that an invalid iteration value is ignored
     * <p/>
     * Expect the last valid iteration count will be reported
     */
    public void testParse_invalidIterationValue() {
        mParser.processNewLines(new String[] {"==== pass 0", "==== pass 1", "==== pass b"});
        mParser.done();
        verifyExpectedIterations(1);
    }

    /**
     * Verify the metrics passed to {@link ITestRunListener#testRunEnded(long, Map)}
     */
    private void verifyExpectedIterations(int iterations) {
        EasyMock.verify(mMockListener);
        Map<String, String> receivedMetrics = mCapturedMetricMap.getValue();
        // expect AVG_ITERATION_TIME_KEY and ITERATION_KEY metrics
        assertEquals(2, receivedMetrics.size());
        assertEquals(Integer.toString(iterations), receivedMetrics.get(
                NativeStressTestParser.ITERATION_KEY));
    }
}
