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

import com.android.ddmlib.Log;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.Option.Importance;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;

/**
 * A {@link ITargetPreparer} that installs one or more apks located on the filesystem.
 */
@OptionClass(alias = "install-apk")
public class InstallApkSetup implements ITargetPreparer {

    private static final String LOG_TAG = InstallApkSetup.class.getSimpleName();

    @Option(name = "apk-path", description =
        "the filesystem path of the apk to install. Can be repeated.",
        importance = Importance.IF_UNSET)
    private Collection<File> mApkPaths = new ArrayList<File>();

    /**
     * {@inheritDoc}
     */
    @Override
    public void setUp(ITestDevice device, IBuildInfo buildInfo) throws TargetSetupError,
            BuildError, DeviceNotAvailableException {
        for (File apk : mApkPaths) {
            if (!apk.exists()) {
                throw new TargetSetupError(String.format("%s does not exist",
                        apk.getAbsolutePath()));
            }
            Log.i(LOG_TAG, String.format("Installing %s on %s", apk.getName(),
                    device.getSerialNumber()));
            String result = device.installPackage(apk, true);
            if (result != null) {
                Log.e(LOG_TAG, String.format("Failed to install %s on device %s. Reason: %s",
                        apk.getAbsolutePath(), device.getSerialNumber(), result));
            }
        }
    }
}
