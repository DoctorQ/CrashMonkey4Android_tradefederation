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
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.JUnitToInvocationResultForwarder;

import junit.framework.TestCase;
import junit.framework.TestResult;

import org.easymock.EasyMock;

import java.util.Collections;

/**
 * Unit tests for {@link DeviceTestCase}.
 */
public class DeviceTestCaseTest extends TestCase {

    public static class MockTest extends DeviceTestCase {

        public void test1() {};
        public void test2() {};
    }

    /**
     * Verify that calling run on a DeviceTestCase will run all test methods.
     */
    @SuppressWarnings("unchecked")
    public void testRun_suite() {
        MockTest test = new MockTest();
        TestResult result = new TestResult();
        // create a mock ITestInvocationListener, because results are easier to verify
        ITestInvocationListener listener = EasyMock.createMock(ITestInvocationListener.class);
        result.addListener(new JUnitToInvocationResultForwarder(listener));

        final TestIdentifier test1 = new TestIdentifier(MockTest.class.getName(), "test1");
        final TestIdentifier test2 = new TestIdentifier(MockTest.class.getName(), "test2");
        listener.testStarted(test1);
        listener.testEnded(test1, Collections.EMPTY_MAP);
        listener.testStarted(test2);
        listener.testEnded(test2, Collections.EMPTY_MAP);
        EasyMock.replay(listener);

        test.run(result);
        EasyMock.verify(listener);
    }

    /**
     * Regression test to verify a single test can still be run.
     */
    @SuppressWarnings("unchecked")
    public void testRun_singleTest() {
        MockTest test = new MockTest();
        test.setName("test1");
        TestResult result = new TestResult();
        // create a mock ITestInvocationListener, because results are easier to verify
        ITestInvocationListener listener = EasyMock.createMock(ITestInvocationListener.class);
        result.addListener(new JUnitToInvocationResultForwarder(listener));

        final TestIdentifier test1 = new TestIdentifier(MockTest.class.getName(), "test1");
        listener.testStarted(test1);
        listener.testEnded(test1, Collections.EMPTY_MAP);
        EasyMock.replay(listener);

        test.run(result);
        EasyMock.verify(listener);
    }
}
