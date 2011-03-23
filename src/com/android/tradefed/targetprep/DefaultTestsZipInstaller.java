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
import com.android.ddmlib.Log;
import com.android.tradefed.build.IDeviceBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.IFileEntry;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.util.FileUtil;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;


/**
 * A default implementation of tests zip installer.
 */
public class DefaultTestsZipInstaller implements ITestsZipInstaller {
    static final String LOG_TAG = "DefaultTestsZipInstaller";

    /**
     * A list of /data subdirectories to NOT wipe when doing UserDataFlashOption.TESTS_ZIP
     */
    private Set<String> mDataWipeSkipList;

    /**
     * @param skipList A list of file names to keep when cleaning everything under userdata.
     */
    public DefaultTestsZipInstaller(String... skipList) {
        mDataWipeSkipList = new HashSet<String>(skipList.length);
        mDataWipeSkipList.addAll(Arrays.asList(skipList));
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation will reboot the device into userland before
     * proceeding. It will also stop the Android runtime and leave it down upon return
     */
    public void pushTestsZipOntoData(ITestDevice device, IDeviceBuildInfo deviceBuild)
            throws DeviceNotAvailableException, TargetSetupError {
        Log.i(LOG_TAG, String.format("Pushing test zips content onto userdata on %s",
                device.getSerialNumber()));

        device.rebootUntilOnline();

        // TODO: if any of the following commands fail, need to instruct ITestDevice to only recover
        // until device is online, not until its available

        // Stop the runtime, so it doesn't notice us mucking with the filesystem
        Log.d(LOG_TAG, "Stopping runtime");
        device.executeShellCommand("stop");

        Log.d(LOG_TAG, String.format("Cleaning %s", FileListingService.DIRECTORY_DATA));
        IFileEntry dataEntry = device.getFileEntry(FileListingService.DIRECTORY_DATA);
        if (dataEntry == null) {
            throw new TargetSetupError(String.format("Could not find %s folder on %s",
                    FileListingService.DIRECTORY_DATA, device.getSerialNumber()));
        }
        for (IFileEntry dataSubDir : dataEntry.getChildren(false)) {
            if (!mDataWipeSkipList.contains(dataSubDir.getName())) {
                device.executeShellCommand(String.format("rm -r %s",
                        dataSubDir.getFullEscapedPath()));
            }
        }

        // Unpack the tests zip somewhere and install the contents
        File unzipDir = null;
        try {
            unzipDir = FileUtil.createTempDir("tests-zip_");
            extractZip(deviceBuild, unzipDir);

            Log.d(LOG_TAG, "Syncing test files/apks");
            File hostDir = new File(unzipDir, "DATA");

            File[] hostDataFiles = getTestsZipDataFiles(hostDir);
            for (File hostSubDir : hostDataFiles) {
                device.syncFiles(hostSubDir, FileListingService.DIRECTORY_DATA);
            }

            // after push, everything in /data is owned by root, need to revert to system
            for (IFileEntry dataSubDir : dataEntry.getChildren(false)) {
                if (!mDataWipeSkipList.contains(dataSubDir.getName())) {
                    // change owner to system, no -R support
                    device.executeShellCommand(String.format("chown system.system %s %s/*",
                            dataSubDir.getFullEscapedPath(), dataSubDir.getFullEscapedPath()));
                }
            }
        } catch (IOException e) {
            throw new TargetSetupError(e.getMessage());
        } finally {
            // Clean up the unpacked zip directory
            FileUtil.recursiveDelete(unzipDir);
        }
    }

    /**
     * Retrieves the set of files contained in given tests.zip/DATA directory.
     * <p/>
     * Exposed so unit tests can mock.
     *
     * @param hostDir the {@link File} directory, representing the local path extracted tests.zip
     *            contents 'DATA' sub-folder
     * @return array of {@link File}
     */
    File[] getTestsZipDataFiles(File hostDir) throws TargetSetupError {
        if (!hostDir.isDirectory()) {
            throw new TargetSetupError("Unrecognized tests.zip content: missing DATA folder");
        }
        File[] childFiles = hostDir.listFiles();
        if (childFiles == null || childFiles.length <= 0) {
            throw new TargetSetupError(
                    "Unrecognized tests.zip content: DATA folder has no content");
        }
        return childFiles;
    }

    /**
     * Extract the tests zip to local disk.
     * <p/>
     * Exposed so unit tests can mock
     */
    void extractZip(IDeviceBuildInfo deviceBuild, File unzipDir) throws IOException,
            ZipException, TargetSetupError {
        if (deviceBuild.getTestsZipFile() == null) {
            throw new TargetSetupError("Missing tests.zip file");
        }
        FileUtil.extractZip(new ZipFile(deviceBuild.getTestsZipFile()), unzipDir);
    }
}