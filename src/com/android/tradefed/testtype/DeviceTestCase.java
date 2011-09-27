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

import com.android.ddmlib.Log;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestResult;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Vector;

/**
 * Helper JUnit test case that provides the {@link IRemoteTest} and {@link IDeviceTest} services.
 * <p/>
 * This is useful if you want to implement tests that follow the JUnit pattern of defining tests,
 * and still have full support for other tradefed features such as {@link Option}s
 */
public class DeviceTestCase extends TestCase implements IDeviceTest, IRemoteTest {

    private static final String LOG_TAG = "DeviceTestCase";
    private ITestDevice mDevice;

    @Option(name = "method", description = "run a specific test method.")
    private String mMethodName = null;

    public DeviceTestCase() {
        super();
    }

    public DeviceTestCase(String name) {
        super(name);
    }

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
    @Override
    public void run(ITestInvocationListener listener) {
        if (getName() == null && mMethodName != null) {
            setName(mMethodName);
        }
        JUnitRunUtil.runTest(listener, this);
    }

    /**
     * Override parent method to run all test methods if test method to run is null.
     * <p/>
     * The JUnit framework only supports running all the tests in a TestCase by wrapping it in a
     * TestSuite. Unfortunately with this mechanism callers can't control the lifecycle of their
     * own test cases, which makes it impossible to do things like have the tradefed configuration
     * framework inject options into a Test Case.
     */
    @Override
    public void run(TestResult result) {
        // check if test method to run aka name is null
        if (getName() == null) {
            Collection<String> testMethodNames = getTestMethodNames(this.getClass());
            for (String methodName : testMethodNames) {
                setName(methodName);
                CLog.d("Running %s#%s()", this.getClass().getName(), methodName);
                super.run(result);
            }
        } else {
            CLog.d("Running %s#%s()", this.getClass().getName(), getName());
            super.run(result);
        }
        // TODO: customize this method further to support aborting if DeviceNotAvailableException
        // is thrown
    }

    /**
     * Get list of test methods to run
     * @param class1
     * @return
     */
    private Collection<String> getTestMethodNames(Class<? extends TestCase> theClass) {
        // Unfortunately {@link TestSuite} doesn't expose the functionality to find all test*
        // methods,
        // so needed to copy and paste code from TestSuite
        Class<?> superClass = theClass;
        Vector<String> names = new Vector<String>();
        while (Test.class.isAssignableFrom(superClass)) {
            Method[] methods = superClass.getDeclaredMethods();
            for (int i = 0; i < methods.length; i++) {
                addTestMethod(methods[i], names, theClass);
            }
            superClass = superClass.getSuperclass();
        }
        if (names.size() == 0) {
            Log.w(LOG_TAG, String.format("No tests found in %s", theClass.getName()));
        }
        return names;
    }

    private void addTestMethod(Method m, Vector<String> names, Class<?> theClass) {
        String name = m.getName();
        if (names.contains(name)) {
            return;
        }
        if (!isPublicTestMethod(m)) {
            if (isTestMethod(m)) {
                Log.w(LOG_TAG, String.format("Test method isn't public: %s", m.getName()));
            }
            return;
        }
        names.addElement(name);
    }

    private boolean isPublicTestMethod(Method m) {
        return isTestMethod(m) && Modifier.isPublic(m.getModifiers());
    }

    private boolean isTestMethod(Method m) {
        String name = m.getName();
        Class<?>[] parameters = m.getParameterTypes();
        Class<?> returnType = m.getReturnType();
        return parameters.length == 0 && name.startsWith("test") && returnType.equals(Void.TYPE);
     }
}
