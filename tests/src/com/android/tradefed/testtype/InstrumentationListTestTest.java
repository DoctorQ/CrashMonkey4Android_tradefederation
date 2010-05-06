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
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.result.ITestInvocationListener;

import org.easymock.EasyMock;

import java.util.ArrayList;
import java.util.Collection;

import junit.framework.TestCase;

/**
 * Unit tests for {@link InstrumentationListTest}.
 */
public class InstrumentationListTestTest extends TestCase {

    /** The {@link InstrumentationListTest} under test, with all dependencies mocked out */
    private InstrumentationListTest mInstrumentationListTest;

    // The mock objects.
    private ITestDevice mMockTestDevice;
    private ITestInvocationListener mMockListener;
    private MockInstrumentationTest mMockInstrumentationTest;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mMockTestDevice = EasyMock.createMock(ITestDevice.class);
        mMockListener = EasyMock.createMock(ITestInvocationListener.class);
        mMockInstrumentationTest = new MockInstrumentationTest();
    }

    /**
     * Test normal run scenario with a single test.
     */
    public void testRun() throws DeviceNotAvailableException {
        final String packageName = "com.foo";
        final TestIdentifier test = new TestIdentifier("FooTest", "testFoo");
        final Collection<TestIdentifier> testList = new ArrayList<TestIdentifier>(1);
        testList.add(test);
        mInstrumentationListTest = new InstrumentationListTest(packageName, "foo", testList) {
            @Override
            InstrumentationTest createInstrumentationTest() {
                return mMockInstrumentationTest;
            }
        };
        EasyMock.replay(mMockListener, mMockTestDevice);
        mInstrumentationListTest.setDevice(mMockTestDevice);
        mInstrumentationListTest.run(mMockListener);
        assertEquals(mMockListener, mMockInstrumentationTest.getListener());
        assertEquals(mMockTestDevice, mMockInstrumentationTest.getDevice());
        assertEquals(test.getClassName(), mMockInstrumentationTest.getClassName());
        assertEquals(test.getTestName(), mMockInstrumentationTest.getMethodName());
    }

    /**
     * Test that IllegalArgumentException is thrown when attempting run without setting device.
     */
    public void testRun_noDevice() throws DeviceNotAvailableException {
        mInstrumentationListTest = new InstrumentationListTest("foo", "foo",
                new ArrayList<TestIdentifier>()) {
            @Override
            InstrumentationTest createInstrumentationTest() {
                return mMockInstrumentationTest;
            }
        };
        mInstrumentationListTest.setDevice(null);
        EasyMock.replay(mMockListener);
        try {
            mInstrumentationListTest.run(mMockListener);
            fail("IllegalArgumentException not thrown");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }
}
