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

import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.ShellCommandUnresponsiveException;
import com.android.ddmlib.TimeoutException;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;

import java.io.IOException;

/**
 * An {@link ITargetPreparer} that checks for a minimum battery charge, and waits for the battery
 * to reach a second charging threshold if the minimum charge isn't present.
 */
@OptionClass(alias = "battery-checker")
public class DeviceBatteryLevelChecker implements ITargetPreparer {

    // FIXME: get rid of this once we're sure nothing is using it
    @Option(name = "min-level", description = "Obsolete.  Use --max-battery.")
    private Integer mMinChargeLevel = null;

    @Option(name = "max-battery", description = "Charge level below which we force the device to " +
            "sit and charge.  Range: 0-100.")
    private Integer mMaxBattery = 10;

    @Option(name = "resume-level", description = "Charge level at which we release the device to " +
            "begin testing again. Range: 0-100.")
    private int mResumeLevel = 80;

    @Option(name = "poll-time", description = "Time in minutes to wait between battery level " +
            "polls. Decimal times accepted.")
    private double mChargingPollTime = 1.0;

    Integer checkBatteryLevel(ITestDevice device) throws DeviceNotAvailableException {
        try {
            IDevice idevice = device.getIDevice();
            return idevice.getBatteryLevel();
        } catch (AdbCommandRejectedException e) {
            return null;
        } catch (IOException e) {
            return null;
        } catch (TimeoutException e) {
            return null;
        } catch (ShellCommandUnresponsiveException e) {
            return null;
        }
    }

    /**
     * {@inheritDoc}
     */
    public void setUp(ITestDevice device, IBuildInfo buildInfo) throws TargetSetupError, BuildError,
            DeviceNotAvailableException {
        if (mMinChargeLevel != null) {
            CLog.w("The obsolete --min-level was specified.  Please use --min-battery instead.");
            mMaxBattery = mMinChargeLevel;
        }

        Integer batteryLevel = checkBatteryLevel(device);
        if (batteryLevel == null) {
            CLog.w("Failed to determine battery level for device %s.", device.getSerialNumber());
            return;
        } else if (batteryLevel < mMaxBattery) {
            // Time-out.  Send the device to the corner
            CLog.w("Battery level %d is below the min level %d; holding for device %s to charge " +
                    "to level %d", batteryLevel, mMaxBattery, device.getSerialNumber(),
                    mResumeLevel);
        } else {
            // Good to go
            CLog.d("Battery level %d is above the minimum of %d; %s is good to go.", batteryLevel,
                    mMaxBattery, device.getSerialNumber());
            return;
        }

        // If we're down here, it's time to hold the device until it reaches mResumeLevel
        while (batteryLevel != null && batteryLevel < mResumeLevel) {
            // FIXME show periodic status messages with "w" log level
            getRunUtil().sleep((long) (mChargingPollTime * 60 * 1000));
            Integer newLevel = checkBatteryLevel(device);
            if (newLevel == null) {
                // weird
                CLog.w("Breaking out of wait loop because battery level read failed for device %s",
                        device.getSerialNumber());
                break;
            } else if (newLevel < batteryLevel) {
                // also weird
                CLog.w("Warning: battery discharged from %d to %d on device %s during the last " +
                        "%d minutes.", batteryLevel, newLevel, device.getSerialNumber(),
                        mChargingPollTime);
            } else {
                CLog.d("Battery level for device %s is now %d", device.getSerialNumber(), newLevel);
            }
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

