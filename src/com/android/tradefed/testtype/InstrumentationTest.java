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
import com.android.ddmlib.testrunner.RemoteAndroidTestRunner;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.util.RunUtil;

import junit.framework.TestResult;

/**
 * A Test that runs an instrumentation test package on given device.
 */
public class InstrumentationTest implements IDeviceTest, IRemoteTest {

    @Option(name = "package", shortName = 'p',
            description="The manifest package name of the Android test application to run")
    private String mPackageName = null;

    @Option(name = "class", shortName = 'c',
            description="The test class name to run")
    private String mTestClassName = null;

    @Option(name = "method", shortName = 'm',
            description="The test method name to run.")
    private String mTestMethodName = null;

    @Option(name = "timeout",
            description="Aborts the test run if it takes longer than the specified number of "
            + " milliseconds ")
    private long mTestRunTimeout = 60 * 60 * 1000;  // default to 1 hour

    private ITestDevice mDevice = null;


    /**
     * {@inheritDoc}
     */
    public void setDevice(ITestDevice device) {
        mDevice = device;
    }

    /**
     * Set the Android manifest package to run.
     */
    void setPackageName(String packageName) {
        mPackageName = packageName;
    }

    /**
     * Optionally, set the test class name to run.
     */
    void setClassName(String testClassName) {
        mTestClassName = testClassName;
    }

    /**
     * Optionally, set the test method to run.
     */
    void setMethodName(String testMethodName) {
        mTestMethodName = testMethodName;
    }

    /**
     * Optionally, set the maximum time for the test run.
     */
    void setRunTimeout(long timeout) {
        mTestRunTimeout = timeout;
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
    public int countTestCases() {
        // TODO: not sure we even want to support this
        // a possible implementation is to issue a adb shell am instrument -e count command when
        // this is first called and cache the result
        throw new UnsupportedOperationException();
    }

    /**
     * @return the {@link IRemoteAndroidTestRunner} to use.
     */
    IRemoteAndroidTestRunner createRemoteAndroidTestRunner(String packageName, IDevice device) {
        return new RemoteAndroidTestRunner(packageName, device);
    }

    /**
     * {@inheritDoc}
     */
    public void run(final ITestRunListener listener) {
        if (mPackageName == null) {
            throw new IllegalArgumentException("package name has not been set");
        }
        if (mDevice == null) {
            throw new IllegalArgumentException("Device has not been set");
        }

        final IRemoteAndroidTestRunner runner = createRemoteAndroidTestRunner(mPackageName,
                mDevice.getIDevice());
        if (mTestClassName != null) {
            if (mTestMethodName != null) {
                runner.setMethodName(mTestClassName, mTestMethodName);
            } else {
                runner.setClassName(mTestClassName);
            }
        }
        // run with no timeout
        if (mTestRunTimeout <= 0) {
            runner.run(listener);
        } else {
            boolean result = RunUtil.runTimed(mTestRunTimeout, new Runnable() {
                public void run() {
                    runner.run(listener);
                }
            });
            if (!result) {
                // test timed out
                runner.cancel();
                listener.testRunFailed(String.format("timeout: test did not complete in %d ms",
                        mTestRunTimeout));
            }
        }
    }

    /**
     * unsupported
     */
    public void run(TestResult result) {
        throw new UnsupportedOperationException();
    }
}
