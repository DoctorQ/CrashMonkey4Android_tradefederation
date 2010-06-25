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
package com.android.tradefed.device;

import com.android.ddmlib.MultiLineReceiver;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Helper class for manipulating wifi services on device.
 */
class WifiHelper {

    private static final String INTERFACE_KEY = "interface";

    enum WifiState {
        COMPLETED, SCANNING, DISCONNECTED;
    }

    /** token used to detect proper command response */
    static final String SUCCESS_MARKER = "tHiS-iS-sUcCeSs-MaRkEr";

    private static final String WPA_STATE = "wpa_state";

    /** the default time in ms to wait for a wifi state */
    private static final long DEFAULT_WIFI_STATE_TIMEOUT = 30*1000;

    private final ITestDevice mDevice;

    WifiHelper(ITestDevice device) {
        mDevice = device;
    }

    /**
     * Get the {@link RunUtil} instance to use.
     * <p/>
     * Exposed for unit testing.
     */
    IRunUtil getRunUtil() {
        return RunUtil.getInstance();
    }

    /**
     * Enables wifi state on device.
     *
     * @throws DeviceNotAvailableException
     */
    void enableWifi() throws DeviceNotAvailableException {
        mDevice.executeShellCommand("svc wifi enable");
    }

    /**
     * Disables wifi state on device.
     *
     * @throws DeviceNotAvailableException
     */
    void disableWifi() throws DeviceNotAvailableException {
        mDevice.executeShellCommand("svc wifi disable");
    }

    /**
     * Disconnect from the wifi network identified by the provided integer.
     *
     * @param networkId the network id identifying its profile in wpa_supplicant configuration
     * @throws DeviceNotAvailableException
     */
    void disconnectFromNetwork(int networkId) throws DeviceNotAvailableException {
        callWpaCli(String.format("disable_network %d", networkId));
    }

    /**
     * Waits until one of the expected wifi states occurs.
     *
     * @param expectedStates one or more wifi states to expect
     * @param timeout max time in ms to wait
     * @return <code>true</code> if the one of the expected states occurred. <code>false</code> if
     *         none of the states occurred before timeout is reached
     * @throws DeviceNotAvailableException
     */
    boolean waitForWifiState(WifiState... expectedStates) throws DeviceNotAvailableException {
        return waitForWifiState(DEFAULT_WIFI_STATE_TIMEOUT, expectedStates);
    }

