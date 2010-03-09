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

import com.android.ddmlib.IDevice;
import com.android.ddmlib.testrunner.IRemoteAndroidTestRunner;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.result.ITestInvocationListener;

import org.easymock.EasyMock;

import junit.framework.TestCase;

/**
 * Unit tests for {@link InstrumentationTest}
 */
public class InstrumentationTestTest extends TestCase {

    private static final String TEST_PACKAGE_VALUE = "com.foo";

    /** The {@link InstrumentationTest} under test, with all dependencies mocked out */
    private InstrumentationTest mInstrumentationTest;

    // The mock objects.
    private IDevice mMockIDevice;
    private ITestDevice mMockTestDevice;
    private IRemoteAndroidTestRunner mMockRemoteRunner;
    private ITestInvocationListener mMockListener;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mMockIDevice = EasyMock.createMock(IDevice.class);
        mMockTestDevice = EasyMock.createMock(ITestDevice.class);
        EasyMock.expect(mMockTestDevice.getIDevice()).andReturn(mMockIDevice);
        mMockRemoteRunner = EasyMock.createMock(IRemoteAndroidTestRunner.class);
        mMockListener = EasyMock.createMock(ITestInvocationListener.class);

        mInstrumentationTest = new InstrumentationTest() {
            @Override
            IRemoteAndroidTestRunner createRemoteAndroidTestRunner(String packageName, IDevice device) {
                return mMockRemoteRunner;
            }
        };
       mInstrumentationTest.setPackageName(TEST_PACKAGE_VALUE);
       mInstrumentationTest.setDevice(mMockTestDevice);
    }

    /**
     * Test normal run scenario with a single test result.
     */
    public void testRun() {
        mMockRemoteRunner.setTestPackageName(TEST_PACKAGE_VALUE);
        mMockRemoteRunner.run(mMockListener);
        EasyMock.replay(mMockRemoteRunner);
        mInstrumentationTest.run(mMockListener);
    }

    /**
     * Test normal run scenario with a test class specified.
     */
    public void testRun_class() {
        final String className = "FooTest";
        mMockRemoteRunner.setTestPackageName(TEST_PACKAGE_VALUE);
        mMockRemoteRunner.setClassName(className);
        mMockRemoteRunner.run(mMockListener);
        EasyMock.replay(mMockRemoteRunner);
        mInstrumentationTest.setClassName(className);
        mInstrumentationTest.run(mMockListener);
    }

    /**
     * Test normal run scenario with a test class and method specified.
     */
    public void testRun_classMethod() {
        final String className = "FooTest";
        final String methodName = "testFoo";
        mMockRemoteRunner.setTestPackageName(TEST_PACKAGE_VALUE);
        mMockRemoteRunner.setMethodName(className, methodName);
        mMockRemoteRunner.run(mMockListener);
        EasyMock.replay(mMockRemoteRunner);
        mInstrumentationTest.setClassName(className);
        mInstrumentationTest.setMethodName(methodName);
        mInstrumentationTest.run(mMockListener);
    }

    /**
     * Test that IllegalArgumentException is thrown when attempting run without setting package.
     */
    public void testRun_noPackage() {
        mInstrumentationTest.setPackageName(null);
        EasyMock.replay(mMockRemoteRunner);
        try {
            mInstrumentationTest.run(mMockListener);
            fail("IllegalArgumentException not thrown");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    /**
     * Test that IllegalArgumentException is thrown when attempting run without setting device.
     */
    public void testRun_noDevice() {
        mInstrumentationTest.setDevice(null);
        EasyMock.replay(mMockRemoteRunner);
        try {
            mInstrumentationTest.run(mMockListener);
            fail("IllegalArgumentException not thrown");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }
}
