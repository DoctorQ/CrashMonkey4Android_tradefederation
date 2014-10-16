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

import com.android.tradefed.build.IAppBuildInfo;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.VersionedFile;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.AaptParser;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

/**
 * A {@link ITargetPreparer} that installs an apk and its tests.
 * <p/>
 * Requires 'aapt' on PATH when --uninstall is set
 */
@OptionClass(alias="app-setup")
public class AppSetup implements ITargetPreparer, ITargetCleaner {

    @Option(name="reboot", description="reboot device after running tests.")
    private boolean mReboot = true;

    @Option(name = "install", description = "install all apks in build.")
    private boolean mInstall = true;

    @Option(name = "uninstall", description =
            "uninstall only apks in build after test completes.")
    private boolean mUninstall = true;

    @Option(name = "uninstall-all", description =
            "uninstall all unnstallable apks found on device after test completes.")
    private boolean mUninstallAll = false;

    @Option(name = "skip-uninstall-pkg", description =
            "force retention of this package when --uninstall-all is set.")
    private Set<String> mSkipUninstallPkgs = new HashSet<String>();

    /** contains package names of installed apps. Used for uninstall */
    private Set<String> mInstalledPkgs = new HashSet<String>();

    /**
     * {@inheritDoc}
     */
    @Override
    public void setUp(ITestDevice device, IBuildInfo buildInfo) throws TargetSetupError,
            DeviceNotAvailableException {
        if (!(buildInfo instanceof IAppBuildInfo)) {
            throw new IllegalArgumentException("Provided buildInfo is not a AppBuildInfo");
        }
        IAppBuildInfo appBuild = (IAppBuildInfo)buildInfo;
        CLog.i("Performing setup on %s", device.getSerialNumber());

        // double check that device is clean, in case it has unexpected cruft on it
        if (mUninstallAll && !uninstallAllApps(device)) {
            // cannot cleanup device! Bad things may happen in future tests. Take device out
            // of service
            // TODO: in future, consider doing more sophisticated recovery operations
            throw new DeviceNotAvailableException(String.format(
                    "Failed to uninstall apps on %s", device.getSerialNumber()));
        }

        if (mInstall) {
            for (VersionedFile apkFile : appBuild.getAppPackageFiles()) {
                String result = device.installPackage(apkFile.getFile(), true);
                if (result != null) {
                    throw new TargetSetupError(String.format(
                            "Failed to install %s on %s. Reason: %s",
                            apkFile.getFile().getName(), device.getSerialNumber(), result));
                }
                if (mUninstall && !mUninstallAll) {
                    addPackageNameToUninstall(apkFile.getFile());
                }
            }
        }

    }

    private void addPackageNameToUninstall(File apkFile) throws TargetSetupError {
        AaptParser aaptParser = AaptParser.parse(apkFile);
        if (aaptParser == null) {
            throw new TargetSetupError(String.format("Failed to extract info from '%s' using aapt",
                    apkFile.getAbsolutePath()));
        }
        if (aaptParser.getPackageName() == null) {
            throw new TargetSetupError(String.format(
                    "Failed to find package name for '%s' using aapt", apkFile.getAbsolutePath()));
        }
        mInstalledPkgs.add(aaptParser.getPackageName());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void tearDown(ITestDevice device, IBuildInfo buildInfo, Throwable e)
            throws DeviceNotAvailableException {
        // reboot device before uninstalling apps, in case device is wedged
        if (mReboot) {
            device.reboot();
        }
        if (mUninstall && !mUninstallAll) {
            for (String pkgName : mInstalledPkgs) {
                String result = device.uninstallPackage(pkgName);
                if (result != null) {
                    CLog.e("Failed to uninstall %s: %s", pkgName, result);
                    // TODO: consider throwing here
                }
            }
        }
        if (mUninstallAll && !uninstallAllApps(device)) {
            // cannot cleanup device! Bad things may happen in future tests. Take device out
            // of service
            // TODO: in future, consider doing more sophisticated recovery operations
            throw new DeviceNotAvailableException(String.format(
                    "Failed to uninstall apps on %s", device.getSerialNumber()));
        }
    }

    private boolean uninstallAllApps(ITestDevice device) throws DeviceNotAvailableException {
        // make multiple attempts to uninstall apps, aborting if failed
        // TODO: consider moving this to ITestDevice, so more sophisticated recovery attempts
        // can be performed
        for (int i = 0; i < 3; i++) {
            Set<String> pkgs = getAllAppsToUninstall(device);
            if (pkgs.isEmpty()) {
                return true;
            }
            for (String pkg : pkgs) {
                String result = device.uninstallPackage(pkg);
                if (result != null) {
                    CLog.w("Uninstall of %s on %s failed: %s", pkg, device.getSerialNumber(),
                            result);
                }
            }
        }
        // check getAppsToUninstall one more time, cause last attempt through loop might have been
        // successful
        return getAllAppsToUninstall(device).isEmpty();
    }

    private Set<String> getAllAppsToUninstall(ITestDevice device) throws DeviceNotAvailableException {
        Set<String> pkgs = device.getUninstallablePackageNames();
        pkgs.removeAll(mSkipUninstallPkgs);
        return pkgs;
    }
}
