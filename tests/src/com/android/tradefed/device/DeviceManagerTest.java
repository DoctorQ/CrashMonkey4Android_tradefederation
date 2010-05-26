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
import com.android.ddmlib.AndroidDebugBridge.IDeviceChangeListener;
import com.android.ddmlib.IDevice.DeviceState;

import org.easymock.EasyMock;

import java.util.Collection;

import junit.framework.TestCase;

/**
 * Unit tests for {@link DeviceManager}.
 */
public class DeviceManagerTest extends TestCase {

    /**
     * A wait time value to provide to allocateDevice that results in reliable test results.
     * <p/>
     * Setting this constant to a value less than 100 may cause actual wait times to be much
     * higher.
     */
    private static final int MIN_ALLOCATE_WAIT_TIME = 100;
    private static final String DEVICE_SERIAL = "1";

    private IAndroidDebugBridge mMockAdbBridge;
    private IDeviceRecovery mMockRecovery;
    private IDevice mMockDevice;

    /** a reference to the DeviceManager's IDeviceChangeListener. Used for triggering device
     * connection events */
    private IDeviceChangeListener mDeviceListener;

    /**
     * {@inheritDoc}
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mMockAdbBridge = EasyMock.createNiceMock(IAndroidDebugBridge.class);
        mMockAdbBridge.addDeviceChangeListener((IDeviceChangeListener)EasyMock.anyObject());
        EasyMock.expectLastCall().andDelegateTo(new IAndroidDebugBridge() {
            public void addDeviceChangeListener(final IDeviceChangeListener listener) {
                mDeviceListener = listener;
            }

            public IDevice[] getDevices() {
                return null;
            }

            public void removeDeviceChangeListener(IDeviceChangeListener listener) {
            }

            public void init(boolean clientSupport) {
            }

            public void terminate() {
            }

        });
        mMockRecovery = EasyMock.createMock(IDeviceRecovery.class);
        mMockDevice = EasyMock.createNiceMock(IDevice.class);
        EasyMock.expect(mMockDevice.getSerialNumber()).andReturn(DEVICE_SERIAL).anyTimes();
        EasyMock.expect(mMockDevice.getState()).andReturn(DeviceState.ONLINE).anyTimes();
    }

    private DeviceManager createDeviceManager() {
        return new DeviceManager() {
            @Override
            IAndroidDebugBridge createAdbBridge() {
                return mMockAdbBridge;
            }

            @Override
            void startFastbootMonitor() {
            }
        };
    }

    /**
     * Test @link DeviceManager#allocateDevice()} when a IDevice is present on DeviceManager
     * creation.
     */
    public void testAllocateDevice() throws DeviceNotAvailableException {
        EasyMock.expect(mMockAdbBridge.getDevices()).andReturn(new IDevice[] {mMockDevice});
        EasyMock.replay(mMockDevice, mMockAdbBridge);
        DeviceManager manager = createDeviceManager();
        ITestDevice testDevice = manager.allocateDevice(mMockRecovery);
        assertEquals(mMockDevice, testDevice.getIDevice());
    }

    /**
     * Test {@link DeviceManager#allocateDevice()} that allocates a asynchronously connected device.
     */
    public void testAllocateDevice_wait() throws DeviceNotAvailableException {
        // first call, return nothing
        EasyMock.expect(mMockAdbBridge.getDevices()).andReturn(new IDevice[] {});
        EasyMock.replay(mMockDevice, mMockAdbBridge);
        DeviceManager manager = createDeviceManager();
        // call the listener back on a different thread
        new Thread() {
            @Override
            public void run() {
                mDeviceListener.deviceConnected(mMockDevice);
            }
        }.start();

        ITestDevice testDevice = manager.allocateDevice(mMockRecovery);
        assertEquals(mMockDevice, testDevice.getIDevice());
    }

    /**
     * Test {@link DeviceManager#allocateDevice(IDeviceRecovery, long))} when device is returned
     */
    public void testAllocateDeviceTime() throws DeviceNotAvailableException {
        EasyMock.expect(mMockAdbBridge.getDevices()).andReturn(new IDevice[] {mMockDevice});
        EasyMock.replay(mMockDevice, mMockAdbBridge);
        DeviceManager manager = createDeviceManager();
        ITestDevice testDevice = manager.allocateDevice(mMockRecovery, 100);
        assertEquals(mMockDevice, testDevice.getIDevice());
    }

    /**
     * Test {@link DeviceManager#allocateDevice(IDeviceRecovery, long))} when timeout is reached.
     */
    public void testAllocateDeviceTime_timeout() throws DeviceNotAvailableException {
        EasyMock.expect(mMockAdbBridge.getDevices()).andReturn(new IDevice[] {});
        EasyMock.replay(mMockDevice, mMockAdbBridge);
        DeviceManager manager = createDeviceManager();
        assertNull(manager.allocateDevice(mMockRecovery, MIN_ALLOCATE_WAIT_TIME));
    }

    /**
     * Test method for {@link DeviceManager#freeDevice(ITestDevice)}.
     */
    public void testFreeDevice() throws DeviceNotAvailableException {
        EasyMock.expect(mMockAdbBridge.getDevices()).andReturn(new IDevice[] {mMockDevice});
        EasyMock.replay(mMockDevice, mMockAdbBridge);
        DeviceManager manager = createDeviceManager();
        ITestDevice testDevice = manager.allocateDevice(mMockRecovery);
        assertEquals(mMockDevice, testDevice.getIDevice());
        manager.freeDevice(testDevice);
        // verify same device can be allocated again
        ITestDevice newDevice = manager.allocateDevice(mMockRecovery);
        assertEquals(mMockDevice, newDevice.getIDevice());
    }

