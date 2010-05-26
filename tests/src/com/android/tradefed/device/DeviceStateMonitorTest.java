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

import com.android.ddmlib.IDevice;
import com.android.ddmlib.IDevice.DeviceState;
import com.android.tradefed.util.RunUtil;

import org.easymock.EasyMock;

import junit.framework.TestCase;

/**
 * Unit tests for {@link DeviceStateMonitorTest}.
 */
public class DeviceStateMonitorTest extends TestCase {

    private static final String SERIAL_NUMBER = "1";
    private IDevice mMockDevice;
    private DeviceStateMonitor mMonitor;

    @Override
    protected void setUp() {
        mMockDevice = EasyMock.createMock(IDevice.class);
        EasyMock.expect(mMockDevice.getState()).andReturn(DeviceState.ONLINE);
        EasyMock.expect(mMockDevice.getSerialNumber()).andReturn(SERIAL_NUMBER).anyTimes();
        EasyMock.replay(mMockDevice);
        mMonitor = new DeviceStateMonitor(mMockDevice);
    }

    /**
     * Test {@link DeviceStateMonitor#waitForDeviceOnline()} when device is already online
     */
    public void testWaitForDeviceOnline_alreadyOnline() {
        assertEquals(mMockDevice, mMonitor.waitForDeviceOnline());
    }

    /**
     * Test {@link DeviceStateMonitor#waitForDeviceOnline()} when device becomes online
     */
    public void testWaitForDeviceOnline() {
        mMonitor.setState(TestDeviceState.NOT_AVAILABLE);
        new Thread() {
            @Override
            public void run() {
                RunUtil.sleep(100);
                mMonitor.setState(TestDeviceState.ONLINE);
            }
        }.start();
        assertEquals(mMockDevice, mMonitor.waitForDeviceOnline());
    }

    /**
     * Test {@link DeviceStateMonitor#waitForDeviceOnline()} when device does not becomes online
     * within allowed time
     */
    public void testWaitForDeviceOnline_timeout() {
        mMonitor.setState(TestDeviceState.NOT_AVAILABLE);
        new Thread() {
            @Override
            public void run() {
                RunUtil.sleep(500);
                mMonitor.setState(TestDeviceState.ONLINE);
            }
        }.start();
        assertNull(mMonitor.waitForDeviceOnline(100));
    }

    /**
     * Normal case test for {@link DeviceStateMonitor#waitForDeviceOnline()}
     */
    public void testWaitForDeviceAvailable() {
        // TODO: implement this when IDevice.executeShellCommand can be mocked more easily
        //assertEquals(mMockDevice, mMonitor.waitForDeviceAvailable());
    }

}
