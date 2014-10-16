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

/**
 * A {@link ITargetPreparer} that configures wifi on the device if necessary.
 * <p/>
 * Unlike {@link DeviceSetup}, this preparer works when adb is not root aka user builds.
 */
public class WifiPreparer implements ITargetPreparer {

    @Option(name="wifi-network", description="the name of wifi network to connect to.",
            mandatory=true)
    private String mWifiNetwork = null;

    @Option(name="wifi-psk", description="WPA-PSK passphrase of wifi network to connect to.")
    private String mWifiPsk = null;

    @Option(name = "wifi-attempts", description =
        "maximum number of attempts to connect to wifi network.")
    private int mWifiAttempts = 2;

    /**
     * {@inheritDoc}
     */
    @Override
    public void setUp(ITestDevice device, IBuildInfo buildInfo) throws TargetSetupError,
            BuildError, DeviceNotAvailableException {
        for (int i=1; i <= mWifiAttempts; i++) {
            if (!device.connectToWifiNetworkIfNeeded(mWifiNetwork, mWifiPsk)) {
                CLog.w("Failed to connect to wifi network %s on %s on attempt %d of %d",
                        mWifiNetwork, device.getSerialNumber(), i, mWifiAttempts);

            } else {
                CLog.w("Successfully connected to wifi network %s on %s",
                        mWifiNetwork, device.getSerialNumber());
                return;
            }
        }
        throw new TargetSetupError(String.format("Failed to connect to wifi network %s on %s",
                mWifiNetwork, device.getSerialNumber()));
    }
}
