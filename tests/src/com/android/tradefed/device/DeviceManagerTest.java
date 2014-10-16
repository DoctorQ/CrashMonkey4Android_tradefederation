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

import com.android.ddmlib.AndroidDebugBridge.IDeviceChangeListener;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.IDevice.DeviceState;
import com.android.tradefed.config.IGlobalConfiguration;
import com.android.tradefed.device.IDeviceManager.FreeDeviceState;
import com.android.tradefed.device.IDeviceMonitor.DeviceLister;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.IRunUtil;

import junit.framework.TestCase;

import org.easymock.EasyMock;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;

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

    /**
     * Helper interface to mock behavior for
     * {@link DeviceManager#createTestDevice(IDevice, IDeviceStateMonitor)}.
     */
    private interface ITestDeviceFactory {
        IManagedTestDevice createDevice();
    }

    private IAndroidDebugBridge mMockAdbBridge;
    private IDevice mMockIDevice;
    private IDeviceStateMonitor mMockMonitor;
    private IManagedTestDevice mMockTestDevice;
    private IRunUtil mMockRunUtil;
    private ITestDeviceFactory mMockDeviceFactory;
    private IGlobalConfiguration mMockGlobalConfig;

    /** a reference to the DeviceManager's IDeviceChangeListener. Used for triggering device
     * connection events */
    private IDeviceChangeListener mDeviceListener;

    static class MockProcess extends Process {

        /**
         * {@inheritDoc}
         */
        @Override
        public void destroy() {
            // ignore
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int exitValue() {
            return 0;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public InputStream getErrorStream() {
            return null;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public InputStream getInputStream() {
            return null;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public OutputStream getOutputStream() {
            return null;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int waitFor() throws InterruptedException {
            return 0;
        }

    }


    /**
     * {@inheritDoc}
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mMockAdbBridge = EasyMock.createNiceMock(IAndroidDebugBridge.class);
        mMockAdbBridge.addDeviceChangeListener((IDeviceChangeListener)EasyMock.anyObject());
        EasyMock.expectLastCall().andDelegateTo(new IAndroidDebugBridge() {
            @Override
            public void addDeviceChangeListener(final IDeviceChangeListener listener) {
                mDeviceListener = listener;
            }

            @Override
            public IDevice[] getDevices() {
                return null;
            }

            @Override
            public void removeDeviceChangeListener(IDeviceChangeListener listener) {
            }

            @Override
            public void init(boolean clientSupport, String adbOsLocation) {
            }

            @Override
            public void terminate() {
            }

            @Override
            public void disconnectBridge() {
            }
        });
        mMockIDevice = EasyMock.createMock(IDevice.class);
        mMockMonitor = EasyMock.createMock(IDeviceStateMonitor.class);
        mMockTestDevice = EasyMock.createMock(IManagedTestDevice.class);
        mMockRunUtil = EasyMock.createMock(IRunUtil.class);
        mMockDeviceFactory = EasyMock.createMock(ITestDeviceFactory.class);
        mMockGlobalConfig = EasyMock.createNiceMock(IGlobalConfiguration.class);

        EasyMock.expect(mMockIDevice.getSerialNumber()).andStubReturn(DEVICE_SERIAL);
        EasyMock.expect(mMockIDevice.isEmulator()).andStubReturn(Boolean.FALSE);

        EasyMock.expect(mMockTestDevice.getSerialNumber()).andStubReturn(DEVICE_SERIAL);

        EasyMock.expect(mMockTestDevice.getIDevice()).andStubReturn(mMockIDevice);
        EasyMock.expect(mMockRunUtil.runTimedCmd(EasyMock.anyLong(), (String)EasyMock.anyObject(),
                (String)EasyMock.anyObject())).andStubReturn(new CommandResult());
        EasyMock.expect(mMockRunUtil.runTimedCmdSilently(EasyMock.anyLong(), (String)EasyMock.
                anyObject(), (String)EasyMock.anyObject())).andStubReturn(new CommandResult());

        EasyMock.expect(mMockGlobalConfig.getDeviceRequirements()).andStubReturn(
                DeviceManager.ANY_DEVICE_OPTIONS);
    }

    private DeviceManager createDeviceManager(IDevice... devices) {
        DeviceManager mgr = createDeviceManagerNoInit();
        mgr.init();
        for (IDevice device : devices) {
            mDeviceListener.deviceConnected(device);
        }
        return mgr;
    }

    private DeviceManager createDeviceManagerNoInit() {

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
                return mMockDeviceFactory.createDevice();
            }

            @Override
            IGlobalConfiguration getGlobalConfig() {
                return mMockGlobalConfig;
            }

            @Override
            IRunUtil getRunUtil() {
                return mMockRunUtil;
            }
        };
        mgr.setEnableLogcat(false);
        mgr.setSynchronousMode(true);
        return mgr;
    }

    /**
     * Test @link DeviceManager#allocateDevice()} when a IDevice is present on DeviceManager
     * creation.
     */
    public void testAllocateDevice() throws DeviceNotAvailableException {
        setCheckAvailableDeviceExpectations();
        replayMocks();
        DeviceManager manager = createDeviceManager(mMockIDevice);
        assertEquals(mMockTestDevice, manager.allocateDevice());
        EasyMock.verify(mMockMonitor);
    }

    /**
     * Verify that {@link DeviceManager#allocateDevice()} can allocate an asynchronously-connected
     * device.
     */
    public void testAllocateDevice_wait() throws DeviceNotAvailableException {
        setCheckAvailableDeviceExpectations();
        replayMocks();
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
     * Test {@link DeviceManager#allocateDevice(long))} when device is returned
     */
    public void testAllocateDevice_time() throws DeviceNotAvailableException {
        setCheckAvailableDeviceExpectations();
        replayMocks();
        DeviceManager manager = createDeviceManager(mMockIDevice);
        assertEquals(mMockTestDevice, manager.allocateDevice(100));
        EasyMock.verify(mMockMonitor);
    }

    /**
     * Test {@link DeviceManager#allocateDevice(long))} when timeout is reached.
     */
    public void testAllocateDevice_timeout() throws DeviceNotAvailableException {
        replayMocks();
        DeviceManager manager = createDeviceManager();
        assertNull(manager.allocateDevice(MIN_ALLOCATE_WAIT_TIME));
    }

    /**
     * Test {@link DeviceManager#allocateDevice(long, DeviceSelectionOptions))} when device is
     * returned.
     */
    public void testAllocateDevice_match() throws DeviceNotAvailableException {
        DeviceSelectionOptions options = new DeviceSelectionOptions();
        options.addSerial(DEVICE_SERIAL);
        setCheckAvailableDeviceExpectations();
        replayMocks();
        DeviceManager manager = createDeviceManager(mMockIDevice);
        assertEquals(mMockTestDevice, manager.allocateDevice(100, options));
        EasyMock.verify(mMockMonitor);
    }

    /**
     * Test {@link DeviceManager#allocateDevice(long, DeviceSelectionOptions))} when timeout is
     * reached.
     */
    public void testAllocateDevice_matchTimeout() throws DeviceNotAvailableException {
        DeviceSelectionOptions options = new DeviceSelectionOptions();
        options.addExcludeSerial(DEVICE_SERIAL);
        setCheckAvailableDeviceExpectations();
        replayMocks();
        DeviceManager manager = createDeviceManager(mMockIDevice);
        assertNull(manager.allocateDevice(MIN_ALLOCATE_WAIT_TIME, options));
        assertNotNull(manager.allocateDevice(MIN_ALLOCATE_WAIT_TIME));
    }

    /**
     * Test {@link DeviceManager#allocateDevice(long, DeviceSelectionOptions))} when stub emulator is
     * requested
     */
    public void testAllocateDevice_stubEmulator() throws DeviceNotAvailableException {
        DeviceSelectionOptions options = new DeviceSelectionOptions();
        options.setStubEmulatorRequested(true);
        EasyMock.expect(mMockDeviceFactory.createDevice()).andReturn(mMockTestDevice);
        replayMocks();
        DeviceManager manager = createDeviceManager();
        assertNotNull(manager.allocateDevice(100, options));
    }

    /**
     * Test freeing an emulator
     */
    public void testFreeDevice_emulator() throws DeviceNotAvailableException {
        DeviceSelectionOptions options = new DeviceSelectionOptions();
        options.setStubEmulatorRequested(true);
        IManagedTestDevice mockEmulator = EasyMock.createMock(IManagedTestDevice.class);
        EasyMock.expect(mockEmulator.getSerialNumber()).andStubReturn("emulator-5554");
        // allocate call
        EasyMock.expect(mMockDeviceFactory.createDevice()).andReturn(mockEmulator).times(2);
        // simulate a emulator launch
        EasyMock.expect(mockEmulator.getEmulatorProcess()).andReturn(new MockProcess()).times(2);
        IDevice mockIEmulator = EasyMock.createMock(IDevice.class);
        EasyMock.expect(mockIEmulator.getSerialNumber()).andStubReturn("emulator-5554");
        EasyMock.expect(mockEmulator.getIDevice()).andStubReturn(mockIEmulator);
        EasyMock.expect(mockIEmulator.isEmulator()).andReturn(Boolean.TRUE);
        EasyMock.expect(mockEmulator.waitForDeviceNotAvailable(EasyMock.anyLong())).andReturn(
                Boolean.TRUE);
        mockEmulator.stopLogcat();
        replayMocks(mockEmulator, mockIEmulator);
        DeviceManager manager = createDeviceManager();
        assertEquals(mockEmulator, manager.allocateDevice(100, options));
        // a freed 'unavailable' emulator should be returned to the available queue.
        manager.freeDevice(mockEmulator, FreeDeviceState.UNAVAILABLE);
        // ensure device can be allocated again
        assertEquals(mockEmulator, manager.allocateDevice(100, options));
    }

    /**
     * Test {@link DeviceManager#allocateDevice(long, DeviceSelectionOptions))} when a null device
     * is requested.
     */
    public void testAllocateDevice_nullDevice() throws DeviceNotAvailableException {
        DeviceSelectionOptions options = new DeviceSelectionOptions();
        options.setNullDeviceRequested(true);
        EasyMock.expect(mMockDeviceFactory.createDevice()).andReturn(mMockTestDevice);
        replayMocks();
        DeviceManager manager = createDeviceManager();
        assertNotNull(manager.allocateDevice(100, options));
    }

    /**
     * Test that DeviceManager will add devices on fastboot to available queue on startup, and
     * that they can be allocated.
     */
    public void testAllocateDevice_fastboot() throws DeviceNotAvailableException {
        EasyMock.reset(mMockRunUtil);
        // mock 'fastboot help' call
        EasyMock.expect(mMockRunUtil.runTimedCmdSilently(EasyMock.anyLong(),
                EasyMock.eq("fastboot"), EasyMock.eq("help"))).andReturn(new CommandResult(
                        CommandStatus.SUCCESS));

        // mock 'fastboot devices' call to return one device
        CommandResult fastbootResult = new CommandResult(
                CommandStatus.SUCCESS);
        fastbootResult.setStdout("serial        fastboot\n");
        EasyMock.expect(mMockRunUtil.runTimedCmd(EasyMock.anyLong(),
                EasyMock.eq("fastboot"), EasyMock.eq("devices"))).andReturn(fastbootResult);

        mMockTestDevice.setDeviceState(TestDeviceState.FASTBOOT);
        EasyMock.expect(mMockDeviceFactory.createDevice()).andReturn(mMockTestDevice);

        replayMocks();
        DeviceManager manager = createDeviceManager();
        assertNotNull(manager.allocateDevice(100));
    }

    /**
     * Test {@link DeviceManager#forceAllocateDevice(String)} when device is unknown
     */
    public void testForceAllocateDevice() throws DeviceNotAvailableException {
        EasyMock.expect(mMockDeviceFactory.createDevice()).andReturn(mMockTestDevice);
        replayMocks();
        DeviceManager manager = createDeviceManager();
        assertNotNull(manager.forceAllocateDevice("unknownserial"));
    }

    /**
     * Test {@link DeviceManager#forceAllocateDevice(String)} when device is available
     */
    public void testForceAllocateDevice_available() throws DeviceNotAvailableException {
        setCheckAvailableDeviceExpectations();
        replayMocks();
        DeviceManager manager = createDeviceManager(mMockIDevice);
        assertNotNull(manager.forceAllocateDevice(DEVICE_SERIAL));
    }

    /**
     * Test {@link DeviceManager#forceAllocateDevice(String)} when device is already allocated
     */
    public void testForceAllocateDevice_alreadyAllocated() throws DeviceNotAvailableException {
        setCheckAvailableDeviceExpectations();
        replayMocks();
        DeviceManager manager = createDeviceManager(mMockIDevice);
        assertNotNull(manager.allocateDevice(MIN_ALLOCATE_WAIT_TIME));
        assertNull(manager.forceAllocateDevice(DEVICE_SERIAL));
    }

    /**
     * Test method for {@link DeviceManager#freeDevice(ITestDevice)}.
     */
    public void testFreeDevice() throws DeviceNotAvailableException {
        setCheckAvailableDeviceExpectations();
        mMockTestDevice.stopLogcat();
        // mock the second allocate device call
        EasyMock.expect(mMockDeviceFactory.createDevice()).andReturn(mMockTestDevice);
        replayMocks();
        DeviceManager manager = createDeviceManager();
        mDeviceListener.deviceConnected(mMockIDevice);
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
        setCheckAvailableDeviceExpectations();
        IManagedTestDevice testDevice = EasyMock.createNiceMock(IManagedTestDevice.class);
        EasyMock.expect(testDevice.getSerialNumber()).andReturn("dontexist");
        EasyMock.expect(testDevice.getIDevice()).andReturn(EasyMock.createNiceMock(IDevice.class));

        replayMocks(testDevice);
        DeviceManager manager = createDeviceManager(mMockIDevice);
        manager.freeDevice(testDevice, FreeDeviceState.AVAILABLE);
    }

    /**
     * Verified that {@link DeviceManager} calls {@link IManagedTestDevice#setIDevice(IDevice)}
     * when DDMS allocates a new IDevice on connection.
     */
    public void testSetIDevice() throws DeviceNotAvailableException {
        setCheckAvailableDeviceExpectations();
        IDevice newMockDevice = EasyMock.createMock(IDevice.class);
        EasyMock.expect(newMockDevice.getSerialNumber()).andReturn(DEVICE_SERIAL).anyTimes();
        EasyMock.expect(newMockDevice.getState()).andReturn(DeviceState.ONLINE);
        mMockTestDevice.setDeviceState(TestDeviceState.NOT_AVAILABLE);
        mMockTestDevice.setIDevice(newMockDevice);
        mMockTestDevice.setDeviceState(TestDeviceState.ONLINE);
        replayMocks(newMockDevice);
        DeviceManager manager = createDeviceManager(mMockIDevice);
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
        setCheckAvailableDeviceExpectations();
        mMockTestDevice.stopLogcat();
        replayMocks();
        DeviceManager manager = createDeviceManager(mMockIDevice);
        // allocate and free the device first to handle the asynchronous checkAvailableDevice()
        // stuff
        ITestDevice device = manager.allocateDevice(MIN_ALLOCATE_WAIT_TIME);
        assertNotNull(device);
        manager.freeDevice(device, FreeDeviceState.AVAILABLE);
        mDeviceListener.deviceDisconnected(mMockIDevice);
        assertNull(manager.allocateDevice(MIN_ALLOCATE_WAIT_TIME));
    }

    /**
     * Verified that a offline device cannot be allocated
     */
    public void testAllocateDevice_offline() throws DeviceNotAvailableException {
        EasyMock.expect(mMockIDevice.getState()).andReturn(DeviceState.OFFLINE);
        replayMocks();
        DeviceManager manager = createDeviceManager(mMockIDevice);
        assertNull(manager.allocateDevice(MIN_ALLOCATE_WAIT_TIME));
    }

    /**
     * Verified that a newly connected offline device cannot be allocated
     */
    public void testAllocateDevice_connectedOffline() throws DeviceNotAvailableException {
        EasyMock.expect(mMockIDevice.getState()).andReturn(DeviceState.OFFLINE);
        replayMocks();
        DeviceManager manager = createDeviceManager();
        mDeviceListener.deviceConnected(mMockIDevice);
        assertNull(manager.allocateDevice(MIN_ALLOCATE_WAIT_TIME));
    }

    /**
     * Verified that a offline device that becomes online can be allocated
     */
    public void testAllocateDevice_offlineOnline() throws DeviceNotAvailableException {
        EasyMock.expect(mMockIDevice.getState()).andReturn(DeviceState.OFFLINE);
        setCheckAvailableDeviceExpectations();
        replayMocks();
        DeviceManager manager = createDeviceManager(mMockIDevice);
        mDeviceListener.deviceChanged(mMockIDevice, IDevice.CHANGE_STATE);
        assertNotNull(manager.allocateDevice());
    }

    /**
     * Test {@link DeviceManager#allocateDevice()} when {@link DeviceManager#init()} has not
     * been called.
     */
    public void testAllocateDevice_noInit() throws DeviceNotAvailableException {
        try {
            createDeviceManagerNoInit().allocateDevice();
            fail("IllegalStateException not thrown when manager has not been initialized");
        } catch (IllegalStateException e) {
            // expected
        }
    }

    /**
     * Verify {@link DeviceManager#allocateDevice()} serves callers in a first-called-first-served
     * order.
     */
    public void testAllocateDevice_firstCalledFirstServed() throws Exception {
        setCheckAvailableDeviceExpectations();
        // keep EasyMock happy - expect stopLogcat call on each freeDevice call
        mMockTestDevice.stopLogcat();
        EasyMock.expectLastCall().times(2);
        EasyMock.expect(mMockDeviceFactory.createDevice()).andReturn(mMockTestDevice).times(2);
        replayMocks();
        // simulate no devices available on DeviceManager start up
        DeviceManager manager = createDeviceManager();

        AllocateCaller firstCaller = new AllocateCaller(manager);
        AllocateCaller secondCaller = new AllocateCaller(manager);
        AllocateCaller thirdCaller = new AllocateCaller(manager);
        firstCaller.startAndWait();
        secondCaller.startAndWait();
        thirdCaller.startAndWait();
        // add a device which can be allocated
        mDeviceListener.deviceConnected(mMockIDevice);
        // expect that the firstCaller receives this device
        assertTrue(firstCaller.waitForAllocate());
        manager.freeDevice(firstCaller.mAllocatedDevice, FreeDeviceState.AVAILABLE);
        // expect that the second caller gets the device once freed
        assertTrue(secondCaller.waitForAllocate());
        manager.freeDevice(secondCaller.mAllocatedDevice, FreeDeviceState.AVAILABLE);
        // expect that, finally, the thirdCaller gets the device once freed for the second time
        assertTrue(thirdCaller.waitForAllocate());
    }

    /**
     * A helper class for performing {@link DeviceManager#allocateDevice()} calls on a background
     * thread
     */
    private static class AllocateCaller extends Thread {
        ITestDevice mAllocatedDevice = null;
        final IDeviceManager mManager;
        AllocateCaller(IDeviceManager manager) {
            mManager = manager;
        }

        @Override
        public void run() {
            synchronized (this) {
                notify();
            }
            mAllocatedDevice = mManager.allocateDevice();
            synchronized (this) {
                notify();
            }
        }

        /**
         * Starts this thread, and blocks until it actually starts
         */
        public void startAndWait() throws InterruptedException {
            mAllocatedDevice = null;
            synchronized (this) {
                start();
                wait();
            }
            // hack, sleep a small amount for allocate call to really occur
            Thread.sleep(10);
        }

        /**
         * Waits for the {@link DeviceManager#allocateDevice()} call to occur. Assumes thread is
         * already started
         * @return <code>true</code> if device was allocated, <code>false</code> otherwise
         * @throws InterruptedException
         */
        public boolean waitForAllocate() throws InterruptedException  {
            synchronized (this) {
                wait(MIN_ALLOCATE_WAIT_TIME);
            }
            return mAllocatedDevice != null;
        }
    }

    /**
     * Test @link DeviceManager#allocateDevice()} when a IDevice is present on DeviceManager
     * creation.
     * <p />
     * FIXME: simplify call structure
     */
    public void testMonitor_allocate() throws DeviceNotAvailableException {
        final IDeviceMonitor dvcMon = EasyMock.createStrictMock(IDeviceMonitor.class);
        EasyMock.expect(mMockGlobalConfig.getDeviceMonitor()).andStubReturn(dvcMon);

        // IDeviceMonitor calls, in order
        dvcMon.setDeviceLister((DeviceLister) EasyMock.anyObject());
        dvcMon.run();
        // add emulators
        dvcMon.notifyDeviceStateChange();
        // add null devices
        dvcMon.notifyDeviceStateChange();
        // allocate actual IDevice(s)
        dvcMon.notifyDeviceStateChange();
        // create ITestDevice from IDevice
        dvcMon.notifyDeviceStateChange();

        setCheckAvailableDeviceExpectations();
        replayMocks(dvcMon);
        DeviceManager manager = createDeviceManager(mMockIDevice);
        assertEquals(mMockTestDevice, manager.allocateDevice());
        EasyMock.verify(mMockMonitor, dvcMon);
    }

    /**
     * Test {@link DeviceManager#init(IDeviceSelectionOptions)} with a global exclusion filter
     */
    public void testInit_excludeDevice() throws DeviceNotAvailableException {
        EasyMock.expect(mMockIDevice.getState()).andReturn(DeviceState.ONLINE);
        replayMocks();
        DeviceManager manager = createDeviceManagerNoInit();
        DeviceSelectionOptions excludeFilter = new DeviceSelectionOptions();
        excludeFilter.addExcludeSerial(mMockIDevice.getSerialNumber());
        manager.init(excludeFilter);
        mDeviceListener.deviceConnected(mMockIDevice);
        assertNull(manager.allocateDevice(MIN_ALLOCATE_WAIT_TIME));
    }

    /**
     * Test {@link DeviceManager#init(IDeviceSelectionOptions)} with a global inclusion filter
     */
    public void testInit_includeDevice() throws DeviceNotAvailableException {
        IDevice excludedDevice = EasyMock.createMock(IDevice.class);
        EasyMock.expect(excludedDevice.getSerialNumber()).andStubReturn("excluded");
        EasyMock.expect(excludedDevice.getState()).andStubReturn(DeviceState.ONLINE);
        setCheckAvailableDeviceExpectations();
        replayMocks(excludedDevice);
        DeviceManager manager = createDeviceManagerNoInit();
        DeviceSelectionOptions includeFilter = new DeviceSelectionOptions();
        includeFilter.addSerial(mMockIDevice.getSerialNumber());
        manager.init(includeFilter);
        mDeviceListener.deviceConnected(mMockIDevice);
        mDeviceListener.deviceConnected(excludedDevice);
        assertEquals(mMockTestDevice, manager.allocateDevice());
        // ensure excludedDevice cannot be allocated
        assertNull(manager.allocateDevice(MIN_ALLOCATE_WAIT_TIME));
        EasyMock.verify(mMockMonitor);
    }

    /**
     * Verified that a online device that becomes offline can be allocated
     */
    public void testAllocateDevice_onlineOffline() throws DeviceNotAvailableException {
        setCheckAvailableDeviceExpectations();
        EasyMock.expect(mMockIDevice.getState()).andReturn(DeviceState.OFFLINE);
        mMockTestDevice.stopLogcat();
        mMockTestDevice.setDeviceState(TestDeviceState.OFFLINE);
        EasyMock.expect(mMockDeviceFactory.createDevice()).andReturn(mMockTestDevice);

        replayMocks();
        DeviceManager manager = createDeviceManager(mMockIDevice);
        mDeviceListener.deviceChanged(mMockIDevice, IDevice.CHANGE_STATE);
        // verify device can still be allocated even though its in offline state
        // this is desired because then recovery can attempt to resurrect the device
        assertEquals(mMockTestDevice, manager.allocateDevice());
    }

    /**
     * Verified that a disconnected device state gets updated
     */
    public void testSetState_disconnected() throws DeviceNotAvailableException {
        setCheckAvailableDeviceExpectations();
        mMockTestDevice.setDeviceState(TestDeviceState.NOT_AVAILABLE);
        replayMocks();
        DeviceManager manager = createDeviceManager(mMockIDevice);
        assertEquals(mMockTestDevice, manager.allocateDevice());
        mDeviceListener.deviceDisconnected(mMockIDevice);
        EasyMock.verify(mMockTestDevice);
    }

    /**
     * Verified that a offline device state gets updated
     */
    public void testSetState_offline() throws DeviceNotAvailableException {
        setCheckAvailableDeviceExpectations();
        mMockTestDevice.setDeviceState(TestDeviceState.OFFLINE);
        replayMocks();
        DeviceManager manager = createDeviceManager(mMockIDevice);
        assertEquals(mMockTestDevice, manager.allocateDevice());
        IDevice newDevice = EasyMock.createMock(IDevice.class);
        EasyMock.expect(newDevice.getSerialNumber()).andReturn(DEVICE_SERIAL).anyTimes();
        EasyMock.expect(newDevice.getState()).andReturn(DeviceState.OFFLINE);
        EasyMock.replay(newDevice);
        mDeviceListener.deviceChanged(newDevice, IDevice.CHANGE_STATE);

    }

    /**
     * Test that receiving two 'deviceConnected' events for the same device serial doesn't lead to
     * duplicate available device entries
     */
    public void testConnectWithoutDisconnect() {
        setCheckAvailableDeviceExpectations(mMockIDevice);
        IDevice mockDevice2 = EasyMock.createMock(IDevice.class);
        setCheckAvailableDeviceExpectations(mockDevice2);
        EasyMock.expect(mockDevice2.getSerialNumber()).andStubReturn(DEVICE_SERIAL);
        EasyMock.expect(mockDevice2.isEmulator()).andStubReturn(Boolean.FALSE);
        replayMocks(mockDevice2);
        DeviceManager manager = createDeviceManager(mMockIDevice);
        assertEquals(1, manager.getAvailableDevices().size());
        mDeviceListener.deviceConnected(mockDevice2);
        assertEquals(1, manager.getAvailableDevices().size());
        assertTrue(manager.getAvailableDeviceQueue().contains(mockDevice2));
        assertFalse(manager.getAvailableDeviceQueue().contains(mMockIDevice));
    }

    // TODO: add test for fastboot state changes

    /**
     * Verify the 'fastboot devices' output parsing
     */
    public void testParseDevicesOnFastboot() {
        Collection<String> deviceSerials = DeviceManager.parseDevicesOnFastboot(
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
    public void testParseDevicesOnFastboot_empty() {
        Collection<String> deviceSerials = DeviceManager.parseDevicesOnFastboot("");
        assertEquals(0, deviceSerials.size());
    }

    /**
     * Test normal success case for {@link DeviceManager#connectToTcpDevice(String)}
     */
    public void testConnectToTcpDevice() throws Exception {
        final String ipAndPort ="ip:5555";
        IManagedTestDevice mockTcpDevice = setConnectToTcpDeviceExpectations(ipAndPort);
        replayMocks(mockTcpDevice);
        DeviceManager manager = createDeviceManager();
        assertNotNull(manager.connectToTcpDevice(ipAndPort));
        // verify device is in allocated list
        assertTrue(manager.getAllocatedDevices().contains(ipAndPort));
        verifyMocks(mockTcpDevice);
    }

    /**
     * Test a {@link DeviceManager#connectToTcpDevice(String)} call where device is already
     * allocated
     */
    public void testConnectToTcpDevice_alreadyAllocated() throws Exception {
        final String ipAndPort ="ip:5555";
        IManagedTestDevice mockTcpDevice = setConnectToTcpDeviceExpectations(ipAndPort);
        replayMocks(mockTcpDevice);
        DeviceManager manager = createDeviceManager();
        assertNotNull(manager.connectToTcpDevice(ipAndPort));
        // now attempt to re-allocate
        assertNull(manager.connectToTcpDevice(ipAndPort));
        verifyMocks(mockTcpDevice);
    }

    /**
     * Test {@link DeviceManager#connectToTcpDevice(String)} where device does not appear on adb
     */
    public void testConnectToTcpDevice_notOnline() throws Exception {
        final String ipAndPort ="ip:5555";
        IManagedTestDevice mockTcpDevice = setConnectToTcpDeviceExpectations(ipAndPort);
        // assume last call is waitForOnline
        EasyMock.expectLastCall().andThrow(new DeviceNotAvailableException());
        EasyMock.expect(mockTcpDevice.getIDevice()).andStubReturn(mMockIDevice);
        mockTcpDevice.stopLogcat();
        replayMocks(mockTcpDevice);
        DeviceManager manager = createDeviceManager();
        assertNull(manager.connectToTcpDevice(ipAndPort));
        // verify device is not in allocated list
        assertFalse(manager.getAllocatedDevices().contains(ipAndPort));
        verifyMocks(mockTcpDevice);
    }

    /**
     * Test {@link DeviceManager#connectToTcpDevice(String)} where the 'adb connect' call fails.
     */
    public void testConnectToTcpDevice_connectFailed() throws Exception {
        final String ipAndPort ="ip:5555";
        IManagedTestDevice mockTcpDevice = EasyMock.createMock(IManagedTestDevice.class);
        EasyMock.expect(mockTcpDevice.getSerialNumber()).andStubReturn(ipAndPort);
        EasyMock.expect(mMockDeviceFactory.createDevice()).andReturn(mockTcpDevice);
        CommandResult connectResult = new CommandResult(CommandStatus.SUCCESS);
        connectResult.setStdout(String.format("failed to connect to %s", ipAndPort));
        EasyMock.expect(mMockRunUtil.runTimedCmd(EasyMock.anyLong(), EasyMock.eq("adb"),
                EasyMock.eq("connect"), EasyMock.eq(ipAndPort))).andReturn(connectResult).times(3);
        mMockRunUtil.sleep(EasyMock.anyLong());
        EasyMock.expectLastCall().times(3);
        mockTcpDevice.stopLogcat();
        EasyMock.expect(mockTcpDevice.getIDevice()).andStubReturn(mMockIDevice);
        replayMocks(mockTcpDevice);
        DeviceManager manager = createDeviceManager();
        assertNull(manager.connectToTcpDevice(ipAndPort));
        // verify device is not in allocated list
        assertFalse(manager.getAllocatedDevices().contains(ipAndPort));
        verifyMocks(mockTcpDevice);
    }

    /**
     * Test normal success case for {@link DeviceManager#disconnectFromTcpDevice(ITestDevice)}
     */
    public void testDisconnectFromTcpDevice() throws Exception {
        final String ipAndPort ="ip:5555";
        IManagedTestDevice mockTcpDevice = setConnectToTcpDeviceExpectations(ipAndPort);
        EasyMock.expect(mockTcpDevice.switchToAdbUsb()).andReturn(Boolean.TRUE);
        mockTcpDevice.stopLogcat();
        EasyMock.expect(mockTcpDevice.getIDevice()).andStubReturn(mMockIDevice);
        replayMocks(mockTcpDevice);
        DeviceManager manager = createDeviceManager();
        assertNotNull(manager.connectToTcpDevice(ipAndPort));
        manager.disconnectFromTcpDevice(mockTcpDevice);
        // verify device is not in allocated or available list
        assertFalse(manager.getAllocatedDevices().contains(ipAndPort));
        assertFalse(manager.getAvailableDevices().contains(ipAndPort));
        verifyMocks(mockTcpDevice);
    }

    /**
     * Test normal success case for {@link DeviceManager#reconnectDeviceToTcp(ITestDevice)}.
     */
    public void testReconnectDeviceToTcp() throws Exception {
        final String ipAndPort = "ip:5555";
        // use the mMockTestDevice as the initially connected to usb device
        setCheckAvailableDeviceExpectations();
        EasyMock.expect(mMockTestDevice.switchToAdbTcp()).andReturn(ipAndPort);
        IManagedTestDevice mockTcpDevice = setConnectToTcpDeviceExpectations(ipAndPort);
        replayMocks(mockTcpDevice);
        DeviceManager manager = createDeviceManager(mMockIDevice);
        assertEquals(mMockTestDevice, manager.allocateDevice(MIN_ALLOCATE_WAIT_TIME));
        assertEquals(mockTcpDevice, manager.reconnectDeviceToTcp(mMockTestDevice));
        verifyMocks();
    }

    /**
     * Test {@link DeviceManager#reconnectDeviceToTcp(ITestDevice)} when tcp connected device does
     * not come online.
     */
    public void testReconnectDeviceToTcp_notOnline() throws Exception {
        final String ipAndPort = "ip:5555";
        // use the mMockTestDevice as the initially connected to usb device
        setCheckAvailableDeviceExpectations();
        EasyMock.expect(mMockTestDevice.switchToAdbTcp()).andReturn(ipAndPort);
        IManagedTestDevice mockTcpDevice = setConnectToTcpDeviceExpectations(ipAndPort);
        EasyMock.expectLastCall().andThrow(new DeviceNotAvailableException());
        // expect recover to be attempted on usb device
        mMockTestDevice.recoverDevice();
        mockTcpDevice.stopLogcat();
        EasyMock.expect(mockTcpDevice.getIDevice()).andStubReturn(mMockIDevice);
        replayMocks(mockTcpDevice);
        DeviceManager manager = createDeviceManager(mMockIDevice);
        assertEquals(mMockTestDevice, manager.allocateDevice(MIN_ALLOCATE_WAIT_TIME));
        assertNull(manager.reconnectDeviceToTcp(mMockTestDevice));
        // verify device is not in allocated list
        assertFalse(manager.getAllocatedDevices().contains(ipAndPort));
        verifyMocks();
    }

    /**
     * Set EasyMock expectations for a successful {@link DeviceManager#connectToTcpDevice(String)}
     * call.
     *
     * @param ipAndPort the ip and port of the device
     * @return the mock tcp connected {@link IManagedTestDevice}
     * @throws DeviceNotAvailableException
     */
    private IManagedTestDevice setConnectToTcpDeviceExpectations(final String ipAndPort)
            throws DeviceNotAvailableException {
        IManagedTestDevice mockTcpDevice = EasyMock.createMock(IManagedTestDevice.class);
        EasyMock.expect(mockTcpDevice.getSerialNumber()).andStubReturn(ipAndPort);
        CommandResult connectResult = new CommandResult(CommandStatus.SUCCESS);
        connectResult.setStdout(String.format("connected to %s", ipAndPort));
        EasyMock.expect(mMockRunUtil.runTimedCmd(EasyMock.anyLong(), EasyMock.eq("adb"),
                EasyMock.eq("connect"), EasyMock.eq(ipAndPort))).andReturn(connectResult);
        EasyMock.expect(mMockDeviceFactory.createDevice()).andReturn(mockTcpDevice);
        mockTcpDevice.setRecovery((IDeviceRecovery)EasyMock.anyObject());
        mockTcpDevice.waitForDeviceOnline();
        return mockTcpDevice;
    }

    /**
     * Sets all member mock objects into replay mode.
     *
     * @param additionalMocks extra local mock objects to set to replay mode
     */
    private void replayMocks(Object... additionalMocks) {
        EasyMock.replay(mMockMonitor, mMockTestDevice, mMockIDevice, mMockAdbBridge, mMockRunUtil,
                mMockDeviceFactory, mMockGlobalConfig);
        for (Object mock : additionalMocks) {
            EasyMock.replay(mock);
        }
    }

    /**
     * Verify all member mock objects.
     *
     * @param additionalMocks extra local mock objects to set to verify
     */
    private void verifyMocks(Object... additionalMocks) {
        EasyMock.verify(mMockMonitor, mMockTestDevice, mMockIDevice, mMockAdbBridge, mMockRunUtil);
        for (Object mock : additionalMocks) {
            EasyMock.verify(mock);
        }
    }

    /**
     * Configure EasyMock expectations for a {@link DeviceManager#checkAndAddAvailableDevice()} call
     * for an online device
     */
    private void setCheckAvailableDeviceExpectations() {
        setCheckAvailableDeviceExpectations(mMockIDevice);
    }

    private void setCheckAvailableDeviceExpectations(IDevice iDevice) {
        EasyMock.expect(iDevice.getState()).andReturn(DeviceState.ONLINE);
        EasyMock.expect(mMockMonitor.waitForDeviceShell(EasyMock.anyLong())).andReturn(
                Boolean.TRUE);
        EasyMock.expect(mMockDeviceFactory.createDevice()).andReturn(mMockTestDevice);
    }
}
