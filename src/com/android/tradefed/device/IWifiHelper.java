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

package com.android.tradefed.device;

import com.android.tradefed.device.WifiHelper.WifiState;

/**
 * Helper interface for manipulating wifi services on device.
 */
interface IWifiHelper {

    /**
     * Enables wifi state on device.
     *
     * @throws DeviceNotAvailableException
     */
    void enableWifi() throws DeviceNotAvailableException;

    /**
     * Disables wifi state on device.
     *
     * @throws DeviceNotAvailableException
     */
    void disableWifi() throws DeviceNotAvailableException;

    /**
     * Disconnect from the wifi network identified by the provided integer.
     *
     * @param networkId the network id identifying its profile in wpa_supplicant configuration
     * @throws DeviceNotAvailableException
     */
    void disconnectFromNetwork(int networkId) throws DeviceNotAvailableException;

    /**
     * Waits until one of the expected wifi states occurs.
     *
     * @param expectedStates one or more wifi states to expect
     * @param timeout max time in ms to wait
     * @return <code>true</code> if the one of the expected states occurred. <code>false</code> if
     *         none of the states occurred before timeout is reached
     * @throws DeviceNotAvailableException
     */
    boolean waitForWifiState(WifiState... expectedStates) throws DeviceNotAvailableException;

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
    Integer addOpenNetwork(String ssid) throws DeviceNotAvailableException;

    /**
     * Adds the WPA-PSK security network identified by ssid.
     *
     * @param ssid the ssid of network to add.
     * @param psk the WPA-PSK passphrase to use
     * @return an integer number identifying the profile created in wpa_supplicant configuration.
     *         <code>null</code> if an error occured.
     * @throws DeviceNotAvailableException
     */
    Integer addWpaPskNetwork(String ssid, String psk) throws DeviceNotAvailableException;

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
    boolean associateNetwork(int networkId) throws DeviceNotAvailableException;

    /**
     * Wait until an ip address is assigned to wifi adapter.
     *
     * @param timeout how long to wait
     * @return <code>true</code> if an ip address is assigned before timeout, <code>false</code>
     *         otherwise
     * @throws DeviceNotAvailableException
     */
    boolean waitForIp(long timeout) throws DeviceNotAvailableException;

    /**
     * Gets the IP address associated with the given interfacec name
     *
     * @param interfaceName the interface to get IP address for. If null, will return IP for first
     * activr valid interface
     */
    String getIpAddress(String interfaceName) throws DeviceNotAvailableException;

    /**
     * Removes all known networks.
     *
     * @throws DeviceNotAvailableException
     */
    void removeAllNetworks() throws DeviceNotAvailableException;

}
