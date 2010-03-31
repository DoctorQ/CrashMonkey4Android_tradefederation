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
import com.android.ddmlib.testrunner.ITestRunListener;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.ddmlib.testrunner.ITestRunListener.TestFailure;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.StubTestDevice;
import com.android.tradefed.result.ITestInvocationListener;

import org.easymock.EasyMock;

import java.util.Collection;

import junit.framework.TestCase;

/**
 * Unit tests for {@link InstrumentationTest}
 */
public class InstrumentationTestTest extends TestCase {

    private static final String TEST_PACKAGE_VALUE = "com.foo";
    private static final String TEST_RUNNER_VALUE = ".FooRunner";

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
        mMockTestDevice = EasyMock.createNiceMock(ITestDevice.class);
        EasyMock.expect(mMockTestDevice.getIDevice()).andReturn(mMockIDevice);
        mMockRemoteRunner = EasyMock.createMock(IRemoteAndroidTestRunner.class);
        mMockListener = EasyMock.createMock(ITestInvocationListener.class);

        mInstrumentationTest = new InstrumentationTest() {
            @Override
            IRemoteAndroidTestRunner createRemoteAndroidTestRunner(String packageName,
                    String runnerName, IDevice device) {
                return mMockRemoteRunner;
            }
        };
       mInstrumentationTest.setPackageName(TEST_PACKAGE_VALUE);
       mInstrumentationTest.setRunnerName(TEST_RUNNER_VALUE);
       mInstrumentationTest.setDevice(mMockTestDevice);
       // default to no rerun, for simplicity
       mInstrumentationTest.setRerunMode(false);
    }

    /**
     * Test normal run scenario with a single test result.
     */
    @SuppressWarnings("unchecked")
    public void testRun() throws Exception {
        mMockRemoteRunner.setTestPackageName(TEST_PACKAGE_VALUE);
        mMockTestDevice.runInstrumentationTests(EasyMock.eq(mMockRemoteRunner),
                (Collection<ITestRunListener>)EasyMock.anyObject());
        // verify the mock listener is passed through to the runner
        EasyMock.expectLastCall().andDelegateTo(new StubTestDevice() {
            @Override
            public void runInstrumentationTests(IRemoteAndroidTestRunner runner,
                    Collection<ITestRunListener> listeners) throws DeviceNotAvailableException {
                assertTrue(listeners.contains(mMockListener));
            }
        });
        EasyMock.replay(mMockRemoteRunner);
        EasyMock.replay(mMockTestDevice);
        mInstrumentationTest.run(mMockListener);
    }

    /**
     * Test normal run scenario with a test class specified.
     */
    @SuppressWarnings("unchecked")
    public void testRun_class() throws Exception {
        final String className = "FooTest";
        mMockRemoteRunner.setTestPackageName(TEST_PACKAGE_VALUE);
        mMockRemoteRunner.setClassName(className);
        mMockTestDevice.runInstrumentationTests(EasyMock.eq(mMockRemoteRunner),
                (Collection<ITestRunListener>)EasyMock.anyObject());
        EasyMock.replay(mMockRemoteRunner);
        EasyMock.replay(mMockTestDevice);
        mInstrumentationTest.setClassName(className);
        mInstrumentationTest.run(mMockListener);
    }

    /**
     * Test normal run scenario with a test class and method specified.
     */
    @SuppressWarnings("unchecked")
    public void testRun_classMethod() throws Exception {
        final String className = "FooTest";
        final String methodName = "testFoo";
        mMockRemoteRunner.setTestPackageName(TEST_PACKAGE_VALUE);
        mMockRemoteRunner.setMethodName(className, methodName);
        mMockTestDevice.runInstrumentationTests(EasyMock.eq(mMockRemoteRunner),
                (Collection<ITestRunListener>)EasyMock.anyObject());
        EasyMock.replay(mMockRemoteRunner);
        EasyMock.replay(mMockTestDevice);
        mInstrumentationTest.setClassName(className);
        mInstrumentationTest.setMethodName(methodName);
        mInstrumentationTest.run(mMockListener);
    }

    /**
     * Test that IllegalArgumentException is thrown when attempting run without setting package.
     */
    public void testRun_noPackage() throws Exception {
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
    public void testRun_noDevice() throws Exception {
        mInstrumentationTest.setDevice(null);
        EasyMock.replay(mMockRemoteRunner);
        try {
            mInstrumentationTest.run(mMockListener);
            fail("IllegalArgumentException not thrown");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    /**
     * Test a test run when a test times out.
     */
    @SuppressWarnings("unchecked")
    public void testRun_timeout() throws Exception {
        final long timeout = 1000;
        mInstrumentationTest.setTestTimeout(timeout);
        mMockRemoteRunner.setTestPackageName(TEST_PACKAGE_VALUE);
        mMockTestDevice.runInstrumentationTests(EasyMock.eq(mMockRemoteRunner),
                (Collection<ITestRunListener>)EasyMock.anyObject());
        mMockRemoteRunner.cancel();
        final TestIdentifier test = new TestIdentifier("FooTest", "testFoo");
        mMockListener.testFailed(EasyMock.eq(TestFailure.ERROR), EasyMock.eq(test),
                (String)EasyMock.anyObject());
        mMockListener.testRunFailed(String.format(InstrumentationTest.TIMED_OUT_MSG, timeout));
        EasyMock.replay(mMockRemoteRunner);
        EasyMock.replay(mMockListener);
        EasyMock.replay(mMockTestDevice);
        mInstrumentationTest.run(mMockListener);
        mInstrumentationTest.testTimeout(test);
    }

    /**
     * Test the rerun mode when test run fails.
     */
    @SuppressWarnings("unchecked")
    public void testRun_rerun() throws Exception {
        mMockRemoteRunner.setTestPackageName(TEST_PACKAGE_VALUE);
        // TODO: add verification for TEST_RUNNER_VALUE
        final TestIdentifier test = new TestIdentifier("FooTest", "testFoo");
        mMockTestDevice.runInstrumentationTests(EasyMock.eq(mMockRemoteRunner),
                (Collection<ITestRunListener>)EasyMock.anyObject());
        mMockRemoteRunner.cancel();
        mMockListener.testFailed(EasyMock.eq(TestFailure.ERROR), EasyMock.eq(test),
                (String)EasyMock.anyObject());
        mMockListener.testRunFailed((String)EasyMock.anyObject());
        EasyMock.replay(mMockRemoteRunner);
        EasyMock.replay(mMockListener);
        EasyMock.replay(mMockTestDevice);
        mInstrumentationTest.run(mMockListener);
        mInstrumentationTest.testTimeout(test);
    }

    /**
     * Test that IllegalArgumentException is thrown if an invalid test size is provided.
     */
    public void testRun_badTestSize() throws Exception {
        mInstrumentationTest.setTestSize("foo");
        EasyMock.replay(mMockRemoteRunner);
        try {
            mInstrumentationTest.run(mMockListener);
            fail("IllegalArgumentException not thrown");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }
}
