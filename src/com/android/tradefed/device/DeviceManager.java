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
import com.android.ddmlib.AndroidDebugBridge.IDeviceChangeListener;

import java.util.HashSet;
import java.util.Set;

/**
 * {@inheritDoc}
 */
public class DeviceManager implements IDeviceManager {

    private static DeviceManager sInstance;

    /** Tracks the {@link IDevice#getSerialNumber()} currently allocated for testing. */
    private Set<String> mDeviceSerialsInUse;
    private IAndroidDebugBridge mAdbBridge;
    private final IDeviceRecovery mRecovery;

    /** The default maximum time in ms to wait for a device to be connected. */
    private static final int DEFAULT_MAX_WAIT_DEVICE_TIME = 10 * 1000;

    private static final String LOG_TAG = "DeviceManager";

    /**
     * Package-private constructor, should only be used by this class and its associated unit test.
     * Use {@link #getInstance()} instead.
     */
    DeviceManager(IDeviceRecovery recovery) {
        mDeviceSerialsInUse = new HashSet<String>();
        initAdb();
        mAdbBridge = createAdbBridge();
        mRecovery = recovery;
    }

    /**
     * Creates the static {@link IDeviceManager} instance to be used.
     *
     * @param recovery the {@link IDeviceRecovery} to use.
     * @throws IllegalStateException if init has already been called
     */
    public synchronized static void init(IDeviceRecovery recovery) {
        if (sInstance == null) {
            sInstance = new DeviceManager(recovery);
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
    public ITestDevice allocateDevice() throws DeviceNotAvailableException {
        synchronized (mDeviceSerialsInUse) {
            for (IDevice device : mAdbBridge.getDevices()) {
                if (!mDeviceSerialsInUse.contains(device.getSerialNumber())) {
                    mDeviceSerialsInUse.add(device.getSerialNumber());
                    return new TestDevice(device, mRecovery);
                }
            }
        }
        // TODO: move this to a separate method
        IDevice device = waitForDevice((String)null, DEFAULT_MAX_WAIT_DEVICE_TIME);
        synchronized (mDeviceSerialsInUse) {
            mDeviceSerialsInUse.add(device.getSerialNumber());
        }
        // TODO: make background logcat capture optional
        ILogTestDevice testDevice =  new TestDevice(device, mRecovery);
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
    public void waitForDevice(ITestDevice testDevice, long time)
            throws DeviceNotAvailableException {
        if (testDevice.getIDevice().isOnline()) {
            // if device has disappeared from adb entirely, it may still be marked as online
            // ensure its active on adb bridge
            for (IDevice device : mAdbBridge.getDevices()) {
                if (device.getSerialNumber().equals(testDevice.getSerialNumber())) {
                    Log.i(LOG_TAG, String.format("Device %s is already online",
                            device.getSerialNumber()));
                    return;
                }
            }
        }
        waitForDevice(testDevice.getSerialNumber(), time);
    }

    private IDevice waitForDevice(String deviceSerial, long time)
            throws DeviceNotAvailableException {
        Log.i(LOG_TAG, String.format("Waiting for device %s...", deviceSerial));
        AdbDeviceListener listener = new AdbDeviceListener(deviceSerial);
        mAdbBridge.addDeviceChangeListener(listener);
        IDevice device = listener.waitForDevice(time);
        mAdbBridge.removeDeviceChangeListener(listener);
        if (device == null) {
            throw new DeviceNotAvailableException(String.format("Could not connect to device %s",
                    deviceSerial));
        } else {
            Log.i(LOG_TAG, String.format("Connected to %s", device.getSerialNumber()));
        }
        return device;
    }

    /**
     * Listens to DDMS for an Android devices.
     */
    private static class AdbDeviceListener implements IDeviceChangeListener {
        private IDevice mDevice;
        private String mSerial;

        public AdbDeviceListener(String serial) {
            mSerial = serial;
        }

        public void deviceChanged(IDevice device, int changeMask) {
            // TODO: handle this
        }

        public void deviceConnected(IDevice device) {
            if (mSerial == null) {
                setDevice(device);
            } else if (mSerial.equals(device.getSerialNumber())) {
                setDevice(device);
            }
        }

        private synchronized void setDevice(IDevice device) {
            mDevice = device;
            notify();
        }

        public void deviceDisconnected(IDevice device) {
        }

        public IDevice waitForDevice(long waitTime) {
            synchronized (this) {
                if (mDevice == null) {
                    try {
                        wait(waitTime);
                    } catch (InterruptedException e) {
                        Log.w(LOG_TAG, "Waiting for device interrupted");
                    }
                }
            }
            return mDevice;
        }
    }
}
