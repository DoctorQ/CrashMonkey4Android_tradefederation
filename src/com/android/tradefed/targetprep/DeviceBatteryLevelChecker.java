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

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;

import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * An {@link ITargetPreparer} that checks for a minimum battery charge, and waits for the battery
 * to reach a second charging threshold if the minimum charge isn't present.
 */
@OptionClass(alias = "battery-checker")
public class DeviceBatteryLevelChecker implements ITargetPreparer {

    @Option(name="min-level", description="Charge level below which we force the device to sit " +
            "and charge.  Range: 0-100.")
    private int mMinChargeLevel = 20;

    @Option(name="resume-level", description="Charge level at which we release the device to " +
            "begin testing again. Range: 0-100.")
    private int mResumeLevel = 80;

    private static final Pattern BATTERY_LEVEL = Pattern.compile("\\s*level: (\\d+)");
    /** poll the battery level every 5 minutes while the device is charging */
    private static final long CHARGING_POLL_TIME = 5 * 60 * 1000;

    private Integer checkBatteryLevel(ITestDevice device) throws DeviceNotAvailableException {
        // FIXME: scale the battery level by "scale" instead of assuming 100
        String dumpsys = device.executeShellCommand("dumpsys battery");
        if (dumpsys != null) {
            String[] lines = dumpsys.split("\r?\n");
            for (String line : lines) {
                Matcher m = BATTERY_LEVEL.matcher(line);
                if (m.matches()) {
                    try {
                        return Integer.parseInt(m.group(1));
                    } catch (NumberFormatException e) {
                        CLog.w("Failed to parse %s as an integer", m.group(1));
                    }
                }
            }
        }
        CLog.w("Failed to determine battery level for device %s.  `dumpsys battery` was: %s",
                device.getSerialNumber(), dumpsys);
        return null;
    }

    /**
     * {@inheritDoc}
     */
    public void setUp(ITestDevice device, IBuildInfo buildInfo) throws TargetSetupError, BuildError,
            DeviceNotAvailableException {
        Integer batteryLevel = checkBatteryLevel(device);
        if (batteryLevel == null) {
            // we already logged a warning
            return;
        } else if (batteryLevel < mMinChargeLevel) {
            // Time-out.  Send the device to the corner
            CLog.w("Battery level %d is below the min level %d; holding for device %s to charge " +
                    "to level %d", batteryLevel, mMinChargeLevel, device.getSerialNumber(),
                    mResumeLevel);
        } else {
            // Good to go
            CLog.d("Battery level %d is above the minimum of %d; %s is good to go.", batteryLevel,
                    mMinChargeLevel, device.getSerialNumber());
            return;
        }

        // If we're down here, it's time to hold the device until it reaches mResumeLevel
        while (batteryLevel != null && batteryLevel < mResumeLevel) {
            // FIXME show periodic status messages with "w" log level
            getRunUtil().sleep(CHARGING_POLL_TIME);
            Integer newLevel = checkBatteryLevel(device);
            CLog.d("Battery level for device %s is now %d", device.getSerialNumber(), newLevel);
            batteryLevel = newLevel;
        }
        CLog.w("Device %s is now charged to battery level %d; releasing.", device.getSerialNumber(),
                batteryLevel);
    }

    /**
     * Get a RunUtil instance
     * <p />
     * Exposed for unit testing
     */
    IRunUtil getRunUtil() {
        return RunUtil.getDefault();
    }
}

