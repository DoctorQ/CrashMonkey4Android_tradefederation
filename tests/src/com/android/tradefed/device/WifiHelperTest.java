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

import com.android.tradefed.util.IRunUtil;

import junit.framework.TestCase;

import org.easymock.EasyMock;

/**
 * Unit tests for {@link WifiHelper}.
 */
public class WifiHelperTest extends TestCase {

    private ITestDevice mMockDevice;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mMockDevice = EasyMock.createMock(ITestDevice.class);
        EasyMock.expect(mMockDevice.executeShellCommand(WifiHelper.CHECK_INSTRUMENTATION_CMD))
                .andReturn(WifiHelper.FULL_INSTRUMENTATION_NAME);
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

    /**
     * Test {@link WifiHelper#waitForIp()} that gets invalid data on first attempt, but then
     * succeeds on second.
     */
    public void testWaitForIp_failThenPass() throws Exception {
        MockTestDeviceHelper.injectShellResponse(mMockDevice, null, "", false);
        MockTestDeviceHelper.injectShellResponse(mMockDevice, null,
                "INSTRUMENTATION_RESULT: result=1.2.3.4", false);
        EasyMock.replay(mMockDevice);
        WifiHelper wifiHelper = new WifiHelper(mMockDevice) {
            @Override
            IRunUtil getRunUtil() {
                return EasyMock.createNiceMock(IRunUtil.class);
            }
        };
        assertTrue(wifiHelper.waitForIp(10 * 60 * 1000));
        // verify that two executeCommand attempt were made
        EasyMock.verify(mMockDevice);
    }
}
