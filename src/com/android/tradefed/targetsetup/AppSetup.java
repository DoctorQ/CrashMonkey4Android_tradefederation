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
package com.android.tradefed.targetsetup;

import com.android.ddmlib.Log;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;

/**
 * A {@link ITargetPreparer} that installs an apk and its tests.
 */
public class AppSetup implements ITargetPreparer {

    private static final String LOG_TAG = "AppSetup";

    @Option(name="app-package-name", description="the package name of app")
    private String mAppPackageName = null;

    @Option(name="test-package-name", description="the package name of tests")
    private String mTestPackageName= null;

    @Option(name="reboot", description="reboot device during setup")
    private boolean mReboot = true;

    /**
     * {@inheritDoc}
     */
    public void setUp(ITestDevice device, IBuildInfo buildInfo) throws TargetSetupError,
            DeviceNotAvailableException {
        if (!(buildInfo instanceof AppBuildInfo)) {
            throw new IllegalArgumentException("Provided buildInfo is not a AppBuildInfo");
        }
        if (mAppPackageName == null || mTestPackageName == null) {
            throw new IllegalArgumentException(
                    "Missing app-package-name or test-package-name options");
        }
        Log.i(LOG_TAG, String.format("Performing setup on %s", device.getSerialNumber()));
        device.uninstallPackage(mAppPackageName);
        device.uninstallPackage(mTestPackageName);
        if (mReboot) {
            // reboot device to get a clean state
            device.reboot();
        }
        AppBuildInfo appBuild = (AppBuildInfo)buildInfo;
        device.installPackage(appBuild.getAppPackageFile(), true);
        device.installPackage(appBuild.getTestPackageFile(), true);

        // TODO: consider adding 'cleanup' step that would uninstall apks when test is finished
    }
}
