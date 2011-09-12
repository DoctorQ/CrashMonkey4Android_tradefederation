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

import com.android.ddmlib.IShellOutputReceiver;
import com.android.tradefed.device.WifiHelper.WifiState;

import junit.framework.TestCase;

import org.easymock.EasyMock;

/**
 * Unit tests for {@link WifiHelper}.
 */
public class WifiHelperTest extends TestCase {

    private ITestDevice mMockDevice;
    private WifiHelper mWifi;

    private static final String NETCFG_WIFI_RESPONSE =
        "lo       UP    127.0.0.1       255.0.0.0       0x00000049\r\n" +
        "usb0     DOWN  0.0.0.0         0.0.0.0         0x00001002\r\n" +
        "eth0     UP    192.168.1.1 255.255.254.0   0x00001043\r\n";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mMockDevice = EasyMock.createMock(ITestDevice.class);
        mWifi = new WifiHelper(mMockDevice) {
            @Override
            long getPollTime() {
                return 50;
            }
        };
    }

    /**
     * Test {@link WifiHelper#waitForIp(long)} success case
     */
    public void testWaitForIp() throws DeviceNotAvailableException {
        // expect a wifi status query followed by a 'netcfg' query
        injectStatusResult(WifiState.COMPLETED);
        injectShellResponse(NETCFG_WIFI_RESPONSE);
        EasyMock.replay(mMockDevice);
        assertTrue(mWifi.waitForIp(1));
    }

    /**
     * Test {@link WifiHelper#getIpAddress(String)} for any interface
     */
    public void testGetIpAddress_interface() throws DeviceNotAvailableException {
        injectShellResponse(NETCFG_WIFI_RESPONSE);
        EasyMock.replay(mMockDevice);
        assertEquals("192.168.1.1", mWifi.getIpAddress(null));
    }

    /**
     * Test {@link WifiHelper#getIpAddress(String)} when ip is not assigned.
     */
    public void testGetIpAddress_failed() throws DeviceNotAvailableException {
        final String netcfgResponse =
            "lo       UP    127.0.0.1       255.0.0.0       0x00000049\r\n" +
            "usb0     DOWN  0.0.0.0         0.0.0.0         0x00001002\r\n" +
            "eth0     UP    0.0.0.0 255.255.254.0   0x00001043\r\n";
        injectShellResponse(netcfgResponse);
        EasyMock.replay(mMockDevice);
        assertNull(mWifi.getIpAddress(null));
    }

    /**
     * Test {@link WifiHelper#getIpAddress(String)} when a specific interface is requested, that
     * does not have an ip.
     */
    public void testGetIpAddress_diffInterface() throws DeviceNotAvailableException {
        final String netcfgResponse =
            "lo       UP    127.0.0.1       255.0.0.0       0x00000049\r\n" +
            "rmnet0   UP    192.168.1.1     255.255.255.0   0x000010d1\r\n" +
            "usb0     DOWN  0.0.0.0         0.0.0.0         0x00001002\r\n" +
            "eth0     UP    0.0.0.0 255.255.254.0   0x00001043\r\n";
        injectShellResponse(netcfgResponse);
        EasyMock.replay(mMockDevice);
        assertNull(mWifi.getIpAddress("eth0"));
    }

    /**
     * Test {@link WifiHelper#waitForWifiState(WifiState...))} success case.
     */
    public void testWaitForWifiState() throws DeviceNotAvailableException {
        injectStatusResult(WifiState.COMPLETED);
        EasyMock.replay(mMockDevice);
        assertTrue(mWifi.waitForWifiState(WifiState.SCANNING, WifiState.COMPLETED));
    }

    /**
     * Test {@link WifiHelper#getWifiStatus())} case when response is missing SUCCESS marker.
     */
    public void testGetWifiStatus_missingMarker() throws DeviceNotAvailableException {
        final String statusQueryReturn =
                "Using interface 'eth0'\r\n" +
                "id=0\r\n" +
                "wpa_state=COMPLETED\r\n";
        injectShellResponse(statusQueryReturn);
        EasyMock.replay(mMockDevice);
        assertNull(mWifi.getWifiStatus());
    }

    /**
     * Test {@link WifiHelper#getWifiStatus())} case when response is missing state.
     */
    public void testGetWifiStatus_missingState() throws DeviceNotAvailableException {
        final String statusQueryReturn = String.format(
                "Using interface 'eth0'\r\n" +
                "id=0\r\n" +
                "%s\r\n", WifiHelper.SUCCESS_MARKER);
        injectShellResponse(statusQueryReturn);
        EasyMock.replay(mMockDevice);
        assertNull(mWifi.getWifiStatus());
    }

    /**
     * Test {@link WifiHelper#addOpenNetwork(String))} success case.
     */
    public void testAddOpenNetwork() throws DeviceNotAvailableException {
        final String addNetworkResponse = String.format(
                "Using interface 'eth0'\r\n" +
                "0\r\n" +
                "%s\r\n", WifiHelper.SUCCESS_MARKER);
        injectShellResponse(addNetworkResponse);
        // expect set ssid command
        injectWpaCliOKResponse();
        // expect key mgmt command
        injectWpaCliOKResponse();
        EasyMock.replay(mMockDevice);
        assertEquals(Integer.valueOf(0), mWifi.addOpenNetwork("foo"));
    }

    /**
     * Test {@link WifiHelper#removeAllNetworks()} success case.
     */
    public void testRemoveAllNetworks() throws DeviceNotAvailableException {
        // expect a list network command followed by a remove network
        final String listNetworkResponse = String.format(
                "Using interface 'eth0'\r\n" +
                "0       MyNetwork     any    [CURRENT]\r\n" +
                "1       MyNetwork2     any\r\n" +
                "%s\r\n", WifiHelper.SUCCESS_MARKER);
        injectShellResponse(listNetworkResponse);

        mMockDevice.executeShellCommand((String)EasyMock.anyObject(),
                (IShellOutputReceiver)EasyMock.anyObject());
        EasyMock.expectLastCall().andDelegateTo(new StubTestDevice() {
            @Override
            public void executeShellCommand(String cmd, IShellOutputReceiver receiver) {
                assertTrue(cmd.contains("remove_network 0"));
            }
        });

        mMockDevice.executeShellCommand((String)EasyMock.anyObject(),
                (IShellOutputReceiver)EasyMock.anyObject());
        EasyMock.expectLastCall().andDelegateTo(new StubTestDevice() {
            @Override
            public void executeShellCommand(String cmd, IShellOutputReceiver receiver) {
                assertTrue(cmd.contains("remove_network 1"));
            }
        });

        EasyMock.replay(mMockDevice);
        mWifi.removeAllNetworks();
    }

    private void injectStatusResult(WifiState state) throws DeviceNotAvailableException {
        final String statusQueryReturn = String.format(
            "Using interface 'eth0'\r\n" +
            "id=0\r\n" +
            "wpa_state=%s\r\n" +
            "%s\r\n",
            state.name(), WifiHelper.SUCCESS_MARKER);
        injectShellResponse(statusQueryReturn);
    }

    private void injectWpaCliOKResponse() throws DeviceNotAvailableException {
        final String response = String.format(
            "Using interface 'eth0'\r\n" +
            "OK\r\n" +
            "%s\r\n",
            WifiHelper.SUCCESS_MARKER);
        injectShellResponse(response);
    }

    private void injectShellResponse(final String data) throws DeviceNotAvailableException {
        mMockDevice.executeShellCommand((String)EasyMock.anyObject(),
                (IShellOutputReceiver)EasyMock.anyObject());
        EasyMock.expectLastCall().andDelegateTo(new StubTestDevice() {
            @Override
            public void executeShellCommand(String cmd, IShellOutputReceiver receiver) {
                byte[] byteData = data.getBytes();
                receiver.addOutput(byteData, 0, byteData.length);
                receiver.flush();
            }
        });
    }
}