    /**
     * Verified that {@link DeviceManager#freeDevice(ITestDevice)} ignores a call with a device
     * that has not been allocated.
     */
    public void testFreeDevice_noop() throws DeviceNotAvailableException {
        EasyMock.expect(mMockAdbBridge.getDevices()).andReturn(new IDevice[] {mMockDevice});
        ITestDevice testDevice = EasyMock.createNiceMock(ITestDevice.class);
        EasyMock.expect(testDevice.getSerialNumber()).andReturn("dontexist");
        EasyMock.replay(testDevice, mMockDevice, mMockAdbBridge);
        DeviceManager manager = createDeviceManager();
        manager.freeDevice(testDevice);
    }

    /**
     * Verified that {@link DeviceManager} calls {@link IManagedTestDevice#setIDevice(IDevice)}
     * when DDMS allocates a new IDevice on connection.
     */
    public void testSetIDevice() throws DeviceNotAvailableException {
        EasyMock.expect(mMockAdbBridge.getDevices()).andReturn(new IDevice[] {mMockDevice});
        EasyMock.replay(mMockDevice, mMockAdbBridge);
        DeviceManager manager = createDeviceManager();
        TestDevice testDevice = (TestDevice)manager.allocateDevice(mMockRecovery);
        assertEquals(mMockDevice, testDevice.getIDevice());
        // now trigger a device disconnect + reconnection
        mDeviceListener.deviceDisconnected(mMockDevice);
        IDevice newMockDevice = EasyMock.createMock(IDevice.class);
        EasyMock.expect(newMockDevice.getSerialNumber()).andReturn(DEVICE_SERIAL).anyTimes();
        EasyMock.expect(newMockDevice.getState()).andReturn(DeviceState.ONLINE).anyTimes();
        EasyMock.replay(newMockDevice);
        mDeviceListener.deviceConnected(newMockDevice);
        assertEquals(newMockDevice, testDevice.getIDevice());
        assertEquals(TestDeviceState.ONLINE, testDevice.getDeviceState());
    }

    /**
     * Verified that a disconnected device cannot be allocated
     */
    public void testAllocateDevice_disconnected() throws DeviceNotAvailableException {
        EasyMock.expect(mMockAdbBridge.getDevices()).andReturn(new IDevice[] {mMockDevice});
        EasyMock.replay(mMockDevice, mMockAdbBridge);
        DeviceManager manager = createDeviceManager();
        mDeviceListener.deviceDisconnected(mMockDevice);
        assertNull(manager.allocateDevice(mMockRecovery, MIN_ALLOCATE_WAIT_TIME));
    }

    /**
     * Verified that a disconnected device state gets updated
     */
    public void testSetState_disconnected() throws DeviceNotAvailableException {
        EasyMock.expect(mMockAdbBridge.getDevices()).andReturn(new IDevice[] {mMockDevice});
        EasyMock.replay(mMockDevice, mMockAdbBridge);
        DeviceManager manager = createDeviceManager();
        TestDevice testDevice = (TestDevice)manager.allocateDevice(mMockRecovery);
        mDeviceListener.deviceDisconnected(mMockDevice);
        assertEquals(TestDeviceState.NOT_AVAILABLE, testDevice.getDeviceState());
    }

    /**
     * Verified that a offline device state gets updated
     */
    public void testSetState_offline() throws DeviceNotAvailableException {
        EasyMock.expect(mMockAdbBridge.getDevices()).andReturn(new IDevice[] {mMockDevice});
        EasyMock.replay(mMockDevice, mMockAdbBridge);
        DeviceManager manager = createDeviceManager();
        TestDevice testDevice = (TestDevice)manager.allocateDevice(mMockRecovery);
        IDevice newDevice = EasyMock.createMock(IDevice.class);
        EasyMock.expect(newDevice.getSerialNumber()).andReturn(DEVICE_SERIAL).anyTimes();
        EasyMock.expect(newDevice.getState()).andReturn(DeviceState.OFFLINE);
        EasyMock.replay(newDevice);
        mDeviceListener.deviceChanged(newDevice, IDevice.CHANGE_STATE);
        assertEquals(TestDeviceState.OFFLINE, testDevice.getDeviceState());
    }

    // TODO: add test for fastboot state changes

    /**
     * Verify the 'fastboot devices' output parsing
     */
    public void testGetDeviceOnFastboot() {
        Collection<String> deviceSerials = DeviceManager.getDevicesOnFastboot(
                "04035EEB0B01F01C        fastboot\n" +
                "HT99PP800024    fastboot\n" +
                "????????????    fastboot");
        assertEquals(2, deviceSerials.size());
        assertTrue(deviceSerials.contains("04035EEB0B01F01C"));
        assertTrue(deviceSerials.contains("HT99PP800024"));
    }

    /**
     * Verify the 'fastboot devices' output parsing when empty
     */
    public void testGetDeviceOnFastboot_empty() {
        Collection<String> deviceSerials = DeviceManager.getDevicesOnFastboot("");
        assertEquals(0, deviceSerials.size());
    }
}
