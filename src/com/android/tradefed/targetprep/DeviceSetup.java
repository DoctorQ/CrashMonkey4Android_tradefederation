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
import com.android.ddmlib.Log;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.util.FileUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

/**
 * A {@link ITargetPreparer} that configures a device for testing based on provided {@link Option}s.
 * <p/>
 * Should be performed *after* a new build is flashed.
 */
public class DeviceSetup implements ITargetPreparer {

    private static final String LOG_TAG = "DeviceSetup";

    @Option(name="wifi-network", description="the name of wifi network to connect to")
    private String mWifiNetwork = null;

    @Option(name="wifi-psk", description="WPA-PSK passphrase of wifi network to connect to")
    private String mWifiPsk = null;

    @Option(name="min-external-store-space", description="the minimum amount of free space in KB" +
            "that must be present on device's external storage")
    // require 500K by default. Values <=0 mean external storage is not required
    private long mMinExternalStoreSpace = 500;

    @Option(name = "local-data-path",
            description = "optional local file path of test data to sync to device's external " +
            "storage. Use --remote-data-path to set remote location")
    private File mLocalDataFile = null;

    @Option(name = "remote-data-path",
            description = "optional file path on device's external storage to sync test data. " +
            "Must be used with --local-data-path")
    private String mRemoteDataPath = null;

    @Option(name="disable-dialing", description="set disable dialing property on boot")
    private boolean mDisableDialing = true;

    @Option(name="set-test-harness", description="set the read-only test harness flag on boot. " +
            "Requires adb root. Default true.")
    private boolean mSetTestHarness = true;

    @Option(name="audio-silent", description="set ro.audio.silent on boot")
    private boolean mSetAudioSilent = true;

    @Option(name="disable-dalvik-verifier", description="disable the dalvik verifier on device. "
        + "Allows package-private framework tests to run.")
    private boolean mDisableDalvikVerifier = false;

    @Option(name="setprop", description="set the specified property on boot.  " +
            "Format: --setprop key=value.  May be repeated.")
    private Collection<String> mSetProps = new ArrayList<String>();

    /**
     * Sets the local data path to use
     * <p/>
     * Exposed for unit testing
     */
    void setLocalDataPath(File localPath) {
        mLocalDataFile = localPath;
    }

    /**
     * Sets the remote data path to use
     * <p/>
     * Exposed for unit testing
     */
    void setRemoteDataPath(String remotePath) {
        mRemoteDataPath = remotePath;
    }

    /**
     * Sets the wifi network ssid to setup.
     * <p/>
     * Exposed for unit testing
     */
    void setWifiNetwork(String network) {
        mWifiNetwork = network;
    }

    /**
     * Sets the minimum external store space
     * <p/>
     * Exposed for unit testing
     */
    void setMinExternalStoreSpace(int minKBytes) {
        mMinExternalStoreSpace = minKBytes;
    }

    /**
     * Adds a property to the list of properties to set
     * <p/>
     * Exposed for unit testing
     */
    void addSetProperty(String prop) {
        mSetProps.add(prop);
    }

    /**
     * {@inheritDoc}
     */
    public void setUp(ITestDevice device, IBuildInfo buildInfo) throws TargetSetupError,
            DeviceNotAvailableException, BuildError {
        Log.i(LOG_TAG, String.format("Performing setup on %s", device.getSerialNumber()));

        device.enableAdbRoot();

        configureSystemProperties(device);

        keepScreenOn(device);

        connectToWifi(device);

        syncTestData(device);

        checkExternalStoreSpace(device);

        // postBootSetup will disable keyguard - this must be done before attempting to clear error
        // dialogs
        device.postBootSetup();

        device.clearErrorDialogs();
    }

