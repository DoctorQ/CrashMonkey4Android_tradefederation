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

    /**
     * {@inheritDoc}
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    /**
     * Simple normal case test for
     * {@link TestDevice#executeShellCommand(String)}.
     * <p/>
     * Do a 'shell ls' command, and verify /data and /system are listed in result.
     */
    public void testExecuteShellCommand() throws IOException, DeviceNotAvailableException {
        Log.i(LOG_TAG, "testExecuteShellCommand");
        final String output = getDevice().executeShellCommand("ls");
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
            final String fileContents = "this is the test file contents";
            tmpFile = File.createTempFile("tmp", "txt");
            FileOutputStream stream = new FileOutputStream(tmpFile);
            stream.write(fileContents.getBytes());
            deviceFilePath = String.format("%s/%s", getDevice().getIDevice().getMountPoint(
                    IDevice.MNT_EXTERNAL_STORAGE), "tmp_testPushPull.txt");
            // ensure file does not already exist
            getDevice().executeShellCommand(String.format("rm %s", deviceFilePath));
            assertFalse(getDevice().doesFileExist(deviceFilePath));

            assertTrue(getDevice().pushFile(tmpFile, deviceFilePath));
            assertTrue(getDevice().doesFileExist(deviceFilePath));
            tmpDestFile = File.createTempFile("tmp", "txt");
            assertTrue(getDevice().pullFile(deviceFilePath, tmpDestFile));
            assertTrue(compareFiles(tmpFile, tmpDestFile));
        } finally {
            if (tmpFile != null) {
                tmpFile.delete();
            }
            if (tmpDestFile != null) {
                tmpDestFile.delete();
            }
            if (deviceFilePath != null) {
                getDevice().executeShellCommand(String.format("rm %s", deviceFilePath));
            }
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
}
