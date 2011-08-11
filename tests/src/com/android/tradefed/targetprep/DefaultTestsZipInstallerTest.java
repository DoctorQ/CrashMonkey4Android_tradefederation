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

package com.android.tradefed.targetprep;

import com.android.ddmlib.FileListingService;
import com.android.tradefed.build.DeviceBuildInfo;
import com.android.tradefed.build.IDeviceBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.ITestDevice.RecoveryMode;
import com.android.tradefed.device.MockFileUtil;

import org.easymock.EasyMock;

import java.io.File;

import junit.framework.TestCase;

public class DefaultTestsZipInstallerTest extends TestCase {
    private static final String SKIP_THIS = "skipThis";

    private static final String TEST_STRING = "foo";

    private ITestDevice mMockDevice;
    private IDeviceBuildInfo mDeviceBuild;
    private DefaultTestsZipInstaller mZipInstaller;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mZipInstaller = new DefaultTestsZipInstaller(SKIP_THIS) {
            @Override
            File[] getTestsZipDataFiles(File hostDir) {
                return new File[] { new File("foo") };
            }
        };

        mMockDevice = EasyMock.createMock(ITestDevice.class);
        EasyMock.expect(mMockDevice.getSerialNumber()).andStubReturn(TEST_STRING);
        EasyMock.expect(mMockDevice.getProductType()).andStubReturn(TEST_STRING);
        EasyMock.expect(mMockDevice.getBuildId()).andStubReturn("1");
        mDeviceBuild = new DeviceBuildInfo("1", TEST_STRING, TEST_STRING);
    }

    /**
     * Exercise the core logic on a successful scenario.
     */
    public void testPushTestsZipOntoData() throws DeviceNotAvailableException, TargetSetupError {
        // mock a filesystem with these contents:
        // /data/app
        // /data/$SKIP_THIS
        MockFileUtil.setMockDirContents(
                mMockDevice, FileListingService.DIRECTORY_DATA, "app", SKIP_THIS);

        // expect initial reboot and android stop
        mMockDevice.rebootUntilOnline();
        EasyMock.expect(mMockDevice.getRecoveryMode()).andReturn(RecoveryMode.AVAILABLE);
        mMockDevice.setRecoveryMode(RecoveryMode.ONLINE);
        EasyMock.expect(mMockDevice.isDeviceEncrypted()).andReturn(false);
        EasyMock.expect(mMockDevice.executeShellCommand("stop")).andReturn("");

        // expect 'rm app' but not 'rm $SKIP_THIS'
        EasyMock.expect(mMockDevice.executeShellCommand(EasyMock.contains("rm -r data/app")))
                .andReturn("");

        // expect second reboot and android stop
        mMockDevice.rebootUntilOnline();
        EasyMock.expect(mMockDevice.isDeviceEncrypted()).andReturn(false);
        EasyMock.expect(mMockDevice.executeShellCommand("stop")).andReturn("");

        EasyMock.expect(mMockDevice.syncFiles((File) EasyMock.anyObject(),
                EasyMock.contains(FileListingService.DIRECTORY_DATA)))
                .andReturn(Boolean.TRUE);
        EasyMock.expect(mMockDevice.executeShellCommand(EasyMock.contains("chown system.system")))
                .andReturn(null);

        mMockDevice.setRecoveryMode(RecoveryMode.AVAILABLE);

        EasyMock.replay(mMockDevice);
        mZipInstaller.pushTestsZipOntoData(mMockDevice, mDeviceBuild);
        EasyMock.verify(mMockDevice);
    }
}
