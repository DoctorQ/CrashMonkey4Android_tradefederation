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

package com.android.tradefed.targetprep;

import com.android.tradefed.build.DeviceBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.StubTestDevice;
import com.android.tradefed.util.FakeTestsZipFolder;
import com.android.tradefed.util.FakeTestsZipFolder.ItemType;

import junit.framework.TestCase;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A test for {@link TestFilePushSetup}.
 */
public class TestFilePushSetupFuncTest extends TestCase {

    private Map<String, ItemType> mFiles;
    private List<String> mDeviceLocationList;
    private FakeTestsZipFolder mFakeTestsZipFolder;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mFiles = new HashMap<String, ItemType>();
        mFiles.put("app/AndroidCommonTests.apk", ItemType.FILE);
        mFiles.put("app/GalleryTests.apk", ItemType.FILE);
        mFiles.put("testinfo", ItemType.DIRECTORY);
        mFakeTestsZipFolder = new FakeTestsZipFolder(mFiles);
        assertTrue(mFakeTestsZipFolder.createItems());
        mDeviceLocationList = new ArrayList<String>();
        for (String file : mFiles.keySet()) {
            mDeviceLocationList.add(TestFilePushSetup.getDevicePathFromUserData(file));
        }
    }

    public void testSetup() throws TargetSetupError, BuildError, DeviceNotAvailableException {
        TestFilePushSetup testFilePushSetup = new TestFilePushSetup();
        DeviceBuildInfo stubBuild = new DeviceBuildInfo("0", "stub", "stub");
        stubBuild.setTestsDir(mFakeTestsZipFolder.getBasePath(), "0");
        assertFalse(mFiles.isEmpty());
        assertFalse(mDeviceLocationList.isEmpty());
        ITestDevice device = new StubTestDevice() {
            @Override
            public boolean pushDir(File localDir, String deviceFilePath)
                    throws DeviceNotAvailableException {
                return mDeviceLocationList.remove(deviceFilePath);
            }

            @Override
            public boolean pushFile(File localFile, String deviceFilePath)
                    throws DeviceNotAvailableException {
                return mDeviceLocationList.remove(deviceFilePath);
            }

            @Override
            public String executeShellCommand(String command) throws DeviceNotAvailableException {
                return "";
            }
        };
        for (String file : mFiles.keySet()) {
            testFilePushSetup.addTestFileName(file);
        }
        testFilePushSetup.setUp(device, stubBuild);
        assertTrue(mDeviceLocationList.isEmpty());
    }

    @Override
    protected void tearDown() throws Exception {
        mFakeTestsZipFolder.cleanUp();
        super.tearDown();
    }
}
