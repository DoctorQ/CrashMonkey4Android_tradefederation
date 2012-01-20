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

            @Override
            void ensureDeviceSetup() {
                // ignore
            }
        };
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

    // tests for reimplementation
    public void testBuildCommand_simple() {
        final String expected = "am instrument -e method \"meth\" -w " +
                WifiHelper.FULL_INSTRUMENTATION_NAME;
        final String cmd = WifiHelper.buildWifiUtilCmd("meth");
        assertEquals(expected, cmd);
    }

    public void testBuildCommand_oneArg() {
        final String start = "am instrument ";
        final String piece1 = "-e method \"meth\" ";
        final String piece2 = "-e id \"45\" ";
        final String end = "-w " + WifiHelper.FULL_INSTRUMENTATION_NAME;

        final String cmd = WifiHelper.buildWifiUtilCmd("meth", "id", "45");
        // Do this piecewise since Map traverse order is arbitrary
        assertTrue(cmd.startsWith(start));
        assertTrue(cmd.contains(piece1));
        assertTrue(cmd.contains(piece2));
        assertTrue(cmd.endsWith(end));
    }

    public void testBuildCommand_withSpace() {
        final String start = "am instrument ";
        final String piece1 = "-e method \"addWpaPskNetwork\" ";
        final String piece2 = "-e ssid \"With Space\" ";
        final String piece3 = "-e psk \"also has space\" ";
        final String end = "-w " + WifiHelper.FULL_INSTRUMENTATION_NAME;

        final String cmd = WifiHelper.buildWifiUtilCmd("addWpaPskNetwork", "ssid", "With Space",
                "psk", "also has space");
        // Do this piecewise since Map traverse order is arbitrary
        assertTrue(cmd.startsWith(start));
        assertTrue(cmd.contains(piece1));
        assertTrue(cmd.contains(piece2));
        assertTrue(cmd.contains(piece3));
        assertTrue(cmd.endsWith(end));
    }
}
