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

// TODO: make this thread safe
/**
 * {@inheritDoc}
 */
public class DeviceManager implements IDeviceManager {

    private static DeviceManager sInstance;

    /** Tracks the {@link IDevice#getSerialNumber()} currently allocated for testing. */
    private Set<String> mDeviceSerialsInUse;
    private IAndroidDebugBridge mAdbBridge;

    /**
     * * The default maximum time to wait for a device to be connected.
     */
    private static final int DEFAULT_MAX_WAIT_DEVICE_TIME = 10000;

    private static final String LOG_TAG = "DeviceManager";

    /**
     * Package-private constructor, should only be used by this class and its associated unit test.
     * Use {@link #getInstance()} instead.
     */
    DeviceManager() {
        mDeviceSerialsInUse = new HashSet<String>();
        initAdb();
        mAdbBridge = createAdbBridge();
    }

    public static IDeviceManager getInstance() {
        if (sInstance == null) {
            sInstance = new DeviceManager();
        }
        return sInstance;
    }

    /**
     * {@inheritDoc}
     */
    public ITestDevice allocateDevice() throws DeviceNotAvailableException {
        for (IDevice device : mAdbBridge.getDevices()) {
            if (!mDeviceSerialsInUse.contains(device.getSerialNumber())) {
                mDeviceSerialsInUse.add(device.getSerialNumber());
                return new TestDevice(device);
            }
        }
        String deviceSerial = null;
        // TODO move this logic elsewhere
        Log.i(LOG_TAG, "Waiting for device...");
        NewDeviceListener listener = new NewDeviceListener(deviceSerial);
        mAdbBridge.addDeviceChangeListener(listener);
        IDevice device = listener.waitForDevice(DEFAULT_MAX_WAIT_DEVICE_TIME);
        mAdbBridge.removeDeviceChangeListener(listener);
        if (device == null) {
            throw new DeviceNotAvailableException("Could not connect to device");
        } else {
            Log.i(LOG_TAG, String.format("Connected to %s", device.getSerialNumber()));
        }
        return new TestDevice(device);

    }

    /**
     * Initialize the adb debug bridge.
     *
     * Exposed so tests can mock this.
     */
    void initAdb() {
        AndroidDebugBridge.init(false /* clientSupport */);
    }

    /**
     * Creates the {@link IAndroidDebugBridge} to use.
     * <p/>
     * Exposed so tests can mock this.
     * @returns the {@link IAndroidDebugBridge}
     */
    IAndroidDebugBridge createAdbBridge() {
        return new AndroidDebugBridgeWrapper();
    }

    /**
     * {@inheritDoc}
     */
    public void freeDevice(ITestDevice device) {
        // TODO: log error if not present
        mDeviceSerialsInUse.remove(device.getIDevice().getSerialNumber());
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
     * Listener for new Android devices.
     *
     * TODO: temporary class, remove this.
     */
    private static class NewDeviceListener implements IDeviceChangeListener {
        private IDevice mDevice;
        private String mSerial;

        public NewDeviceListener(String serial) {
            mSerial = serial;
        }

        public void deviceChanged(IDevice device, int changeMask) {
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
