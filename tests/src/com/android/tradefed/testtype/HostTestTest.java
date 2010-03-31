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

import com.android.tradefed.device.ITestDevice;

import org.easymock.EasyMock;

import junit.framework.AssertionFailedError;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestListener;
import junit.framework.TestResult;
import junit.framework.TestSuite;

/**
 * Unit tests for {@link HostTest}.
 */
public class HostTestTest extends TestCase {

    private HostTest mHostTest;
    private TestListener mListener;
    private TestResult mResult;

    public static class SuccessTestCase extends TestCase {
        public SuccessTestCase() {
        }

        public SuccessTestCase(String name) {
            super(name);
        }

        public void testPass() {
        }

        public void testPass2() {
        }

        /** Override parent to do content based comparison - to allow for EasyMock matching */
        @Override
        public boolean equals(Object other) {
           if (other instanceof SuccessTestCase)  {
               SuccessTestCase otherTest = (SuccessTestCase)other;
               if (getName() == null && otherTest.getName() == null) {
                   return true;
               } else if (getName() != null) {
                   return getName().equals(otherTest.getName());
               }
           }
           return false;
        }

        @Override
        public int hashCode() {
            return getName().hashCode();
        }
    }

    public static class SuccessTestSuite extends TestSuite {
        public SuccessTestSuite() {
            super(SuccessTestCase.class);
        }
    }

    public static class SuccessDeviceTest extends DeviceTestCase {
        public SuccessDeviceTest() {
            super();
        }

        public SuccessDeviceTest(String name) {
            super(name);
        }

        public void testPass() {
        }

        /** Override parent to do content based comparison - to allow for EasyMock matching */
        @Override
        public boolean equals(Object other) {
           if (other instanceof SuccessDeviceTest)  {
               SuccessDeviceTest otherTest = (SuccessDeviceTest)other;
               if (getName() == null && otherTest.getName() == null) {
                   return true;
               } else if (getName() != null) {
                   return getName().equals(otherTest.getName());
               }
           }
           return false;
        }

        @Override
        public int hashCode() {
            return getName().hashCode();
        }
    }

    /** Non-public class; should fail to load. */
    private static class PrivateTest extends TestCase {
    }

    /** class without default constructor; should fail to load */
    public static class NoConstructorTest extends TestCase {
        public NoConstructorTest(String name) {
            super(name);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mHostTest = new HostTest();
        mListener = EasyMock.createMock(TestListener.class);
        //mListener = new MyTestListener();
        mResult = new TestResult();
        mResult.addListener(mListener);
    }

    /**
     * Test success case for {@link HostTest#run(TestResult)}, where test to run is a
     * {@link TestCase}.
     */
    public void testRun_testcase() {
        mHostTest.setClassName(SuccessTestCase.class.getName());
        SuccessTestCase test1 = new SuccessTestCase("testPass");
        SuccessTestCase test2 = new SuccessTestCase("testPass2");
        mListener.startTest(EasyMock.eq(test1));
        mListener.endTest(EasyMock.eq(test1));
        mListener.startTest(EasyMock.eq(test2));
        mListener.endTest(EasyMock.eq(test2));
        EasyMock.replay(mListener);
        mHostTest.run(mResult);
    }

    /**
     * Test success case for {@link HostTest#run(TestResult)}, where test to run is a
     * {@link TestSuite}.
     */
    public void testRun_testSuite() {
        mHostTest.setClassName(SuccessTestSuite.class.getName());
        SuccessTestCase test1 = new SuccessTestCase("testPass");
        SuccessTestCase test2 = new SuccessTestCase("testPass2");
        mListener.startTest(EasyMock.eq(test1));
        mListener.endTest(EasyMock.eq(test1));
        mListener.startTest(EasyMock.eq(test2));
        mListener.endTest(EasyMock.eq(test2));
        EasyMock.replay(mListener);
        mHostTest.run(mResult);
    }

    /**
     * Test success case for {@link HostTest#run(TestResult)}, where test to run is a
     * {@link TestCase} and methodName is set.
     */
    public void testRun_testMethod() {
        mHostTest.setClassName(SuccessTestCase.class.getName());
        mHostTest.setMethodName("testPass");
        SuccessTestCase test1 = new SuccessTestCase("testPass");
        mListener.startTest(EasyMock.eq(test1));
        mListener.endTest(EasyMock.eq(test1));
        EasyMock.replay(mListener);
        mHostTest.run(mResult);
    }

    /**
     * Test for {@link HostTest#run(TestResult)}, where className is not set.
     */
    public void testRun_missingClass() {
        try {
            mHostTest.run(mResult);
            fail("IllegalArgumentException not thrown");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    /**
     * Test for {@link HostTest#run(TestResult)}, for an invalid class.
     */
    public void testRun_invalidClass() {
        try {
            mHostTest.setClassName("foo");
            mHostTest.run(mResult);
            fail("IllegalArgumentException not thrown");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    /**
     * Test for {@link HostTest#run(TestResult)}, for a valid class that is not a {@link Test}.
     */
    public void testRun_notTestClass() {
        try {
            mHostTest.setClassName(String.class.getName());
            mHostTest.run(mResult);
            fail("IllegalArgumentException not thrown");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    /**
     * Test for {@link HostTest#run(TestResult)}, for a private class.
     */
    public void testRun_privateClass() {
        try {
            mHostTest.setClassName(PrivateTest.class.getName());
            mHostTest.run(mResult);
            fail("IllegalArgumentException not thrown");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    /**
     * Test for {@link HostTest#run(TestResult)}, for a test class with no default constructor.
     */
    public void testRun_noConstructorClass() {
        try {
            mHostTest.setClassName(NoConstructorTest.class.getName());
            mHostTest.run(mResult);
            fail("IllegalArgumentException not thrown");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    /**
     * Test for {@link HostTest#run(TestResult)}, for a {@link DeviceTest}.
     */
    public void testRun_deviceTest() {
        final ITestDevice device = EasyMock.createMock(ITestDevice.class);
        mHostTest.setClassName(SuccessDeviceTest.class.getName());
        mHostTest.setDevice(device);

        SuccessDeviceTest test1 = new SuccessDeviceTest("testPass");
        mListener.startTest(EasyMock.eq(test1));
        EasyMock.expectLastCall().andDelegateTo(new TestListener() {
            public void addError(Test test, Throwable t) {
            }

            public void addFailure(Test test, AssertionFailedError t) {
            }

            public void endTest(Test test) {
            }

            public void startTest(Test test) {
                assertEquals(device, ((DeviceTestCase)test).getDevice());
            }

        });
        mListener.endTest(EasyMock.eq(test1));
        EasyMock.replay(mListener);
        mHostTest.run(mResult);
    }

    /**
     * Test for {@link HostTest#run(TestResult)}, for a {@link DeviceTest} where no device has been
     * provided.
     */
    public void testRun_missingDevice() {
        mHostTest.setClassName(SuccessDeviceTest.class.getName());
        try {
            mHostTest.run(mResult);
            fail("expected IllegalArgumentException not thrown");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }
}
