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

import com.android.tradefed.device.WifiHelper.WifiState;

import org.easymock.EasyMock;

import junit.framework.TestCase;

/**
 * Unit tests for {@link WifiHelper}.
 */
public class WifiHelperTest extends TestCase {

    private ITestDevice mMockDevice;
    private WifiHelper mWifi;

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
     * Test {@link WifiHelper#waitForDhcp(long)} success case.
     */
    public void testWaitForDhcp() throws DeviceNotAvailableException {
        // expect a wifi status query followed by a 'netcfg' query
        injectStatusResult(WifiState.COMPLETED);
        final String dhcpResponse =
            "lo       UP    127.0.0.1       255.0.0.0       0x00000049\r\n" +
            "usb0     DOWN  0.0.0.0         0.0.0.0         0x00001002\r\n" +
            "eth0     UP    192.168.1.1 255.255.254.0   0x00001043\r\n";
        injectShellResponse(dhcpResponse);
        EasyMock.replay(mMockDevice);
        assertTrue(mWifi.waitForDhcp(1));
    }

    /**
     * Test {@link WifiHelper#waitForDhcp(long)} when ip is not assigned.
     */
    public void testWaitForDhcp_failed() throws DeviceNotAvailableException {
        // expect a wifi status query followed by a 'netcfg' query
        injectStatusResult(WifiState.COMPLETED);
        final String dhcpResponse =
            "lo       UP    127.0.0.1       255.0.0.0       0x00000049\r\n" +
            "usb0     DOWN  0.0.0.0         0.0.0.0         0x00001002\r\n" +
            "eth0     UP    0.0.0.0 255.255.254.0   0x00001043\r\n";
        injectShellResponse(dhcpResponse);
        EasyMock.replay(mMockDevice);
        assertFalse(mWifi.waitForDhcp(1));
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
                (ICancelableReceiver)EasyMock.anyObject());
        EasyMock.expectLastCall().andDelegateTo(new StubTestDevice() {
            @Override
            public void executeShellCommand(String cmd, ICancelableReceiver receiver) {
                assertTrue(cmd.contains("remove_network 0"));
            }
        });

        mMockDevice.executeShellCommand((String)EasyMock.anyObject(),
                (ICancelableReceiver)EasyMock.anyObject());
        EasyMock.expectLastCall().andDelegateTo(new StubTestDevice() {
            @Override
            public void executeShellCommand(String cmd, ICancelableReceiver receiver) {
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
                (ICancelableReceiver)EasyMock.anyObject());
        EasyMock.expectLastCall().andDelegateTo(new StubTestDevice() {
            @Override
            public void executeShellCommand(String cmd, ICancelableReceiver receiver) {
                byte[] byteData = data.getBytes();
                receiver.addOutput(byteData, 0, byteData.length);
                receiver.flush();
            }
        });
    }
}
