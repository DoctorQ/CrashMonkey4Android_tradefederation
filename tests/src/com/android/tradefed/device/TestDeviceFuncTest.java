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
package com.android.tradefed.device;

import com.android.ddmlib.IDevice;
import com.android.ddmlib.Log;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.util.CommandResult.CommandStatus;

import org.easymock.EasyMock;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Functional tests for {@link TestDevice}.
 * <p/>
 * Requires a physical device to be connected.
 */
public class TestDeviceFuncTest extends DeviceTestCase {

    private static final String LOG_TAG = "TestDeviceFuncTest";
    private TestDevice mTestDevice;
    private IDeviceStateMonitor mMonitor;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mTestDevice = (TestDevice)getDevice();
        mMonitor = mTestDevice.getDeviceStateMonitor();
    }

    /**
     * Simple normal case test for
     * {@link TestDevice#executeShellCommand(String)}.
     * <p/>
     * Do a 'shell ls' command, and verify /data and /system are listed in result.
     */
    public void testExecuteShellCommand() throws IOException, DeviceNotAvailableException {
        Log.i(LOG_TAG, "testExecuteShellCommand");
        final String output = mTestDevice.executeShellCommand("ls");
        assertTrue(output.contains("data"));
        assertTrue(output.contains("system"));
    }

    /**
     * Push and then pull a file from device, and verify contents are as expected.
     */
    public void testPushPull() throws IOException, DeviceNotAvailableException {
        Log.i(LOG_TAG, "testPushPull");
        File tmpFile = null;
        File tmpDestFile = null;
        String deviceFilePath = null;

        try {
            tmpFile = createTempTestFile(null);
            deviceFilePath = String.format("%s/%s", mTestDevice.getIDevice().getMountPoint(
                    IDevice.MNT_EXTERNAL_STORAGE), "tmp_testPushPull.txt");
            // ensure file does not already exist
            mTestDevice.executeShellCommand(String.format("rm %s", deviceFilePath));
            assertFalse(String.format("%s exists", deviceFilePath),
                    mTestDevice.doesFileExist(deviceFilePath));

            assertTrue(mTestDevice.pushFile(tmpFile, deviceFilePath));
            assertTrue(mTestDevice.doesFileExist(deviceFilePath));
            tmpDestFile = File.createTempFile("tmp", "txt");
            assertTrue(mTestDevice.pullFile(deviceFilePath, tmpDestFile));
            assertTrue(compareFiles(tmpFile, tmpDestFile));
        } finally {
            if (tmpDestFile != null) {
                tmpDestFile.delete();
            }
            if (deviceFilePath != null) {
                mTestDevice.executeShellCommand(String.format("rm %s", deviceFilePath));
            }
        }
    }

    private File createTempTestFile(File dir) throws IOException, FileNotFoundException {
        File tmpFile;
        final String fileContents = "this is the test file contents";
        tmpFile = File.createTempFile("tmp", ".txt", dir);
        FileOutputStream stream = new FileOutputStream(tmpFile);
        stream.write(fileContents.getBytes());
        stream.close();
        tmpFile.deleteOnExit();
        return tmpFile;
    }

    /**
     * Utility method to do byte-wise content comparison of two files.
     */
    private boolean compareFiles(File file1, File file2) throws IOException {
        BufferedInputStream stream1 = null;
        BufferedInputStream stream2 = null;

        try {
            stream1 = new BufferedInputStream(new FileInputStream(file1));
            stream2 = new BufferedInputStream(new FileInputStream(file2));
            boolean eof = false;
            while (!eof) {
                int byte1 = stream1.read();
                int byte2 = stream2.read();
                if (byte1 != byte2) {
                    return false;
                }
                eof = byte1 == -1;
            }
            return true;
        } finally {
            if (stream1 != null) {
                stream1.close();
            }
            if (stream2 != null) {
                stream2.close();
            }
        }
    }

    /**
     * Test syncing a single file using {@link TestDevice#syncFiles(File, String)}.
     */
    public void testSyncFiles() throws IOException, DeviceNotAvailableException {
        // create temp dir with one temp file
        File tmpDir = File.createTempFile("tmp", null);
        tmpDir.delete();
        tmpDir.mkdir();
        File tmpFile = createTempTestFile(tmpDir);
        // set last modified to 10 minutes ago
        tmpFile.setLastModified(System.currentTimeMillis() - 10*60*1000);
        String externalStorePath = mTestDevice.getIDevice().getMountPoint(
                IDevice.MNT_EXTERNAL_STORAGE);
        String expectedDeviceFilePath = String.format("%s/%s/%s", externalStorePath,
                tmpDir.getName(), tmpFile.getName());
        try {
            assertTrue(mTestDevice.syncFiles(tmpDir, externalStorePath));
            assertTrue(mTestDevice.doesFileExist(expectedDeviceFilePath));

            // get 'ls -l' attributes of file which includes timestamp
            String origTmpFileStamp = mTestDevice.executeShellCommand(String.format("ls -l %s",
                    expectedDeviceFilePath));
            // now create another file and verify that is synced
            File tmpFile2 = createTempTestFile(tmpDir);
            tmpFile2.setLastModified(System.currentTimeMillis() - 10*60*1000);
            assertTrue(mTestDevice.syncFiles(tmpDir, externalStorePath));
            String expectedDeviceFilePath2 = String.format("%s/%s/%s", externalStorePath,
                    tmpDir.getName(), tmpFile2.getName());
            assertTrue(mTestDevice.doesFileExist(expectedDeviceFilePath2));

            // verify 1st file timestamp did not change
            String unchangedTmpFileStamp = mTestDevice.executeShellCommand(String.format("ls -l %s",
                    expectedDeviceFilePath));
            assertEquals(origTmpFileStamp, unchangedTmpFileStamp);

            // now modify 1st file and verify it does change remotely
            String testString = "blah";
            FileOutputStream stream = new FileOutputStream(tmpFile);
            stream.write(testString.getBytes());
            stream.close();

            assertTrue(mTestDevice.syncFiles(tmpDir, externalStorePath));
            String tmpFileContents = mTestDevice.executeShellCommand(String.format("cat %s",
                    expectedDeviceFilePath));
            assertTrue(tmpFileContents.contains(testString));
        } finally {
            mTestDevice.executeShellCommand(String.format("rm -r %s/%s", externalStorePath,
                    expectedDeviceFilePath));
        }
    }

    /**
     * Test {@link TestDevice#executeFastbootCommand(String...)} when device is in adb mode.
     * <p/>
     * Expect fastboot recovery to be invoked, which will boot device back to fastboot mode and
     * command will succeed.
     */
    public void testExecuteFastbootCommand_deviceInAdb() throws DeviceNotAvailableException {
        Log.i(LOG_TAG, "testExecuteFastbootCommand_deviceInAdb");
        try {
            assertEquals(TestDeviceState.ONLINE, mMonitor.getDeviceState());
            assertEquals(CommandStatus.SUCCESS,
                    mTestDevice.executeFastbootCommand("getvar", "product").getStatus());
            assertEquals(TestDeviceState.FASTBOOT, mMonitor.getDeviceState());
        } finally {
            mTestDevice.reboot();
            assertEquals(TestDeviceState.ONLINE, mMonitor.getDeviceState());
        }
    }

    /**
     * Test {@link TestDevice#executeFastbootCommand(String...)} when an invalid command is passed.
     * <p/>
     * Expect the result indicate failure, and recovery not to be invoked.
     */
    public void testExecuteFastbootCommand_badCommand() throws DeviceNotAvailableException {
        Log.i(LOG_TAG, "testExecuteFastbootCommand_badCommand");
        IDeviceRecovery origRecovery = mTestDevice.getRecovery();
        try {
            mTestDevice.rebootIntoBootloader();
            assertEquals(TestDeviceState.FASTBOOT, mMonitor.getDeviceState());
            // substitute recovery mechanism to ensure recovery is not called when bad command is
            // passed
            IDeviceRecovery mockRecovery = EasyMock.createStrictMock(IDeviceRecovery.class);
            mTestDevice.setRecovery(mockRecovery);
            EasyMock.replay(mockRecovery);
            assertEquals(CommandStatus.FAILED,
                    mTestDevice.executeFastbootCommand("badcommand").getStatus());
        } finally {
            mTestDevice.setRecovery(origRecovery);
            mTestDevice.reboot();
            assertEquals(TestDeviceState.ONLINE, mMonitor.getDeviceState());
        }
    }

    /**
     * Verify device can be rebooted into bootloader and back to adb.
     */
    public void testRebootIntoBootloader() throws DeviceNotAvailableException {
        Log.i(LOG_TAG, "testRebootIntoBootloader");
        try {
            mTestDevice.rebootIntoBootloader();
            assertEquals(TestDeviceState.FASTBOOT, mMonitor.getDeviceState());
        } finally {
            mTestDevice.reboot();
            assertEquals(TestDeviceState.ONLINE, mMonitor.getDeviceState());
        }
    }

    /**
     * Verify device can be rebooted into adb.
     */
    public void testReboot() throws DeviceNotAvailableException {
        Log.i(LOG_TAG, "testReboot");
        mTestDevice.reboot();
        assertEquals(TestDeviceState.ONLINE, mMonitor.getDeviceState());
    }

    /**
     * Simple normal case test for {@link DeviceManager#enableAdbRoot(ITestDevice)}.
     * <p/>
     * This test assumes adb build on device under test a) doesn't have root by default, and
     * b) is allowed to get root. ie running userdebug build.
     * <p/>
     * Verifies adb root works initially and after a reboot.
     */
    public void testEnableAdbRoot() throws DeviceNotAvailableException, InterruptedException {
        mTestDevice.setEnableAdbRoot(false);
        assertTrue(mTestDevice.enableAdbRoot());
        // if adb is running as root, adb shell id should return "uid=0(root)"
        assertTrue(mTestDevice.executeShellCommand("id").contains("root"));
        mTestDevice.reboot();
        assertFalse(mTestDevice.executeShellCommand("id").contains("root"));
        assertTrue(mTestDevice.enableAdbRoot());
        assertTrue(mTestDevice.executeShellCommand("id").contains("root"));
    }
}
