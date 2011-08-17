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
package com.android.wireless.tests;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;

import java.util.Arrays;


/**
 * Helper class to get device radio settings
 */
public class RadioHelper {
    private final static String[] PING_SERVER_LIST = {"www.google.com", "www.facebook.com",
        "www.bing.com", "www.ask.com", "www.yahoo.com"};

    /**
     * get phone type: current support type: 0, 1, 2
     */
    private static int getPhoneType(ITestDevice device) throws DeviceNotAvailableException {
        String phoneType = device.getPropertySync("gsm.current.phone-type");
        if (phoneType == null || phoneType.isEmpty()) {
            return 0; // phone type is unknown
        } else {
            return Integer.parseInt(phoneType);
        }
    }

    /**
     * Verify whether the device is a phone or tablet
     * @param device
     * @param nonPhoneDevices is a string with product type separated by ", "
     * @return
     * @throws DeviceNotAvailableException
     */
    public static boolean isVoiceCapable(ITestDevice device, String nonePhoneDevices)
        throws DeviceNotAvailableException {
        String productType = device.getProductType();
        CLog.d("productType: " + productType);
        String[] noneVoiceDevice = nonePhoneDevices.split(", ");
        CLog.d("None voice-capable devices: ");
        for (int i = 0; i < noneVoiceDevice.length; i++) {
            CLog.d("%s ", noneVoiceDevice[i]);
        }
        if (productType == null || Arrays.asList(noneVoiceDevice).contains(productType)) {
            return false;
        }
        return true;
    }

    /**
     * Verify whether a device is a GSM phone
     * @param device
     * @return true for GSM phone, false for others
     * @throws DeviceNotAvailableException
     */
    public static boolean isGsmPhone(ITestDevice device) throws DeviceNotAvailableException {
        if (getPhoneType(device) == 1) {
            return true;
        }
        return false;
    }

    /**
     * Verify whether a device is a CDMA phone
     * @param device
     * @return true for CDMA phone
     * @throws DeviceNotAvailableException
     */
    public static boolean isCdmaPhone(ITestDevice device) throws DeviceNotAvailableException {
        if (getPhoneType(device) == 2) {
            return true;
        }
        return false;
    }

    public static void resetBootComplete(ITestDevice device) throws DeviceNotAvailableException {
        device.executeShellCommand("setprop dev.bootcomplete 0");
    }

    public static boolean waitForBootComplete(ITestDevice device)
        throws DeviceNotAvailableException {
        long deviceBootTime = 5 * 60 * 1000;
        long startTime = System.currentTimeMillis();
        while ((System.currentTimeMillis() - startTime) < deviceBootTime) {
            String output = device.executeShellCommand("getprop dev.bootcomplete");
            output = output.replace('#', ' ').trim();
            if (output.equals("1")) {
                return true;
            }
            try {
                Thread.sleep(5 * 1000);
            } catch (Exception e) {
                CLog.d("thread is interrupted: %s", e.toString());
            }
        }
        return false;
    }

    public static boolean pingTest(ITestDevice device) throws DeviceNotAvailableException {
        String failString = "ping: unknown host";
        // assume the chance that all servers are down is very small
        for (int i = 0; i < PING_SERVER_LIST.length; i++ ) {
            String host = PING_SERVER_LIST[i];
            CLog.d("Start ping test, ping %s", host);
            String res = device.executeShellCommand("ping -c 10 -w 100 " + host);
            CLog.d("res: %s", res);
            if (!res.contains(failString)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Activate a device if it hasn't been activated yet
     * @param device
     * @return true
     * @throws DeviceNotAvailableException
     */
    public static boolean radioActivation(ITestDevice device) throws DeviceNotAvailableException {
        if (isGsmPhone(device) || pingTest(device)) {
            // for GSM device, CDMA/LTE device that is already activated
            return true;
        }

        int retries = 3;
        for (int i = 0; i < retries; i++ ) {
            device.executeShellCommand("radiooptions 8 *22899");
            try {
                Thread.sleep(5*60*1000); // wait for 5 minutes
            } catch (Exception e) {
                CLog.d("interrupted while in sleep %s", e);
            }
            if (pingTest(device)) {
                return true;
            }
        }
        return false;
    }
}