    /**
     * Waits the given time until one of the expected wifi states occurs.
     *
     * @param expectedStates one or more wifi states to expect
     * @param timeout max time in ms to wait
     * @return <code>true</code> if the one of the expected states occurred. <code>false</code> if
     *         none of the states occurred before timeout is reached
     * @throws DeviceNotAvailableException
     */
    boolean waitForWifiState(long timeout, WifiState... expectedStates)
            throws DeviceNotAvailableException {
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() < (startTime + timeout)) {
            Map<String, String> statusMap = getWifiStatus();
            if (statusMap != null) {
                String state = statusMap.get(WPA_STATE);
                for (WifiState expectedState : expectedStates) {
                    if (expectedState.name().equals(state)) {
                        return true;
                    }
                }
            }
            getRunUtil().sleep(getPollTime());
        }
        return false;
    }

    /**
     * Gets the time to sleep between poll attempts
     */
    long getPollTime() {
        return 1*1000;
    }

    /**
     * Retrieves wifi status from device.
     * <p/>
     * This will call 'wpa_cli' status on device. Typical output looks like:
     *
     * <pre>
     * Using interface 'tiwlan0'
     * bssid=00:0b:86:c3:80:40
     * ssid=ssidname
     * id=0
     * pairwise_cipher=NONE
     * group_cipher=NONE
     * key_mgmt=NONE
     * wpa_state=COMPLETED
     * </pre>
     *
     * Each line will be converted to an entry in the resulting map, with an additional key
     * {@link #INTERFACE_KEY} indicating the interface name.
     *
     * @return a map containing wifi status variables. <code>null</code> if wpa_cli failed to
     *         connect to wpa_supplicant
     * @throws DeviceNotAvailableException
     */
    Map<String, String> getWifiStatus() throws DeviceNotAvailableException {
        Map<String, String> statusMap = new HashMap<String, String>();
        WpaCliOutput output = callWpaCli("status");
        if (!output.isSuccess()) {
            return null;
        }
        for (String line: output.mOutputLines) {
            String[] pair = line.split("=", 2);
            if (pair.length == 2) {
                statusMap.put(pair[0], pair[1]);
            }
        }
        if (!statusMap.containsKey(WPA_STATE)) {
            return null;
        }
        statusMap.put(INTERFACE_KEY, output.mWpaInterface);
        return statusMap;
    }

    /**
     * Remove the network identified by an integer network id.
     *
     * @param networkId the network id identifying its profile in wpa_supplicant configuration
     * @throws DeviceNotAvailableException
     */
    void removeNetwork(int networkId) throws DeviceNotAvailableException {
        callWpaCli(String.format("remove_network %d", networkId));
    }

    /**
     * Adds the open security network identified by ssid.
     * <p/>
     * To connect to any wifi network, a network profile must be created in wpa_supplicant
     * configuration first. This will call wpa_cli to add the open security network identified by
     * ssid.
     *
     * @param ssid the ssid of network to add.
     * @return an integer number identifying the profile created in wpa_supplicant configuration.
     *         <code>null</code> if an error occured.
     * @throws DeviceNotAvailableException
     */
    Integer addOpenNetwork(String ssid) throws DeviceNotAvailableException {
        WpaCliOutput output = callWpaCli("add_network");
        if (!output.isSuccess()) {
            return null;
        }
        Pattern networkIdPattern = Pattern.compile("^\\d+$");
        Integer networkId = null;
        for (String line : output.mOutputLines) {
            Matcher networkMatcher = networkIdPattern.matcher(line);
            if (networkMatcher.matches()) {
                networkId = Integer.parseInt(line);
                break;
            }
        }
        if (networkId == null) {
            return null;
        }

        // set ssid - command to send is   set_network 0  ssid '"ssidname"'
        String setSsidCmd = String.format("set_network %d ssid '\"%s\"'", networkId, ssid);
        if (!callWpaCliChecked(setSsidCmd)) {
            removeNetwork(networkId);
            return null;
        }
        // set key_mgmt to NONE (open security)
        if (!callWpaCliChecked(String.format("set_network %d key_mgmt NONE", networkId))) {
            removeNetwork(networkId);
            return null;
        }
        return networkId;
    }

    /**
     * Associate with the wifi network identified by the provided integer.
     *
     * @param networkId the network id identifying its profile in wpa_supplicant configuration,
     *            e.g. returned by AddOpenNetwork
     * @return <code>true</code> if the call is successful. <code>false</code> if the call failed.
     *         Note that a <code>true</code> return does not necessarily mean that the device has
     *         successfully associated with the network, must call {@link #getWifiStatus()} or
     *         {@link #waitForWifiState(WifiState...)} to verify.
     * @throws DeviceNotAvailableException
     */
    boolean associateNetwork(int networkId) throws DeviceNotAvailableException {
        if (!callWpaCliChecked("disconnect")) {
            return false;
        }
        if (!callWpaCliChecked(String.format("enable_network %d", networkId))) {
            return false;
        }
        if (!callWpaCliChecked(String.format("select_network %d", networkId))) {
            return false;
        }
        if (!callWpaCliChecked("reconnect")) {
            return false;
        }
        return true;
    }

    /**
     * Wait until an ip address is assigned to wifi adapter.
     *
     * @param timeout how long to wait
     * @return <code>true</code> if an ip address is assigned before timeout, <code>false</code>
     *         otherwise
     * @throws DeviceNotAvailableException
     */
    boolean waitForDhcp(long timeout) throws DeviceNotAvailableException {
        Map<String, String> statusMap = getWifiStatus();
        if (statusMap == null ) {
            return false;
        }
        String interfaceName = statusMap.get(INTERFACE_KEY);
        if (interfaceName == null) {
            return false;
        }
        long startTime = System.currentTimeMillis();

        while (System.currentTimeMillis() < (startTime + timeout)) {
            DhcpOutput output = new DhcpOutput(interfaceName);
            mDevice.executeShellCommand("netcfg", output);
            if (output.mDhcpSuccess) {
                return true;
            }
            getRunUtil().sleep(getPollTime());
        }
        return false;
    }

    /**
     * Removes all known networks.
     *
     * @throws DeviceNotAvailableException
     */
    void removeAllNetworks() throws DeviceNotAvailableException {
        WpaCliOutput output = callWpaCli("list_networks");
        if (!output.isSuccess()) {
            return;
        }
        // expected format is
        // networkId   SSID
        Pattern networkPattern = Pattern.compile("^(\\d+)\\s+\\w+");
        for (String line : output.mOutputLines) {
            Matcher matcher = networkPattern.matcher(line);
            if (matcher.find()) {
                // network id is first group
                Integer networkId = Integer.parseInt(matcher.group(1));
                removeNetwork(networkId);
            }
        }
    }

    /**
     * Calls wpa_cli and does initial parsing.
     *
     * @param cmd the wpa_cli command to run
     * @return a WpaCliOutput object containing the result of the command. If an error is detected
     *         in wpa_cli output, e.g. failed to connect to wpa_supplicant, which is typically due
     *         to disabled wifi, <code>null</code> will be returned
     * @throws DeviceNotAvailableException
     */
    private WpaCliOutput callWpaCli(String cmd) throws DeviceNotAvailableException {
        String fullCmd = String.format("wpa_cli %s && echo && echo %s", cmd, SUCCESS_MARKER);
        WpaCliOutput output = new WpaCliOutput();
        mDevice.executeShellCommand(fullCmd, output);
        return output;
    }

    /**
     * Calls wpa_cli and also checks output for OK or FAIL.
     *
     * @param cmd the wpa_cli command to run
     * @return <code>true</code> if output contains OK, <code>false</code> if output does not
     *         contain OK or it failed
     * @throws DeviceNotAvailableException
     */
    private boolean callWpaCliChecked(String cmd) throws DeviceNotAvailableException {
        WpaCliOutput output = callWpaCli(cmd);
        if (!output.isSuccess()) {
            return false;
        }
        for (String line: output.mOutputLines) {
            if (line.equals("OK")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Processes the output of a wpa_cli command.
     */
    private static class WpaCliOutput extends MultiLineReceiver implements ICancelableReceiver {

        private boolean mDidCommandComplete = false;
        private boolean mIsCommandSuccess = true;

        /** The name of the interface resulting from a wpa cli command */
        String mWpaInterface = null;

        /** The output lines of the wpa cli command. */
        List<String> mOutputLines;

        private boolean mIsCanceled = false;

        WpaCliOutput() {
            mOutputLines = new ArrayList<String>();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void processNewLines(String[] lines) {
            // expect SUCCESS_MARKER to be present on last line for successful command
            if (!mDidCommandComplete) {
                mDidCommandComplete = lines[lines.length -1].equals(SUCCESS_MARKER);
            }
            Pattern interfacePattern = Pattern.compile("Using interface '(.*)'");

            for (String line : lines) {
                mOutputLines.add(line);
                if (line.contains("Failed to connect to wpa_supplicant")) {
                    mIsCommandSuccess = false;
                }
                Matcher interfaceMatcher = interfacePattern.matcher(line);
                if (interfaceMatcher.find()) {
                    mWpaInterface = interfaceMatcher.group(1);
                }
            }
        }

        public boolean isSuccess() {
            return mDidCommandComplete && mIsCommandSuccess;
        }

        /**
         * {@inheritDoc}
         */
        public boolean isCancelled() {
            return mIsCanceled;
        }

        public void cancel() {
            mIsCanceled = true;
        }
    }

    /**
     * Process the output of a 'netcfg' command.
     * <p/>
     * Looks for valid IP being assigned to a given network interface. 'Valid' is interpreted as
     * != "0.0.0.0".
     */
    private static class DhcpOutput extends MultiLineReceiver implements ICancelableReceiver {

        boolean mDhcpSuccess = false;
        final String mInterfaceName;
        private boolean mIsCanceled = false;

        DhcpOutput(String interfaceName) {
            mInterfaceName = interfaceName;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void processNewLines(String[] lines) {
            for (String line : lines) {
                if (line.contains(mInterfaceName)) {
                    String[] fields = line.split("\\s+");
                    if (fields.length >= 2 && fields[1].equals("UP") &&
                            !fields[2].equals("0.0.0.0")) {
                        mDhcpSuccess = true;
                    }
                    break;
                }
            }
        }

        /**
         * {@inheritDoc}
         */
        public boolean isCancelled() {
            return mIsCanceled ;
        }

        /**
         * {@inheritDoc}
         */
        public void cancel() {
            mIsCanceled = true;
        }

    }
}
