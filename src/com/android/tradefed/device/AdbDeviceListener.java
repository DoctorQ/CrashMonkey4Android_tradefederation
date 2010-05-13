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
import com.android.ddmlib.Log;
import com.android.ddmlib.AndroidDebugBridge.IDeviceChangeListener;

/**
 * Listener for monitoring a DDMS device state.
 */
class AdbDeviceListener implements IDeviceChangeListener {

    private static final String LOG_TAG = "AdbDeviceListener";

    private IDevice mDevice;
    private final String mSerial;
    private final TestDeviceState mExpectedState;

    AdbDeviceListener(String serial, TestDeviceState expectedState) {
        mSerial = serial;
        mExpectedState = expectedState;
    }

    /**
     * {@inheritDoc}
     */
    public void deviceChanged(IDevice device, int changeMask) {
        if ((changeMask & IDevice.CHANGE_STATE) != 0) {
           if (mExpectedState.getDdmsState() == device.getState()) {
               setDevice(device);
           }
        }
    }

    /**
     * {@inheritDoc}
     */
    public void deviceConnected(IDevice device) {
        if (mExpectedState == TestDeviceState.ONLINE &&
                mExpectedState.getDdmsState() == device.getState()) {
            if (mSerial == null) {
                setDevice(device);
            } else if (mSerial.equals(device.getSerialNumber())) {
                setDevice(device);
            }
        }
    }

    private synchronized void setDevice(IDevice device) {
        mDevice = device;
        notify();
    }

    /**
     * {@inheritDoc}
     */
    public void deviceDisconnected(IDevice device) {
        if (mExpectedState == TestDeviceState.NOT_AVAILABLE &&
                device.getSerialNumber().equals(mSerial)) {
            setDevice(device);
        }
    }

    /**
     * Block and wait for device to reach expected state.
     *
     * @param waitTime tiem in ms to wait for device.
     * @return the {@link IDevice} or <code>null</code> if it did not reach expected state
     */
    IDevice waitForDevice(long waitTime) {
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
