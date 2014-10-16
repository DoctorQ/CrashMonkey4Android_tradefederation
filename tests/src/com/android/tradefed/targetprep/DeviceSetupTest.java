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

package com.android.tradefed.targetprep;

import com.android.ddmlib.IDevice;
import com.android.tradefed.build.DeviceBuildInfo;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.IDeviceBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.util.FileUtil;

import junit.framework.TestCase;

import org.easymock.EasyMock;

import java.io.File;
import java.io.IOException;

/**
 * Unit tests for {@link DeviceSetup}.
 */
public class DeviceSetupTest extends TestCase {

    private DeviceSetup mDeviceSetup;
    private ITestDevice mMockDevice;
    private IDevice mMockIDevice;
    private IDeviceBuildInfo mMockBuildInfo;
    private File mTmpDir;

    /**
     * {@inheritDoc}
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mMockDevice = EasyMock.createMock(ITestDevice.class);
        mMockIDevice = EasyMock.createMock(IDevice.class);
        EasyMock.expect(mMockDevice.getSerialNumber()).andReturn("foo").anyTimes();
        mMockBuildInfo = new DeviceBuildInfo("0", "", "");
        mDeviceSetup = new DeviceSetup();
        mDeviceSetup.setMinExternalStoreSpace(-1);
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
        EasyMock.replay(mMockDevice);
        mDeviceSetup.setUp(mMockDevice, mMockBuildInfo);
    }

    /**
     * Set EasyMock expectations for a normal setup call
     */
    private void doSetupExpectations() throws TargetSetupError, DeviceNotAvailableException {
        EasyMock.expect(mMockDevice.enableAdbRoot()).andReturn(Boolean.TRUE);
        mMockDevice.postBootSetup();
        EasyMock.expect(mMockDevice.clearErrorDialogs()).andReturn(Boolean.TRUE);
        EasyMock.expect(mMockDevice.executeShellCommand("getprop dev.bootcomplete")).andReturn("1");
        // expect push of local.prop file to change system properties
        EasyMock.expect(mMockDevice.pushString((String)EasyMock.anyObject(),
                EasyMock.contains("local.prop"))).andReturn(Boolean.TRUE);
        mMockDevice.reboot();
        // expect a bunch of shell commands - no need to verify which ones
        EasyMock.expect(mMockDevice.executeShellCommand((String)EasyMock.anyObject())).
                andReturn("").anyTimes();
        EasyMock.expect(mMockDevice.getProperty("ro.build.id")).andReturn("IMM76K");
    }

    /**
     * Test {@link DeviceSetup#setUp(ITestDevice, IBuildInfo)} when free space check fails.
     */
    public void testSetup_freespace() throws Exception {
        doSetupExpectations();
        mDeviceSetup.setMinExternalStoreSpace(500);
        EasyMock.expect(mMockDevice.getExternalStoreFreeSpace()).andReturn(1L);
        EasyMock.replay(mMockDevice);
        try {
            mDeviceSetup.setUp(mMockDevice, mMockBuildInfo);
            fail("DeviceNotAvailableException not thrown");
        } catch (DeviceNotAvailableException e) {
            // expected
        }
    }

    /**
     * Test {@link DeviceSetup#setUp(ITestDevice, IBuildInfo)} when local data path does not
     * exist.
     */
    public void testSetup_badLocalData() throws Exception {
        doSetupExpectations();
        mDeviceSetup.setLocalDataPath(new File("idontexist"));
        EasyMock.replay(mMockDevice);
        try {
            mDeviceSetup.setUp(mMockDevice, mMockBuildInfo);
            fail("TargetSetupError not thrown");
        } catch (TargetSetupError e) {
            // expected
        }
    }

    /**
     * Test normal case {@link DeviceSetup#setUp(ITestDevice, IBuildInfo)} when local data
     * is synced
     */
    public void testSetup_syncData() throws Exception {
        doSetupExpectations();
        doSyncDataExpectations(true);

        EasyMock.replay(mMockDevice, mMockIDevice);
        mDeviceSetup.setUp(mMockDevice, mMockBuildInfo);
    }

    /**
     * Perform common EasyMock expect operations for a setUp call which syncs local data
     */
    private void doSyncDataExpectations(boolean result) throws IOException,
            DeviceNotAvailableException {
        mDeviceSetup.setLocalDataPath(mTmpDir);
        EasyMock.expect(mMockDevice.getIDevice()).andReturn(mMockIDevice);
        String mntPoint = "/sdcard";
        EasyMock.expect(mMockIDevice.getMountPoint(IDevice.MNT_EXTERNAL_STORAGE)).andReturn(
                mntPoint);
        EasyMock.expect(mMockDevice.syncFiles(mTmpDir, mntPoint)).andReturn(result);
    }

    /**
     * Test case {@link DeviceSetup#setUp(ITestDevice, IBuildInfo)} when local data fails to be
     * synced.
     */
    public void testSetup_syncDataFails() throws Exception {
        doSetupExpectations();
        doSyncDataExpectations(false);
        EasyMock.replay(mMockDevice, mMockIDevice);
        try {
            mDeviceSetup.setUp(mMockDevice, mMockBuildInfo);
            fail("TargetSetupError not thrown");
        } catch (TargetSetupError e) {
            // expected
        }
    }

    public void testBuildName() {
        assertTrue("failed to verify IML74K", mDeviceSetup.isReleaseBuildName("IML74K"));
        assertTrue("failed to verify GRJ90", mDeviceSetup.isReleaseBuildName("GRJ90"));
        assertFalse("failed to reject MASTER", mDeviceSetup.isReleaseBuildName("MASTER"));
        assertFalse("failed to reject 123456", mDeviceSetup.isReleaseBuildName("123456"));
        assertFalse("failed to reject empty string", mDeviceSetup.isReleaseBuildName(""));
        assertFalse("failed to reject random stuff", mDeviceSetup.isReleaseBuildName("!@#$%^&*("));
    }
}
