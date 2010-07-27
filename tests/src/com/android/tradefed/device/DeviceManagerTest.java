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
import com.android.tradefed.device.IDeviceManager.FreeDeviceState;

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
    private static final String DEVICE_SERIAL = "serial";

    private IAndroidDebugBridge mMockAdbBridge;
    private IDevice mMockIDevice;
    private IDeviceStateMonitor mMockMonitor;
    private IManagedTestDevice mMockTestDevice;

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

            public void init(boolean clientSupport, String adbOsLocation) {
            }

            public void terminate() {
            }

        });
        mMockIDevice = EasyMock.createMock(IDevice.class);
        mMockMonitor = EasyMock.createMock(IDeviceStateMonitor.class);
        mMockTestDevice = EasyMock.createMock(IManagedTestDevice.class);

        EasyMock.expect(mMockIDevice.getSerialNumber()).andReturn(DEVICE_SERIAL).anyTimes();
        EasyMock.expect(mMockTestDevice.getSerialNumber()).andReturn(DEVICE_SERIAL).anyTimes();
        EasyMock.expect(mMockTestDevice.getIDevice()).andStubReturn(mMockIDevice);
    }

    private DeviceManager createDeviceManager() {
        DeviceManager mgr = new DeviceManager() {


            @Override
            IAndroidDebugBridge createAdbBridge() {
                return mMockAdbBridge;
            }

            @Override
            void startFastbootMonitor() {
            }

            @Override
            IDeviceStateMonitor createStateMonitor(IDevice device) {
                return mMockMonitor;
            }

            @Override
            IManagedTestDevice createTestDevice(IDevice allocatedDevice,
                    IDeviceStateMonitor monitor) {
                return mMockTestDevice;
            }
        };
        mgr.setEnableLogcat(false);
        return mgr;
    }

    /**
     * Test @link DeviceManager#allocateDevice()} when a IDevice is present on DeviceManager
     * creation.
     */
    public void testAllocateDevice() throws DeviceNotAvailableException {
        EasyMock.expect(mMockAdbBridge.getDevices()).andReturn(new IDevice[] {mMockIDevice});
        EasyMock.expect(mMockIDevice.getState()).andReturn(DeviceState.ONLINE);
        EasyMock.expect(mMockMonitor.waitForDeviceAvailable(EasyMock.anyLong())).andReturn(
                mMockIDevice);
        EasyMock.replay(mMockMonitor, mMockIDevice, mMockAdbBridge);
        DeviceManager manager = createDeviceManager();
        assertEquals(mMockTestDevice, manager.allocateDevice());
        EasyMock.verify(mMockMonitor);
    }

    /**
     * Test {@link DeviceManager#allocateDevice()} that allocates a asynchronously connected device.
     */
    public void testAllocateDevice_wait() throws DeviceNotAvailableException {
        // first call, return nothing
        EasyMock.expect(mMockAdbBridge.getDevices()).andReturn(new IDevice[] {});
        EasyMock.expect(mMockIDevice.getState()).andReturn(DeviceState.ONLINE);

        EasyMock.expect(mMockMonitor.waitForDeviceAvailable(EasyMock.anyLong())).andReturn(
                mMockIDevice);
        EasyMock.replay(mMockMonitor, mMockIDevice, mMockAdbBridge);
        DeviceManager manager = createDeviceManager();
        // call the listener back on a different thread
        new Thread() {
            @Override
            public void run() {
                mDeviceListener.deviceConnected(mMockIDevice);
            }
        }.start();

        assertEquals(mMockTestDevice, manager.allocateDevice());
        EasyMock.verify(mMockMonitor);
    }

    /**
     * Test {@link DeviceManager#allocateDevice(IDeviceRecovery, long))} when device is returned
     */
    public void testAllocateDeviceTime() throws DeviceNotAvailableException {
        EasyMock.expect(mMockAdbBridge.getDevices()).andReturn(new IDevice[] {mMockIDevice});
        EasyMock.expect(mMockIDevice.getState()).andReturn(DeviceState.ONLINE);
        EasyMock.expect(mMockMonitor.waitForDeviceAvailable(EasyMock.anyLong())).andReturn(
                mMockIDevice);
        EasyMock.replay(mMockMonitor, mMockIDevice, mMockAdbBridge);
        DeviceManager manager = createDeviceManager();
        assertEquals(mMockTestDevice, manager.allocateDevice(100));
        EasyMock.verify(mMockMonitor);
    }

    /**
     * Test {@link DeviceManager#allocateDevice(IDeviceRecovery, long))} when timeout is reached.
     */
    public void testAllocateDeviceTime_timeout() throws DeviceNotAvailableException {
        EasyMock.expect(mMockAdbBridge.getDevices()).andReturn(new IDevice[] {});
        EasyMock.replay(mMockIDevice, mMockAdbBridge);
        DeviceManager manager = createDeviceManager();
        assertNull(manager.allocateDevice(MIN_ALLOCATE_WAIT_TIME));
    }

    /**
     * Test method for {@link DeviceManager#freeDevice(ITestDevice)}.
     */
    public void testFreeDevice() throws DeviceNotAvailableException {
        EasyMock.expect(mMockAdbBridge.getDevices()).andReturn(new IDevice[] {mMockIDevice});
        EasyMock.expect(mMockIDevice.getState()).andReturn(DeviceState.ONLINE);
        EasyMock.expect(mMockMonitor.waitForDeviceAvailable(EasyMock.anyLong())).andReturn(
                mMockIDevice);
        mMockTestDevice.stopLogcat();
        EasyMock.replay(mMockTestDevice, mMockMonitor, mMockIDevice, mMockAdbBridge);
        DeviceManager manager = createDeviceManager();
        assertEquals(mMockTestDevice, manager.allocateDevice());
        manager.freeDevice(mMockTestDevice, FreeDeviceState.AVAILABLE);
        // verify same device can be allocated again
        assertEquals(mMockTestDevice, manager.allocateDevice());
    }

    /**
     * Verified that {@link DeviceManager#freeDevice(ITestDevice)} ignores a call with a device
     * that has not been allocated.
     */
    public void testFreeDevice_noop() throws DeviceNotAvailableException {
        EasyMock.expect(mMockAdbBridge.getDevices()).andReturn(new IDevice[] {mMockIDevice});
        EasyMock.expect(mMockIDevice.getState()).andReturn(DeviceState.ONLINE);
        EasyMock.expect(mMockMonitor.waitForDeviceAvailable(EasyMock.anyLong())).andReturn(
                mMockIDevice);
        ITestDevice testDevice = EasyMock.createNiceMock(ITestDevice.class);
        EasyMock.expect(testDevice.getSerialNumber()).andReturn("dontexist");
        EasyMock.replay(mMockMonitor, testDevice, mMockIDevice, mMockAdbBridge);
        DeviceManager manager = createDeviceManager();
        manager.freeDevice(testDevice, FreeDeviceState.AVAILABLE);
    }

    /**
     * Verified that {@link DeviceManager} calls {@link IManagedTestDevice#setIDevice(IDevice)}
     * when DDMS allocates a new IDevice on connection.
     */
    public void testSetIDevice() throws DeviceNotAvailableException {
        EasyMock.expect(mMockAdbBridge.getDevices()).andReturn(new IDevice[] {mMockIDevice});
        EasyMock.expect(mMockIDevice.getState()).andReturn(DeviceState.ONLINE);
        EasyMock.expect(mMockMonitor.waitForDeviceAvailable(EasyMock.anyLong())).andReturn(
                mMockIDevice);
        IDevice newMockDevice = EasyMock.createMock(IDevice.class);
        EasyMock.expect(newMockDevice.getSerialNumber()).andReturn(DEVICE_SERIAL).anyTimes();
        EasyMock.expect(newMockDevice.getState()).andReturn(DeviceState.ONLINE);
        mMockTestDevice.setDeviceState(TestDeviceState.NOT_AVAILABLE);
        mMockTestDevice.setIDevice(newMockDevice);
        mMockTestDevice.setDeviceState(TestDeviceState.ONLINE);
        EasyMock.replay(mMockMonitor, mMockIDevice, mMockTestDevice, mMockAdbBridge, newMockDevice);
        DeviceManager manager = createDeviceManager();
        assertEquals(mMockTestDevice, manager.allocateDevice());
        // now trigger a device disconnect + reconnection
        mDeviceListener.deviceDisconnected(mMockIDevice);
        mDeviceListener.deviceConnected(newMockDevice);
        EasyMock.verify(mMockTestDevice);
    }

    /**
     * Verified that a disconnected device cannot be allocated
     */
    public void testAllocateDevice_disconnected() throws DeviceNotAvailableException {
        EasyMock.expect(mMockAdbBridge.getDevices()).andReturn(new IDevice[] {mMockIDevice});
        EasyMock.expect(mMockIDevice.getState()).andReturn(DeviceState.ONLINE);
        EasyMock.replay(mMockIDevice, mMockAdbBridge);
        DeviceManager manager = createDeviceManager();
        mDeviceListener.deviceDisconnected(mMockIDevice);
        assertNull(manager.allocateDevice(MIN_ALLOCATE_WAIT_TIME));
    }

    /**
     * Verified that a offline device cannot be allocated
     */
    public void testAllocateDevice_offline() throws DeviceNotAvailableException {
        EasyMock.expect(mMockAdbBridge.getDevices()).andReturn(new IDevice[] {mMockIDevice});
        EasyMock.expect(mMockIDevice.getState()).andReturn(DeviceState.OFFLINE);
        EasyMock.replay(mMockIDevice, mMockAdbBridge);
        DeviceManager manager = createDeviceManager();
        assertNull(manager.allocateDevice(MIN_ALLOCATE_WAIT_TIME));
    }

    /**
     * Verified that a newly connected offline device cannot be allocated
     */
    public void testAllocateDevice_connectedOffline() throws DeviceNotAvailableException {
        EasyMock.expect(mMockAdbBridge.getDevices()).andReturn(new IDevice[] {});
        EasyMock.expect(mMockIDevice.getState()).andReturn(DeviceState.OFFLINE);
        EasyMock.replay(mMockIDevice, mMockAdbBridge);
        DeviceManager manager = createDeviceManager();
        mDeviceListener.deviceConnected(mMockIDevice);
        assertNull(manager.allocateDevice(MIN_ALLOCATE_WAIT_TIME));
    }

    /**
     * Verified that a offline device that becomes online can be allocated
     */
    public void testAllocateDevice_offlineOnline() throws DeviceNotAvailableException {
        EasyMock.expect(mMockAdbBridge.getDevices()).andReturn(new IDevice[] {mMockIDevice});
        EasyMock.expect(mMockIDevice.getState()).andReturn(DeviceState.OFFLINE);
        EasyMock.expect(mMockIDevice.getState()).andReturn(DeviceState.ONLINE);
        EasyMock.expect(mMockMonitor.waitForDeviceAvailable(EasyMock.anyLong())).andReturn(
                mMockIDevice);
        EasyMock.replay(mMockIDevice, mMockMonitor, mMockAdbBridge);
        DeviceManager manager = createDeviceManager();
        mDeviceListener.deviceChanged(mMockIDevice, IDevice.CHANGE_STATE);
        assertNotNull(manager.allocateDevice());
    }

    /**
     * Verified that a online device that becomes offline can be allocated
     */
    public void testAllocateDevice_onlineOffline() throws DeviceNotAvailableException {
        EasyMock.expect(mMockAdbBridge.getDevices()).andReturn(new IDevice[] {mMockIDevice});
        EasyMock.expect(mMockIDevice.getState()).andReturn(DeviceState.ONLINE);
        EasyMock.expect(mMockMonitor.waitForDeviceAvailable(EasyMock.anyLong())).andReturn(
                mMockIDevice);
        EasyMock.expect(mMockIDevice.getState()).andReturn(DeviceState.OFFLINE);
        mMockTestDevice.stopLogcat();
        EasyMock.replay(mMockIDevice, mMockTestDevice, mMockMonitor, mMockAdbBridge);
        DeviceManager manager = createDeviceManager();
        // allocate and free device to avoid race condition with waitForDeviceAvailable being
        // called on background thread
        assertEquals(mMockTestDevice, manager.allocateDevice());
        manager.freeDevice(mMockTestDevice, FreeDeviceState.AVAILABLE);
        mDeviceListener.deviceChanged(mMockIDevice, IDevice.CHANGE_STATE);
        // verify device can still be allocated even though its in offline state
        // this is desired because then recovery can attempt to resurrect the device
        assertEquals(mMockTestDevice, manager.allocateDevice());
    }

    /**
     * Verified that a disconnected device state gets updated
     */
    public void testSetState_disconnected() throws DeviceNotAvailableException {
        EasyMock.expect(mMockAdbBridge.getDevices()).andReturn(new IDevice[] {mMockIDevice});
        EasyMock.expect(mMockIDevice.getState()).andReturn(DeviceState.ONLINE).times(2);
        EasyMock.expect(mMockMonitor.waitForDeviceAvailable(EasyMock.anyLong())).andReturn(
                mMockIDevice);
        mMockTestDevice.setDeviceState(TestDeviceState.NOT_AVAILABLE);
        EasyMock.replay(mMockMonitor, mMockTestDevice, mMockIDevice, mMockAdbBridge);
        DeviceManager manager = createDeviceManager();
        assertEquals(mMockTestDevice, manager.allocateDevice());
        mDeviceListener.deviceDisconnected(mMockIDevice);
        EasyMock.verify(mMockTestDevice);
    }

    /**
     * Verified that a offline device state gets updated
     */
    public void testSetState_offline() throws DeviceNotAvailableException {
        EasyMock.expect(mMockAdbBridge.getDevices()).andReturn(new IDevice[] {mMockIDevice});
        EasyMock.expect(mMockIDevice.getState()).andReturn(DeviceState.ONLINE).times(2);
        EasyMock.expect(mMockMonitor.waitForDeviceAvailable(EasyMock.anyLong())).andReturn(
                mMockIDevice);
        mMockTestDevice.setDeviceState(TestDeviceState.OFFLINE);
        EasyMock.replay(mMockMonitor, mMockTestDevice, mMockIDevice, mMockAdbBridge);
        DeviceManager manager = createDeviceManager();
        assertEquals(mMockTestDevice, manager.allocateDevice());
        IDevice newDevice = EasyMock.createMock(IDevice.class);
        EasyMock.expect(newDevice.getSerialNumber()).andReturn(DEVICE_SERIAL).anyTimes();
        EasyMock.expect(newDevice.getState()).andReturn(DeviceState.OFFLINE);
        EasyMock.replay(newDevice);
        mDeviceListener.deviceChanged(newDevice, IDevice.CHANGE_STATE);

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
