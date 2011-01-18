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

package com.android.tradefed.targetsetup;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.targetsetup.IDeviceFlasher.UserDataFlashOption;
import com.android.tradefed.util.FileUtil;

import org.easymock.EasyMock;

import java.io.File;

import junit.framework.TestCase;

/**
 * Unit tests for {@link DeviceFlashPreparer}.
 */
public class DeviceFlashPreparerTest extends TestCase {

    private IDeviceFlasher mMockFlasher;
    private DeviceFlashPreparer mDeviceFlashPreparer;
    private ITestDevice mMockDevice;
    private IDeviceBuildInfo mMockBuildInfo;
    private File mTmpDir;

    /**
     * {@inheritDoc}
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mMockFlasher = EasyMock.createMock(IDeviceFlasher.class);
        mMockDevice = EasyMock.createMock(ITestDevice.class);
        EasyMock.expect(mMockDevice.getSerialNumber()).andReturn("foo").anyTimes();
        mMockBuildInfo = new DeviceBuildInfo(0, "", "");
        mDeviceFlashPreparer = new DeviceFlashPreparer() {
            @Override
            protected IDeviceFlasher createFlasher(ITestDevice device) {
                return mMockFlasher;
            }

            @Override
            int getDeviceBootPollTimeMs() {
                return 100;
            }
        };
        mDeviceFlashPreparer.setDeviceBootTime(100);
        // expect this call
        mMockFlasher.setUserDataFlashOption(UserDataFlashOption.FLASH);
        mTmpDir = FileUtil.createTempDir("tmp");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void tearDown() throws Exception {
        FileUtil.recursiveDelete(mTmpDir);
        super.tearDown();
    }

    /**
     * Simple normal case test for {@link DeviceSetup#setUp(ITestDevice, IBuildInfo)}.
     */
    public void testSetup() throws Exception {
        doSetupExpectations();
        EasyMock.replay(mMockFlasher, mMockDevice);
        mDeviceFlashPreparer.setUp(mMockDevice, mMockBuildInfo);
    }

    /**
     * Set EasyMock expectations for a normal setup call
     */
    private void doSetupExpectations() throws TargetSetupError, DeviceNotAvailableException {
        mMockFlasher.flash(mMockDevice, mMockBuildInfo);
        mMockDevice.waitForDeviceOnline();
        // expect shell command to test if boot is complete
        EasyMock.expect(mMockDevice.executeShellCommand((String)EasyMock.anyObject())).andReturn(
                "1");
        mMockDevice.waitForDeviceAvailable();
    }

    /**
     * Test {@link DeviceSetupr#setUp(ITestDevice, IBuildInfo)} when a non IDeviceBuildInfo type
     * is provided
     */
    public void testSetUp_nonDevice() throws Exception {
        try {
            mDeviceFlashPreparer.setUp(mMockDevice, EasyMock.createMock(IBuildInfo.class));
            fail("IllegalArgumentException not thrown");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    /**
     * Test {@link DeviceSetup#setUp(ITestDevice, IBuildInfo)} when build does not boot
     */
    public void testSetup_buildError() throws Exception {
        mMockFlasher.flash(mMockDevice, mMockBuildInfo);
        mMockDevice.waitForDeviceOnline();
        EasyMock.expect(mMockDevice.executeShellCommand("getprop dev.bootcomplete")).andReturn("");
        EasyMock.expect(mMockDevice.executeShellCommand((String)EasyMock.anyObject())).
                andReturn("").anyTimes();
        EasyMock.replay(mMockFlasher, mMockDevice);
        try {
            mDeviceFlashPreparer.setUp(mMockDevice, mMockBuildInfo);
            fail("BuildError not thrown");
        } catch (BuildError e) {
            // expected
        }
    }
}
