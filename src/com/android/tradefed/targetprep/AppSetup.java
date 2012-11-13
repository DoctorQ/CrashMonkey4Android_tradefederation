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

import com.android.ddmlib.Log;
import com.android.tradefed.build.IAppBuildInfo;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.VersionedFile;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;

import java.util.Set;

/**
 * A {@link ITargetPreparer} that installs an apk and its tests.
 */
public class AppSetup implements ITargetPreparer, ITargetCleaner{

    private static final String LOG_TAG = "AppSetup";

    @Option(name="reboot", description="reboot device during setup.")
    private boolean mReboot = true;

    @Option(name="uninstall", description="uninstall all apks after test completes.")
    private boolean mUninstall = true;

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
        Log.i(LOG_TAG, String.format("Performing setup on %s", device.getSerialNumber()));

        if (mReboot) {
            // reboot device to get a clean state
            device.reboot();
        }

        for (VersionedFile apkFile : appBuild.getAppPackageFiles()) {
            String result = device.installPackage(apkFile.getFile(), true);
            if (result != null) {
                throw new TargetSetupError(String.format("Failed to install %s on %s. Reason: %s",
                        apkFile.getFile().getName(), device.getSerialNumber(), result));
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void tearDown(ITestDevice device, IBuildInfo buildInfo, Throwable e)
            throws DeviceNotAvailableException {
        if (mUninstall) {
            Set<String> pkgs = device.getInstalledNonSystemPackageNames();
            for (String pkg : pkgs) {
                String result = device.uninstallPackage(pkg);
                if (result != null) {
                    CLog.w("Uninstall of %s on %s failed: %s", pkg, device.getSerialNumber(),
                            result);
                }
            }
        }
    }
}
