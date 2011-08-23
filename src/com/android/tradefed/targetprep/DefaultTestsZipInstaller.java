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
import com.android.tradefed.build.IDeviceBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.IFileEntry;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.ITestDevice.RecoveryMode;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.FileUtil;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;


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
     * This convenience constructor allows the caller to set the skip list directly, rather than
     * needing to call {@link #setDataWipeSkipList} separately.
     *
     * @param skipList The list of paths under {@code /data} to keep when clearing the filesystem
     * @see #setDataWipeSkipList
     */
    public DefaultTestsZipInstaller(String... skipList) {
        setDataWipeSkipList(skipList);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setDataWipeSkipList(String... skipList) {
        mDataWipeSkipList = new HashSet<String>(skipList.length);
        mDataWipeSkipList.addAll(Arrays.asList(skipList));
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation will reboot the device into userland before
     * proceeding. It will also stop the Android runtime and leave it down upon return
     */
    @Override
    public void pushTestsZipOntoData(ITestDevice device, IDeviceBuildInfo deviceBuild)
            throws DeviceNotAvailableException, TargetSetupError {
        CLog.i(String.format("Pushing test zips content onto userdata on %s",
                device.getSerialNumber()));

        // Stop the runtime, so it doesn't notice us mucking with the filesystem
        device.executeShellCommand("stop");
        deleteData(device);

        CLog.d("Syncing test files/apks");
        File hostDir = new File(deviceBuild.getTestsDir(), "DATA");

        File[] hostDataFiles = getTestsZipDataFiles(hostDir);
        for (File hostSubDir : hostDataFiles) {
            device.syncFiles(hostSubDir, FileListingService.DIRECTORY_DATA);
        }

        File deviceRootPath = new File(FileListingService.FILE_SEPARATOR
                + FileListingService.DIRECTORY_DATA);
        for (File dir : findDirs(hostDir, deviceRootPath)) {
            device.executeShellCommand("chown system.system " + dir.getPath());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteData(ITestDevice device) throws DeviceNotAvailableException,
            TargetSetupError {
        RecoveryMode cachedRecoveryMode = device.getRecoveryMode();
        device.setRecoveryMode(RecoveryMode.ONLINE);

        CLog.d("clearing " + FileListingService.DIRECTORY_DATA + " directory on device "
                + device.getSerialNumber());

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

        device.setRecoveryMode(cachedRecoveryMode);
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
     * Indirection to {@link FileUtil#findDirsUnder(File, File)} to allow for unit testing.
     */
    Set<File> findDirs(File hostDir, File deviceRootPath) {
        return FileUtil.findDirsUnder(hostDir, deviceRootPath);
    }
}
