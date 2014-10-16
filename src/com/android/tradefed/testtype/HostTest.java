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
import com.android.tradefed.config.Option.Importance;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.result.ITestInvocationListener;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * A test runner for JUnit host based tests
 */
@OptionClass(alias = "host")
public class HostTest implements IDeviceTest, IRemoteTest {

    @Option(name="class", description="The JUnit Test to run.",
            importance = Importance.IF_UNSET)
    private String mClassName;

    @Option(name="method", description="The JUnit TestCase method to run.",
            importance = Importance.IF_UNSET)
    private String mMethodName;

    private ITestDevice mDevice = null;

    /**
     * {@inheritDoc}
     */
    @Override
    public ITestDevice getDevice() {
        return mDevice;
    }

    /**
     * {@inheritDoc}
     */
    @Override
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
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        if (mClassName == null) {
            throw new IllegalArgumentException("Missing Test class name");
        }
        Class<?> classObj = loadTestClass(mClassName);
        Object testObj = loadObject(classObj);
        if (testObj instanceof IDeviceTest) {
            if (mDevice == null) {
                throw new IllegalArgumentException("Missing device");
            }
            ((IDeviceTest)testObj).setDevice(mDevice);
        }
        if (testObj instanceof Test) {
            Test test = (Test)testObj;
            if (test instanceof TestCase) {
                if (mMethodName != null) {
                    // run a single test method
                    ((TestCase)test).setName(mMethodName);
                } else if (!(test instanceof DeviceTestCase)) {
                    // wrap the test in a suite, because the JUnit TestCase.run implementation can
                    // only run a single method
                    TestSuite testSuite = new TestSuite(classObj);
                    test = testSuite;
                }
            }
            JUnitRunUtil.runTest(listener, test);
        } else if (testObj instanceof IRemoteTest) {
            ((IRemoteTest)testObj).run(listener);
        } else {
            throw new IllegalArgumentException(String.format("%s is not a test", mClassName));
        }
    }

    private Class<?> loadTestClass(String className) throws IllegalArgumentException  {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException(String.format("Could not load Test class %s",
                    className), e);
        }
    }

    private Object loadObject(Class<?> classObj) throws IllegalArgumentException {
        final String className = classObj.getName();
        try {
            Object testObject = classObj.newInstance();
            return testObject;
        } catch (InstantiationException e) {
            throw new IllegalArgumentException(String.format("Could not load Test class %s",
                    className), e);
        } catch (IllegalAccessException e) {
            throw new IllegalArgumentException(String.format("Could not load Test class %s",
                    className), e);
        }
    }
}
