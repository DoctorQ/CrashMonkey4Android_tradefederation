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

import junit.framework.TestCase;

/**
 * Unit tests for {@link DeviceManager}.
 */
public class DeviceManagerTest extends TestCase {

    private static final String DEVICE_SERIAL = "1";

    private DeviceManager mDeviceManager;
    private IAndroidDebugBridge mMockAdbBridge;
    private IDeviceRecovery mMockRecovery;

    /**
     * {@inheritDoc}
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mMockAdbBridge = EasyMock.createMock(IAndroidDebugBridge.class);
        mMockRecovery = EasyMock.createMock(IDeviceRecovery.class);
        mDeviceManager = new DeviceManager() {
            @Override
            void initAdb() {
                // do nothing
            }

            @Override
            IAndroidDebugBridge createAdbBridge() {
                return mMockAdbBridge;
            }
        };
    }

    /**
     * Test method for normal case {@link DeviceManager#allocateDevice()}.
     */
    public void testAllocateDevice() throws DeviceNotAvailableException {
        doTestAllocateDevice();
    }

    /**
     * Perform a normal case {@link DeviceManager#allocateDevice()} test scenario.
     */
    private ITestDevice doTestAllocateDevice() throws DeviceNotAvailableException {
        IDevice mockDevice = EasyMock.createNiceMock(IDevice.class);
        EasyMock.expect(mockDevice.getSerialNumber()).andReturn(DEVICE_SERIAL);
        EasyMock.expect(mockDevice.getState()).andReturn(DeviceState.ONLINE);
        EasyMock.expect(mMockAdbBridge.getDevices()).andReturn(new IDevice[] {mockDevice});
        EasyMock.replay(mockDevice);
        EasyMock.replay(mMockAdbBridge);
        ITestDevice testDevice = mDeviceManager.allocateDevice(mMockRecovery);
        assertEquals(mockDevice, testDevice.getIDevice());
        return testDevice;
    }

    /**
     * Test method for {@link DeviceManager#allocateDevice()} that checks if device has been
     * previously allocated, it will wait for new one.
     */
    public void testAllocateDevice_wait() throws DeviceNotAvailableException {
        final IDevice mockDevice = EasyMock.createNiceMock(IDevice.class);
        EasyMock.expect(mockDevice.getSerialNumber()).andReturn(DEVICE_SERIAL);
        EasyMock.expect(mockDevice.getState()).andReturn(DeviceState.ONLINE);
        // first call, return nothing
        EasyMock.expect(mMockAdbBridge.getDevices()).andReturn(new IDevice[] {});
        mMockAdbBridge.addDeviceChangeListener((IDeviceChangeListener)EasyMock.anyObject());
        EasyMock.expectLastCall().andDelegateTo(new IAndroidDebugBridge() {

            public void addDeviceChangeListener(final IDeviceChangeListener listener) {
                // call the listener back on a different thread
                new Thread() {
                    @Override
                    public void run() {
                        listener.deviceConnected(mockDevice);
                    }
                }.start();
            }

            public IDevice[] getDevices() {
                return null;
            }

            public void removeDeviceChangeListener(IDeviceChangeListener listener) {
            }

        });
        mMockAdbBridge.removeDeviceChangeListener((IDeviceChangeListener)EasyMock.anyObject());

        EasyMock.replay(mockDevice);
        EasyMock.replay(mMockAdbBridge);
        ITestDevice testDevice = mDeviceManager.allocateDevice(mMockRecovery);
        assertEquals(mockDevice, testDevice.getIDevice());
    }

    /**
     * Test method for {@link DeviceManager#freeDevice(ITestDevice)}.
     */
    public void testFreeDevice() throws DeviceNotAvailableException {
        ITestDevice testDevice = doTestAllocateDevice();
        mDeviceManager.freeDevice(testDevice);
        // verify same device can be allocated again
        EasyMock.reset(mMockAdbBridge);
        doTestAllocateDevice();
    }

    /**
     * Verified that {@link DeviceManager#freeDevice(ITestDevice)} ignores a call with a device
     * that has not been allocated.
     */
    public void testFreeDevice_noop() throws DeviceNotAvailableException {
        IDevice mockIDevice = EasyMock.createMock(IDevice.class);
        ITestDevice testDevice = EasyMock.createNiceMock(ITestDevice.class);
        EasyMock.expect(testDevice.getSerialNumber()).andReturn("dontexist");
        EasyMock.replay(testDevice);
        EasyMock.replay(mockIDevice);
        mDeviceManager.freeDevice(testDevice);
    }


    /**
     * Test method for {@link DeviceManager#registerListener(DeviceListener)}.
     */
    public void testRegisterListener() {
        // TODO: implement this
    }

    /**
     * Test method for {@link DeviceManager#removeListener(DeviceListener)}.
     */
    public void testRemoveListener() {
        // TODO: implement this
    }
}
