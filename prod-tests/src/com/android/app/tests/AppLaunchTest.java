/*
 * Copyright (C) 2012 The Android Open Source Project
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
package com.android.app.tests;

import com.android.ddmlib.testrunner.ITestRunListener.TestFailure;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.build.IAppBuildInfo;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.testtype.InstrumentationTest;
import com.android.tradefed.util.AaptParser;

import junit.framework.Assert;

import java.io.File;
import java.util.Collections;

/**
 * A harness that installs and launches an app on device and verifies it doesn't crash.
 * <p/>
 * Requires a {@link IAppBuildInfo} and 'aapt' being present in path. Assume the AppLaunch
 * test app is already present on device.
 */
public class AppLaunchTest implements IDeviceTest, IRemoteTest, IBuildReceiver {

    private static final String RUN_NAME = "AppLaunch";
    private ITestDevice mDevice;
    private IBuildInfo mBuild;

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
    @Override
    public ITestDevice getDevice() {
        return mDevice;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mBuild = buildInfo;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        long startTime = System.currentTimeMillis();
        listener.testRunStarted(RUN_NAME, 2);
        try {
            Assert.assertTrue(mBuild instanceof IAppBuildInfo);
            IAppBuildInfo appBuild = (IAppBuildInfo)mBuild;
            Assert.assertEquals(1, appBuild.getAppPackageFiles().size());
            File apkFile = appBuild.getAppPackageFiles().get(0).getFile();
            AaptParser p = AaptParser.parse(apkFile);
            Assert.assertNotNull(p);
            String packageName = p.getPackageName();
            Assert.assertNotNull(String.format("Failed to parse package name from %s",
                    apkFile.getAbsolutePath()), packageName);

            performInstallTest(apkFile, listener);
            performLaunchTest(packageName, listener);
            getDevice().uninstallPackage(packageName);
        } catch (AssertionError e) {
            listener.testRunFailed(e.toString());
        } finally {
            listener.testRunEnded(System.currentTimeMillis() - startTime,
                    Collections.<String, String> emptyMap());
        }

    }

    private void performInstallTest(File apkFile, ITestInvocationListener listener)
            throws DeviceNotAvailableException {
        TestIdentifier installTest = new TestIdentifier("com.android.app.tests.InstallTest",
                "testInstall");
        listener.testStarted(installTest);
        String result = getDevice().installPackage(apkFile, true);
        if (result != null) {
            listener.testFailed(TestFailure.FAILURE, installTest, result);
        }
        listener.testEnded(installTest, Collections.<String, String> emptyMap());
    }

    private void performLaunchTest(String packageName, ITestInvocationListener listener)
            throws DeviceNotAvailableException {
        InstrumentationTest i = new InstrumentationTest();
        i.setRunName(RUN_NAME);
        i.setPackageName("com.android.applaunchtest");
        i.setRunnerName("com.android.applaunchtest.AppLaunchRunner");
        i.setDevice(getDevice());
        i.addInstrumentationArg("packageName", packageName);
        i.run(listener);
        InputStreamSource s = getDevice().getScreenshot();
        listener.testLog("screenshot", LogDataType.PNG, s);
        s.cancel();
    }
}
