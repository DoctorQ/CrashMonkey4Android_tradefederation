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

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.Log;
import com.android.ddmlib.IDevice.DeviceState;

import java.util.HashSet;
import java.util.Set;

/**
 * {@inheritDoc}
 */
public class DeviceManager implements IDeviceManager {

    private static final String LOG_TAG = "DeviceManager";

    private static DeviceManager sInstance;

    /** Tracks the {@link IDevice#getSerialNumber()} currently allocated for testing. */
    private Set<String> mDeviceSerialsInUse;
    private IAndroidDebugBridge mAdbBridge;

    /** The default maximum time in ms to wait for a device to be connected. */
    private static final int DEFAULT_MAX_WAIT_DEVICE_TIME = 10 * 1000;

    /**
     * Package-private constructor, should only be used by this class and its associated unit test.
     * Use {@link #getInstance()} instead.
     */
    DeviceManager() {
        mDeviceSerialsInUse = new HashSet<String>();
        initAdb();
        mAdbBridge = createAdbBridge();
    }

    /**
     * Creates the static {@link IDeviceManager} instance to be used and initializes DDMS support.
     *
     * @param recovery the {@link IDeviceRecovery} to use.
     * @throws IllegalStateException if init has already been called
     */
    public synchronized static void init() {
        if (sInstance == null) {
            sInstance = new DeviceManager();
        } else {
            throw new IllegalStateException();
        }
    }

    /**
     * Return the previously created {@link IDeviceManager} instance to use.
     *
     * @throws IllegalStateException if init() has not been called
     */
    public synchronized static IDeviceManager getInstance() {
        if (sInstance == null) {
            throw new IllegalStateException();
        }
        return sInstance;
    }

    /**
     * {@inheritDoc}
     */
    public ITestDevice allocateDevice(IDeviceRecovery recovery) throws DeviceNotAvailableException {
        IDevice allocatedDevice = null;
        synchronized (mDeviceSerialsInUse) {
            for (IDevice device : mAdbBridge.getDevices()) {
                if (!mDeviceSerialsInUse.contains(device.getSerialNumber()) &&
                        device.getState() == DeviceState.ONLINE) {
                    mDeviceSerialsInUse.add(device.getSerialNumber());
                    allocatedDevice = device;
                }
            }
        }
        if (allocatedDevice == null) {
            // TODO: move to separate method
            allocatedDevice = waitForAnyConnectedDevice(DEFAULT_MAX_WAIT_DEVICE_TIME);
            synchronized (mDeviceSerialsInUse) {
                mDeviceSerialsInUse.add(allocatedDevice.getSerialNumber());
            }
        }
        // TODO: make background logcat capture optional
        ILogTestDevice testDevice =  new TestDevice(allocatedDevice, recovery,
                new DeviceStateMonitor(allocatedDevice, mAdbBridge));
        testDevice.startLogcat();
        return testDevice;
    }

    /**
     * Initialize the adb debug bridge.
     *
     * Exposed so tests can mock this.
     */
    synchronized void initAdb() {
        AndroidDebugBridge.init(false /* clientSupport */);
    }

    /**
     * Creates the {@link IAndroidDebugBridge} to use.
     * <p/>
     * Exposed so tests can mock this.
     * @returns the {@link IAndroidDebugBridge}
     */
    synchronized IAndroidDebugBridge createAdbBridge() {
        return new AndroidDebugBridgeWrapper();
    }

    /**
     * {@inheritDoc}
     */
    public void freeDevice(ITestDevice device) {
        if (device instanceof ILogTestDevice) {
            ((ILogTestDevice)device).stopLogcat();
        }
        synchronized (mDeviceSerialsInUse) {
            if (!mDeviceSerialsInUse.remove(device.getSerialNumber())) {
                Log.w(LOG_TAG, String.format("freeDevice called with unallocated device %s",
                        device.getSerialNumber()));
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public void registerListener(IDeviceListener listener) {
        // TODO implement this
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     */
    public void removeListener(IDeviceListener listener) {
        // TODO implement this
        throw new UnsupportedOperationException();
    }


    /**
     * {@inheritDoc}
     */
    public void terminate() {
        AndroidDebugBridge.terminate();
    }

    private IDevice waitForAnyConnectedDevice(long time) throws DeviceNotAvailableException {
        Log.i(LOG_TAG, String.format("Waiting for a connected device..."));
        AdbDeviceListener listener = new AdbDeviceListener(null, TestDeviceState.ONLINE);
        mAdbBridge.addDeviceChangeListener(listener);
        IDevice device = listener.waitForDevice(time);
        mAdbBridge.removeDeviceChangeListener(listener);
        if (device == null) {
            throw new DeviceNotAvailableException("A device could not be found");
        } else {
            Log.i(LOG_TAG, String.format("Found device %s", device.getSerialNumber()));
        }
        return device;

    }
}
