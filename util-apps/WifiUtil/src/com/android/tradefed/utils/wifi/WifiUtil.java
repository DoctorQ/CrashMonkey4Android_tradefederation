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

package com.android.tradefed.utils.wifi;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import java.util.BitSet;
import java.util.List;

/**
 * adb shell am instrument -e method (method name) -e arg1 val1 -e arg2 val2
 * -w com.android.tradefed.utils.wifi/.WifiUtils
 */
public class WifiUtil extends Instrumentation {
    // FIXME: document exposed API methods and arguments
    private static final String TAG = "WifiUtil";

    private Bundle mArguments;
    private WifiManager mWifiManager = null;

    static class MissingArgException extends Exception {
        public MissingArgException(String msg) {
            super(msg);
        }

        public static MissingArgException fromArg(String arg) {
            return new MissingArgException(
                    String.format("Error: missing mandatory argument '%s'", arg));
        }
    }

    @Override
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        mArguments = arguments;
        start();
    }

    private static String quote(String str) {
        return String.format("\"%s\"", str);
    }

    private void fail(String errMsg) {
        Log.e(TAG, errMsg);
        Bundle result = new Bundle();
        result.putString("error", errMsg);
        finish(Activity.RESULT_CANCELED, result);
    }

    private String expectString(String arg) throws MissingArgException {
        String val = mArguments.getString(arg);
        if (TextUtils.isEmpty(val)) {
            throw MissingArgException.fromArg(arg);
        }

        return val;
    }

    private int expectInteger(String arg) throws MissingArgException {
        String val = expectString(arg);
        int intVal;
        try {
            intVal = Integer.parseInt(val);
        } catch (NumberFormatException e) {
            final String msg = String.format("Couldn't parse arg '%s': %s", arg, e.getMessage());
            throw new MissingArgException(msg);
        }

        return intVal;
    }

    @Override
    public void onStart() {
        super.onStart();
        final Bundle result = new Bundle();

        try {
            final String method = expectString("method");

            mWifiManager = (WifiManager)getContext().getSystemService(Context.WIFI_SERVICE);
            if (mWifiManager == null) {
                fail("Couldn't get WifiManager reference; goodbye!");
                return;
            }

            // As a pattern, method implementations below should gather arguments _first_, and then
            // use those arguments so that the system is not left in an inconsistent state if an
            // argument is missing in the middle of an implementation.
            if ("enableWifi".equals(method)) {
                result.putBoolean("result", mWifiManager.setWifiEnabled(true));
            } else if ("disableWifi".equals(method)) {
                result.putBoolean("result", mWifiManager.setWifiEnabled(false));
            } else if ("addOpenNetwork".equals(method)) {
                final String ssid = expectString("ssid");

                final WifiConfiguration config = new WifiConfiguration();
                // A string SSID _must_ be enclosed in double-quotation marks
                config.SSID = quote(ssid);
                // KeyMgmt should be NONE only
                final BitSet keymgmt = new BitSet();
                keymgmt.set(WifiConfiguration.KeyMgmt.NONE);
                config.allowedKeyManagement = keymgmt;

                result.putInt("result", mWifiManager.addNetwork(config));

            } else if ("addWpaPskNetwork".equals(method)) {
                final String ssid = expectString("ssid");
                final String psk = expectString("psk");

                final WifiConfiguration config = new WifiConfiguration();
                // A string SSID _must_ be enclosed in double-quotation marks
                config.SSID = quote(ssid);
                // Likewise for the psk
                config.preSharedKey = quote(psk);

                result.putInt("result", mWifiManager.addNetwork(config));

            } else if ("associateNetwork".equals(method)) {
                final int id = expectInteger("id");

                result.putBoolean("result",
                        mWifiManager.enableNetwork(id, true /* disable other networks */));

            } else if ("disconnect".equals(method)) {
                result.putBoolean("result", mWifiManager.disconnect());

            } else if ("disableNetwork".equals(method)) {
                final int id = expectInteger("id");

                result.putBoolean("result", mWifiManager.disableNetwork(id));

            } else if ("isWifiEnabled".equals(method)) {
                result.putBoolean("result", mWifiManager.isWifiEnabled());

            } else if ("getIpAddress".equals(method)) {
                final WifiInfo info = mWifiManager.getConnectionInfo();
                final int addr = info.getIpAddress();

                // IP address is stored with the first octet in the lowest byte
                final int a = (addr >> 0) & 0xff;
                final int b = (addr >> 8) & 0xff;
                final int c = (addr >> 16) & 0xff;
                final int d = (addr >> 24) & 0xff;

                result.putString("result", String.format("%s.%s.%s.%s", a, b, c, d));

            } else if ("getSSID".equals(method)) {
                final WifiInfo info = mWifiManager.getConnectionInfo();

                result.putString("result", info.getSSID());

            } else if ("removeAllNetworks".equals(method)) {
                boolean success = true;
                List<WifiConfiguration> netlist = mWifiManager.getConfiguredNetworks();
                if (netlist == null) {
                    success = false;
                } else {
                    for (WifiConfiguration config : netlist) {
                        success &= mWifiManager.removeNetwork(config.networkId);
                    }
                }

                result.putBoolean("result", success);

            } else if ("removeNetwork".equals(method)) {
                final int id = expectInteger("id");

                result.putBoolean("result", mWifiManager.removeNetwork(id));

            } else if ("saveConfiguration".equals(method)) {
                result.putBoolean("result", mWifiManager.saveConfiguration());

            } else if ("getSupplicantState".equals(method)) {
                String state = mWifiManager.getConnectionInfo().getSupplicantState().name();
                result.putString("result", state);
            } else {
                fail(String.format("Didn't recognize method '%s'", method));
                return;
            }
        } catch (MissingArgException e) {
            fail(e.getMessage());
            return;
        }

        finish(Activity.RESULT_OK, result);
    }
}
