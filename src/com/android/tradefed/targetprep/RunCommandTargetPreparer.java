/*
 * Copyright (C) 2012 The Android Open Source Project
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

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;

import java.util.ArrayList;
import java.util.List;

public class RunCommandTargetPreparer implements ITargetCleaner {
    @Option(name = "run-command", description = "adb shell command to run")
    private List<String> mCommands = new ArrayList<String>();

    @Option(name = "teardown-command", description = "adb shell command to run at teardown time")
    private List<String> mTeardownCommands = new ArrayList<String>();

    /**
     * {@inheritDoc}
     */
    @Override
    public void setUp(ITestDevice device, IBuildInfo buildInfo) throws TargetSetupError,
            DeviceNotAvailableException {
        for (String cmd : mCommands) {
            // If the command had any output, the executeShellCommand method will log it at the
            // VERBOSE level; so no need to do any logging from here.
            CLog.d("About to run setup command on device %s: %s", device.getSerialNumber(), cmd);
            device.executeShellCommand(cmd);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void tearDown(ITestDevice device, IBuildInfo buildInfo, Throwable e)
            throws DeviceNotAvailableException {
        for (String cmd : mTeardownCommands) {
            // If the command had any output, the executeShellCommand method will log it at the
            // VERBOSE level; so no need to do any logging from here.
            CLog.d("About to run tearDown command on device %s: %s", device.getSerialNumber(), cmd);
            device.executeShellCommand(cmd);
        }
    }
}

