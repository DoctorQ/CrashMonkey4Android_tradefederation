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

// TODO: seems like for now, RunInstance via JUnit is performing the runner function. Perhaps only
// a com.android.tradefed.testtype package is needed
package com.android.tradefed.testtype;

import com.android.ddmlib.IDevice;
import com.android.ddmlib.testrunner.IRemoteAndroidTestRunner;
import com.android.ddmlib.testrunner.ITestRunListener;
import com.android.ddmlib.testrunner.RemoteAndroidTestRunner;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.ITestDevice;

import junit.framework.TestResult;

/**
 * A Test that runs a instrumentation test package on given device.
 */
public class InstrumentationTest implements IDeviceTest, IRemoteTest {

    @Option(name = "package", shortName = "p",
            description="The manifest package name of the Android test application to run")
    private String mPackageName = null;

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
     * @return
     */
    IRemoteAndroidTestRunner createRemoteAndroidTestRunner(String packageName, IDevice device) {
        return new RemoteAndroidTestRunner(packageName, device);
    }

    /**
     * {@inheritDoc}
     */
    public void run(ITestRunListener listener) {
        if (mPackageName == null) {
            throw new IllegalArgumentException("package name has not been set");
        }
        if (mDevice == null) {
            throw new IllegalArgumentException("Device has not been set");
        }

        IRemoteAndroidTestRunner runner = createRemoteAndroidTestRunner(mPackageName,
                mDevice.getIDevice());
        runner.run(listener);
    }

    /**
     * unsupported
     */
    public void run(TestResult result) {
        throw new UnsupportedOperationException();
    }
}