    /**
     * Configures device system properties.
     * <p/>
     * Device will be rebooted if any property is changed.
     *
     * @param device
     * @throws TargetSetupError
     * @throws DeviceNotAvailableException
     */
    private void configureSystemProperties(ITestDevice device) throws TargetSetupError,
            DeviceNotAvailableException {
        // build the local.prop file contents with properties to change
        StringBuilder propertyBuilder = new StringBuilder();
        if (mDisableDialing) {
            propertyBuilder.append("ro.telephony.disable-call=true\n");
        }
        if (mSetTestHarness) {
            // set both ro.monkey and ro.test_harness, for compatibility with older platforms
            propertyBuilder.append("ro.monkey=1\n");
            propertyBuilder.append("ro.test_harness=1\n");
        }
        if (mSetAudioSilent) {
            propertyBuilder.append("ro.audio.silent=1\n");
        }
        if (mDisableDalvikVerifier) {
            propertyBuilder.append("dalvik.vm.dexopt-flags=v\n");
        }
        for (String prop : mSetProps) {
            propertyBuilder.append(prop);
            propertyBuilder.append("\n");
        }
        if (propertyBuilder.length() > 0) {
            // create a local.prop file, and push it to /data/local.prop
            File localFile = createTempFile("local.prop", propertyBuilder.toString());
            try {
                boolean result = device.pushFile(localFile, "/data/local.prop");
                if (!result) {
                    throw new TargetSetupError(String.format("Failed to push file to %s",
                            device.getSerialNumber()));
                }
            } finally {
                localFile.delete();
            }
            Log.i(LOG_TAG, String.format(
                    "Setup requires system property change. Reboot of %s required",
                    device.getSerialNumber()));
            device.reboot();
        }
    }

    /**
     * @param device
     * @throws DeviceNotAvailableException
     */
    private void keepScreenOn(ITestDevice device) throws DeviceNotAvailableException {
        device.executeShellCommand("svc power stayon true");
    }

    /**
     * Check that device external store has the required space
     *
     * @param device
     * @throws DeviceNotAvailableException if device does not have required space
     */
    private void checkExternalStoreSpace(ITestDevice device) throws DeviceNotAvailableException {
        if (mMinExternalStoreSpace > 0) {
            long freeSpace = device.getExternalStoreFreeSpace();
            if (freeSpace < mMinExternalStoreSpace) {
                throw new DeviceNotAvailableException(String.format(
                        "External store free space %dK is less than required %dK for device %s",
                        freeSpace , mMinExternalStoreSpace, device.getSerialNumber()));
            }
        }
    }

    /**
     * Connect to wifi network if specified
     *
     * @param device
     * @throws DeviceNotAvailableException
     * @throws TargetSetupError if failed to connect to wifi
     */
    private void connectToWifi(ITestDevice device) throws DeviceNotAvailableException,
            TargetSetupError {
        if (mWifiNetwork != null) {
            if (device.connectToWifiNetwork(mWifiNetwork, mWifiPsk)) {
                Log.i(LOG_TAG, String.format("Connected to wifi network %s", mWifiNetwork));
            } else {
                throw new TargetSetupError(String.format(
                        "Failed to connect to wifi network %s on %s", mWifiNetwork,
                        device.getSerialNumber()));
            }
        }
    }

    /**
     * Syncs a set of test data files, specified via local-data-path, to devices external storage.
     *
     * @param device the {@link ITestDevice} to sync data to
     * @throws TargetSetupError if data fails to sync
     */
    void syncTestData(ITestDevice device) throws TargetSetupError, DeviceNotAvailableException {
        if (mLocalDataFile != null) {
            if (!mLocalDataFile.exists() || !mLocalDataFile.isDirectory()) {
                throw new TargetSetupError(String.format("local-data-path %s is not a directory",
                        mLocalDataFile.getAbsolutePath()));

            }
            String fullRemotePath = device.getIDevice().getMountPoint(IDevice.MNT_EXTERNAL_STORAGE);
            if (mRemoteDataPath != null) {
                fullRemotePath = String.format("%s/%s", fullRemotePath, mRemoteDataPath);
            }
            boolean result = device.syncFiles(mLocalDataFile, fullRemotePath);
            if (!result) {
                // TODO: get exact error code and respond accordingly
                throw new DeviceNotAvailableException(String.format(
                        "failed to sync test data from " + "local-data-path %s to %s on device %s",
                        mLocalDataFile.getAbsolutePath(), fullRemotePath,
                        device.getSerialNumber()));
            }
        }
    }

    /**
     * Creates a local temporary file with the given contents
     *
     * @param fileName the file name prefix
     * @param fileContents the file contents
     * @return the created {@link File}
     * @throws TargetSetupError if file could not be created
     */
    private File createTempFile(String fileName, String fileContents) throws TargetSetupError {
        File tmpFile = null;
        try {
            tmpFile = FileUtil.createTempFile(fileName, ".txt");
            FileUtil.writeToFile(fileContents, tmpFile);
            return tmpFile;
        } catch (IOException e) {
            Log.e(LOG_TAG, e);
            if (tmpFile != null) {
                tmpFile.delete();
            }
            throw new TargetSetupError("Failed to create local temp file", e);
        }
    }
}
