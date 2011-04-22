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
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;

/**
 * A {@link ITargetPreparer} that installs an apk and its tests.
 */
public class AppSetup implements ITargetPreparer {

    private static final String LOG_TAG = "AppSetup";

    @Option(name="app-package-name", description="the package name(s) of app and tests. " +
            "Used for device cleanup of old packages before starting tests.")
    private Collection<String> mAppPackageNames = new ArrayList<String>();

    @Option(name="reboot", description="reboot device during setup.")
    private boolean mReboot = true;

    /**
     * {@inheritDoc}
     */
    public void setUp(ITestDevice device, IBuildInfo buildInfo) throws TargetSetupError,
            DeviceNotAvailableException {
        if (!(buildInfo instanceof IAppBuildInfo)) {
            throw new IllegalArgumentException("Provided buildInfo is not a AppBuildInfo");
        }
        IAppBuildInfo appBuild = (IAppBuildInfo)buildInfo;
        Log.i(LOG_TAG, String.format("Performing setup on %s", device.getSerialNumber()));
        for (String packageName : mAppPackageNames) {
            device.uninstallPackage(packageName);
        }

        if (mReboot) {
            // reboot device to get a clean state
            device.reboot();
        }

        for (File apkFile : appBuild.getAppPackageFiles()) {
            device.installPackage(apkFile, true);
        }
    }

}
