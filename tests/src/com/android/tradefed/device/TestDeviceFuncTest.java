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
import com.android.ddmlib.testrunner.RemoteAndroidTestRunner;
import com.android.tradefed.TestAppConstants;
import com.android.tradefed.result.CollectingTestListener;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.StreamUtil;

import org.easymock.EasyMock;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
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
    /** Expect bugreports to be at least a meg. */
    private static final int mMinBugreportBytes = 1024 * 1024;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mTestDevice = (TestDevice)getDevice();
        mMonitor = mTestDevice.getDeviceStateMonitor();
    }

    /**
     * Simple testcase to ensure that the grabbing a bugreport from a real TestDevice works.
     */
    public void testBugreport() throws Exception {
        String data = StreamUtil.getStringFromStream(
                mTestDevice.getBugreport().createInputStream());
        assertTrue(String.format("Expected at least %d characters; only saw %d", mMinBugreportBytes,
                data.length()), data.length() >= mMinBugreportBytes);
    }

    /**
     * Simple normal case test for
     * {@link TestDevice#executeShellCommand(String)}.
     * <p/>
     * Do a 'shell ls' command, and verify /data and /system are listed in result.
     */
    public void testExecuteShellCommand() throws IOException, DeviceNotAvailableException {
        Log.i(LOG_TAG, "testExecuteShellCommand");
        assertSimpleShellCommand();
    }

    /**
     * Verify that a simple {@link TestDevice#executeShellCommand(String)} command is successful.
     */
    private void assertSimpleShellCommand() throws DeviceNotAvailableException {
        final String output = mTestDevice.executeShellCommand("ls");
        assertTrue(output.contains("data"));
        assertTrue(output.contains("system"));
    }

    /**
     * Test install and uninstall of package
     */
    public void testInstallUninstall() throws IOException, DeviceNotAvailableException {
        Log.i(LOG_TAG, "testInstallUninstall");
        // TODO: somehow inject path to apk file to test
//        File tmpFile = new File("<path toTradeFedTestApp.apk>");
//        mTestDevice.uninstallPackage(TestAppConstants.TESTAPP_PACKAGE);
//        assertFalse(mTestDevice.executeShellCommand("pm list packages").contains(
//                TestAppConstants.TESTAPP_PACKAGE));
//        mTestDevice.installPackage(tmpFile, true);
//        assertTrue(mTestDevice.executeShellCommand("pm list packages").contains(
//                TestAppConstants.TESTAPP_PACKAGE));
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
            String externalStorePath =  mTestDevice.getMountPoint(IDevice.MNT_EXTERNAL_STORAGE);
            assertNotNull(externalStorePath);
            deviceFilePath = String.format("%s/%s", externalStorePath, "tmp_testPushPull.txt");
            // ensure file does not already exist
            mTestDevice.executeShellCommand(String.format("rm %s", deviceFilePath));
            assertFalse(String.format("%s exists", deviceFilePath),
                    mTestDevice.doesFileExist(deviceFilePath));

            assertTrue(mTestDevice.pushFile(tmpFile, deviceFilePath));
            assertTrue(mTestDevice.doesFileExist(deviceFilePath));
            tmpDestFile = FileUtil.createTempFile("tmp", "txt");
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

    /**
     * Test pulling a file from device that does not exist.
     * <p/>
     * Expect {@link TestDevice#pullFile(String)} to return <code>false</code>
     */
    public void testPull_noexist() throws IOException, DeviceNotAvailableException {
        Log.i(LOG_TAG, "testPull_noexist");

        // make sure the root path is valid
        String externalStorePath =  mTestDevice.getMountPoint(IDevice.MNT_EXTERNAL_STORAGE);
        assertNotNull(externalStorePath);
        String deviceFilePath = String.format("%s/%s", externalStorePath, "thisfiledoesntexist");
        assertFalse(String.format("%s exists", deviceFilePath),
                mTestDevice.doesFileExist(deviceFilePath));
        assertNull(mTestDevice.pullFile(deviceFilePath));
    }

    private File createTempTestFile(File dir) throws IOException {
        File tmpFile = null;
        try {
            final String fileContents = "this is the test file contents";
            tmpFile = FileUtil.createTempFile("tmp", ".txt", dir);
            FileUtil.writeToFile(fileContents, tmpFile);
            return tmpFile;
        } catch (IOException e) {
            if (tmpFile != null) {
                tmpFile.delete();
            }
            throw e;
        }
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
        String expectedDeviceFilePath = null;
        String externalStorePath = null;

        // create temp dir with one temp file
        File tmpDir = FileUtil.createTempDir("tmp");
        try {
            File tmpFile = createTempTestFile(tmpDir);
            // set last modified to 10 minutes ago
            tmpFile.setLastModified(System.currentTimeMillis() - 10*60*1000);
            externalStorePath = mTestDevice.getMountPoint(IDevice.MNT_EXTERNAL_STORAGE);
            assertNotNull(externalStorePath);
            expectedDeviceFilePath = String.format("%s/%s/%s", externalStorePath,
                    tmpDir.getName(), tmpFile.getName());

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
            if (expectedDeviceFilePath != null && externalStorePath != null) {
                mTestDevice.executeShellCommand(String.format("rm -r %s/%s", externalStorePath,
                        expectedDeviceFilePath));
            }
            FileUtil.recursiveDelete(tmpDir);
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
        int origTimeout = mTestDevice.getCommandTimeout();
        try {
            assertEquals(TestDeviceState.ONLINE, mMonitor.getDeviceState());
            // reset operation timeout to small value to make test run quicker
            mTestDevice.setCommandTimeout(5*1000);
            assertEquals(CommandStatus.SUCCESS,
                    mTestDevice.executeFastbootCommand("getvar", "product").getStatus());
            assertEquals(TestDeviceState.FASTBOOT, mMonitor.getDeviceState());
        } finally {
            mTestDevice.setCommandTimeout(origTimeout);
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
        // check that device has root
        assertTrue(mTestDevice.executeShellCommand("id").contains("root"));
    }

    /**
     * Verify device can be rebooted into adb recovery.
     */
    public void testRebootIntoRecovery() throws DeviceNotAvailableException {
        Log.i(LOG_TAG, "testRebootIntoRecovery");
        try {
            mTestDevice.rebootIntoRecovery();
            assertEquals(TestDeviceState.RECOVERY, mMonitor.getDeviceState());
        } finally {
            mTestDevice.reboot();
        }
    }

    /**
     * Verify that {@link TestDevice#clearErrorDialogs()} can successfully clear an error dialog
     * from screen.
     * <p/>
     * This is done by running a test app which will crash, then running another app that
     * does UI based tests.
     * <p/>
     * Assumes DevTools and TradeFedUiApp are currently installed.
     */
    public void testClearErrorDialogs_crash() throws DeviceNotAvailableException {
        Log.i(LOG_TAG, "testClearErrorDialogs_crash");
        // now cause a crash dialog to appear
        getDevice().executeShellCommand("am start -w -n " + TestAppConstants.CRASH_ACTIVITY);
        getDevice().clearErrorDialogs();
        assertTrue(runUITests());
    }

    /**
     * Verify the steps taken to disable keyguard after reboot are successfully
     * <p/>
     * This is done by rebooting then run a app that does UI based tests.
     * <p/>
     * Assumes DevTools and TradeFedUiApp are currently installed.
     */
    public void testDisableKeyguard() throws DeviceNotAvailableException {
        Log.i(LOG_TAG, "testDisableKeyguard");
        getDevice().reboot();
        assertTrue(runUITests());
    }

    /**
     * Test that TradeFed can successfully recover from the adb host daemon process being killed
     */
    public void testExecuteShellCommand_adbKilled() throws DeviceNotAvailableException {
        // FIXME: adb typically does not recover, and this causes rest of tests to fail
        //Log.i(LOG_TAG, "testExecuteShellCommand_adbKilled");
        //CommandResult result = RunUtil.getInstance().runTimedCmd(30*1000, "adb", "kill-server");
        //assertEquals(CommandStatus.SUCCESS, result.getStatus());
        //assertSimpleShellCommand();
    }

    /**
     * Run the test app UI tests and return true if they all pass.
     */
    private boolean runUITests() throws DeviceNotAvailableException {
        RemoteAndroidTestRunner uirunner = new RemoteAndroidTestRunner(
                TestAppConstants.UITESTAPP_PACKAGE, getDevice().getIDevice());
        CollectingTestListener uilistener = new CollectingTestListener();
        getDevice().runInstrumentationTests(uirunner, uilistener);
        return TestAppConstants.UI_TOTAL_TESTS == uilistener.getNumPassedTests();
    }
}
