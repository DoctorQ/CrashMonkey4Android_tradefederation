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

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestResult;

/**
 * A test runner for host based tests
 */
public class HostTest implements IDeviceTest {

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

    /**
     * {@inheritDoc}
     */
    public void run(TestResult result) {
        if (mClassName == null) {
            throw new IllegalArgumentException("Missing Test class name");
        }
        if (mDevice == null) {
            throw new IllegalArgumentException("Missing device");
        }

        Test test = loadTest(mClassName);
        if (test instanceof IDeviceTest) {
            ((IDeviceTest)test).setDevice(mDevice);
        }
        if (test instanceof TestCase && mMethodName != null) {
            TestCase testCase = (TestCase)test;
            testCase.setName(mMethodName);
        }
        test.run(result);
    }

    private Test loadTest(String className) throws IllegalArgumentException {
        try {
            Class<?> classObj = Class.forName(className);
            Object testObject = classObj.newInstance();
            if (!(testObject instanceof Test)) {
                throw new IllegalArgumentException(String.format("%s is not a Test", className));
            }
            return (Test)testObject;
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException(String.format("Could not load Test class %s",
                    className), e);
        } catch (InstantiationException e) {
            throw new IllegalArgumentException(String.format("Could not load Test class %s",
                    className), e);
        } catch (IllegalAccessException e) {
            throw new IllegalArgumentException(String.format("Could not load Test class %s",
                    className), e);
        }
    }
}
