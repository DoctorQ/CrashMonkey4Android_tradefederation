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

import com.android.tradefed.config.Option;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.result.ITestInvocationListener;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * A test runner for JUnit host based tests
 */
public class HostTest implements IDeviceTest, IRemoteTest {

    @Option(name="class", description="The JUnit Test to run")
    private String mClassName;

    @Option(name="method", description="The JUnit TestCase method to run")
    private String mMethodName;

    private ITestDevice mDevice = null;

    /**
     * {@inheritDoc}
     */
    public ITestDevice getDevice() {
        return mDevice;
    }

    /**
     * {@inheritDoc}
     */
    public void setDevice(ITestDevice device) {
        mDevice = device;
    }

    /**
     * {@inheritDoc}
     */
    public int countTestCases() {
        // TODO implement this
        return 1;
    }

    void setClassName(String className) {
        mClassName = className;
    }

    void setMethodName(String methodName) {
        mMethodName = methodName;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void run(ITestInvocationListener listener) {
        if (mClassName == null) {
            throw new IllegalArgumentException("Missing Test class name");
        }
        Class<?> classObj = loadTestClass(mClassName);
        Test test = loadTest(classObj);
        if (test instanceof IDeviceTest) {
            if (mDevice == null) {
                throw new IllegalArgumentException("Missing device");
            }
            ((IDeviceTest)test).setDevice(mDevice);
        }
        if (test instanceof TestCase) {
            TestCase testCase = (TestCase)test;
            if (mMethodName != null) {
                // run a single test method
                testCase.setName(mMethodName);
            } else if (!(test instanceof DeviceTestCase)) {
                // wrap the test in a suite, because the JUnit TestCase.run implementation can
                // only run a single method
                TestSuite testSuite = new TestSuite(classObj);
                test = testSuite;
            }
        }
        JUnitRunUtil.runTest(listener, test);
    }

    private Class<?> loadTestClass(String className) throws IllegalArgumentException  {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException(String.format("Could not load Test class %s",
                    className), e);
        }
    }

    private Test loadTest(Class<?> classObj) throws IllegalArgumentException {
        final String className = classObj.getName();
        try {
            Object testObject = classObj.newInstance();
            if (!(testObject instanceof Test)) {
                throw new IllegalArgumentException(String.format("%s is not a Test", className));
            }
            return (Test)testObject;
        } catch (InstantiationException e) {
            throw new IllegalArgumentException(String.format("Could not load Test class %s",
                    className), e);
        } catch (IllegalAccessException e) {
            throw new IllegalArgumentException(String.format("Could not load Test class %s",
                    className), e);
        }
    }
}
