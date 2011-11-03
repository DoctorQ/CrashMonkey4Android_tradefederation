/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.sdk.tests;

import com.android.ddmlib.EmulatorConsole;
import com.android.ddmlib.testrunner.RemoteAndroidTestRunner;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.ISdkBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.util.FileUtil;

import junit.framework.Assert;

import java.io.File;

/**
 * Runs all the emulator test applications
 */
public class EmulatorTests implements IRemoteTest, IBuildReceiver, IDeviceTest {

    private ITestDevice mDevice;
    private ISdkBuildInfo mSdkBuild;
    private static final String EMULATOR_TEST_FOLDER = "tests/emulator-test-apps";
    private static final String[] EMULATOR_TEST_PACKAGES = {
        "com.android.emulator.connectivity.test","com.android.emulator.gps.test" };

    // default gps location for the gps test
    private static final double LONGITUDE = -122.08345770835876;
    private static final double LATITUDE = 37.41991859119417;

    /**
     * {@inheritDoc}
     */
    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mSdkBuild = (ISdkBuildInfo)buildInfo;
    }

    @Override
    public void setDevice(ITestDevice device){
        mDevice = device;
    }

    @Override
    public ITestDevice getDevice() {
        return mDevice;
    }

    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        Assert.assertNotNull("missing sdk build to test", mSdkBuild);
        Assert.assertNotNull("missing sdk build to test", mSdkBuild.getSdkDir());
        Assert.assertNotNull("missing emulator", mDevice);
        Assert.assertTrue("device is not a emulator", mDevice.getIDevice().isEmulator());

        CLog.i("Running Emulator Test Apps in sdk %s", mSdkBuild.getSdkDir().getAbsolutePath());

        // get the path to the test-apps
        File emulatorTestAppDir = FileUtil.getFileForPath(mSdkBuild.getSdkDir(),
                EMULATOR_TEST_FOLDER);
        Assert.assertTrue(String.format("could not find tests/emulator-test-apps folder in sdk %s",
                mSdkBuild.getSdkDir()), emulatorTestAppDir.isDirectory() &&
                emulatorTestAppDir.listFiles() != null);
        Assert.assertTrue(String.format("Could not find targets for sdk %s",
                mSdkBuild.getSdkDir()), mSdkBuild.getSdkTargets() != null &&
                mSdkBuild.getSdkTargets().length > 0);

        installTestApps(emulatorTestAppDir.listFiles(), listener);
        setEmulatorPreTestState();
        for (int i = 0; i < EMULATOR_TEST_PACKAGES.length; i++){
            runTestApps(EMULATOR_TEST_PACKAGES[i], listener);
        }
    }

    /**
     * Install the given list of apk's
     *
     * @param testApps an array of apk files
     * @param listener test result listener
     * @throws DeviceNotAvailableException
     */
    private void installTestApps( File [] testApps, ITestInvocationListener listener)
        throws DeviceNotAvailableException{
        for (File testApp : testApps){
            if (testApp.getName().endsWith(".apk")){
                CLog.i("Installing emulator test-app %s", testApp.getName());
                String result = mDevice.installPackage(testApp, true);
                if ( result==null ){
                    CLog.i("Installation completed");
                } else {
                    CLog.e( "Installation failed: \n%s", result);
                }
            }
        }
    }

    /**
     * Set the emulator to the state that tests expect it in
     */
    private void setEmulatorPreTestState(){
        // GPS location
        EmulatorConsole console = EmulatorConsole.getConsole(mDevice.getIDevice());
        console.sendLocation(LONGITUDE, LATITUDE, 0);
    }

    /**
     * Run all the emulator test apps
     * @param testAppPackage the emulator test app package
     * @param listener test result listener
     * @throws DeviceNotAvailableException
     */
    private void runTestApps(String testAppPackage, ITestInvocationListener listener)
            throws DeviceNotAvailableException{
        RemoteAndroidTestRunner runner = new RemoteAndroidTestRunner(testAppPackage,
                mDevice.getIDevice());
        mDevice.runInstrumentationTests(runner, listener);
    }
}
