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
import com.android.tradefed.util.RunUtil;
import com.android.tradefed.util.RunUtil.IRunnableResult;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Helper class for monitoring the state of a {@link IDevice}.
 */
class DeviceStateMonitor implements IDeviceStateMonitor {

    private static final String LOG_TAG = "DeviceStateMonitor";
    private IDevice mDevice;
    private TestDeviceState mDeviceState;

    /** the time in ms to wait between 'poll for responsiveness' attempts */
    private static final int CHECK_POLL_TIME = 5 * 1000;
    /** the maximum operation time in ms for a 'poll for responsiveness' command */
    private static final long MAX_OP_TIME = 30 * 1000;

    /** the ratio of time to wait for device to be online vs other tasks in
     * {@link DeviceManager#waitForDeviceAvailable(ITestDevice, long)}.
     */
    private static final float WAIT_DEVICE_ONLINE_RATIO = 0.2f;

    /** the ratio of time to wait for device to be online vs other tasks in
     * {@link DeviceManager#waitForDeviceAvailable(ITestDevice, long)}.
     */
    private static final float WAIT_DEVICE_PM_RATIO = 0.6f;

    /** the ratio of time to wait for device's external store to be mounted vs other tasks in
     * {@link DeviceManager#waitForDeviceAvailable(ITestDevice, long)}.
     */
    private static final float WAIT_DEVICE_STORE_RATIO = 0.2f;

    /** The  time in ms to wait for a device to boot. */
    // TODO: make this configurable
    private static final long DEFAULT_BOOT_TIMEOUT = 6 * 60 * 1000;

    private List<DeviceStateListener> mStateListeners;

    DeviceStateMonitor(IDevice device) {
        mDevice = device;
        mStateListeners = new ArrayList<DeviceStateListener>();
        mDeviceState = TestDeviceState.getStateByDdms(device.getState());
    }

    /**
     * {@inheritDoc}
     */
    public IDevice waitForDeviceOnline(long waitTime) {
        if (waitForDeviceState(TestDeviceState.ONLINE, waitTime)) {
            return getIDevice();
        }
        return null;
    }

    /**
     * @return
     */
    private IDevice getIDevice() {
        return mDevice;
    }

    /**
     * {@inheritDoc}
     */
    public IDevice waitForDeviceOnline() {
        return waitForDeviceOnline((long)(DEFAULT_BOOT_TIMEOUT*WAIT_DEVICE_ONLINE_RATIO));
    }

    /**
     * {@inheritDoc}
     */
    public boolean waitForDeviceNotAvailable(long waitTime) {
        return waitForDeviceState(TestDeviceState.NOT_AVAILABLE, waitTime);
    }

    /**
     * {@inheritDoc}
     */
    public IDevice waitForDeviceAvailable(final long waitTime) {
        // A device is currently considered "available" if and only if three events are true:
        // 1. Device is online aka visible via DDMS/adb
        // 2. Device's package manager is responsive
        // 3. Device's external storage is mounted
        //
        // The current implementation waits for each event to occur in sequence.
        //
        // Each wait for event call is allocated a certain percentage of the total waitTime.
        // These percentages are given by the constants WAIT_DEVICE_*_RATIO, whose values must add
        // up to 1.
        //
        // Note that this algorithm is somewhat limiting, because this method can return before
        // the total waitTime has actually expired. A potential future enhancement would be to add
        // logic to adjust for the time expired. ie if waitForDeviceOnline returns immediately,
        // give waitForPmResponsive more time to complete

        IDevice device = waitForDeviceOnline((long)(waitTime*WAIT_DEVICE_ONLINE_RATIO));
        if (device != null) {
             if (waitForPmResponsive((long)(waitTime*WAIT_DEVICE_PM_RATIO)) &&
                     waitForStoreMount((long)(waitTime*WAIT_DEVICE_STORE_RATIO))) {
                 return device;
             }
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    public IDevice waitForDeviceAvailable() {
        return waitForDeviceAvailable(DEFAULT_BOOT_TIMEOUT);
    }

    /**
     * Waits for the device package manager to be responsive.
     *
     * @param waitTime time in ms to wait before giving up
     * @return <code>true</code> if package manage becomes responsive before waitTime expires.
     * <code>false</code> otherwise
     */
    private boolean waitForPmResponsive(final long waitTime) {
        Log.i(LOG_TAG, String.format("Waiting for device %s package manager",
                getSerialNumber()));
        IRunnableResult pmPollRunnable = new IRunnableResult() {
            public boolean run() {
                final String cmd = "pm path android";
                try {
                    // TODO move collecting output command to IDevice
                    CollectingOutputReceiver receiver = new CollectingOutputReceiver();
                    // assume the 'adb shell pm path android' command will always
                    // return 'package: something' in the success case
                    getIDevice().executeShellCommand(cmd, receiver);
                    String output = receiver.getOutput();
                    Log.d(LOG_TAG, String.format("%s returned %s", cmd, output));
                    return output.contains("package:");
                } catch (IOException e) {
                    Log.i(LOG_TAG, String.format("%s failed: %s", cmd, e.getMessage()));
                    return false;
                }
            }
        };
        int numAttempts = (int)(waitTime / CHECK_POLL_TIME);
        return RunUtil.runTimedRetry(MAX_OP_TIME, CHECK_POLL_TIME, numAttempts,
                pmPollRunnable);
    }

    /**
     * Waits for the device's external store to be mounted.
     *
     * @param waitTime time in ms to wait before giving up
     * @return <code>true</code> if external store is mounted before waitTime expires.
     * <code>false</code> otherwise
     */
    private boolean waitForStoreMount(final long waitTime) {
        Log.i(LOG_TAG, String.format("Waiting for device %s external store",
                getSerialNumber()));

        IRunnableResult storePollRunnable = new IRunnableResult() {
            public boolean run() {
                final String cmd = "cat /proc/mounts";
                try {
                    String externalStore = getIDevice().getMountPoint(IDevice.MNT_EXTERNAL_STORAGE);
                    if (externalStore == null) {
                        Log.i(LOG_TAG, String.format(
                                "Failed to get external store mount point for %s",
                                getSerialNumber()));
                        return false;
                    }
                    // TODO move collecting output command to IDevice
                    CollectingOutputReceiver receiver = new CollectingOutputReceiver();
                    getIDevice().executeShellCommand(cmd, receiver);
                    String output = receiver.getOutput();
                    Log.d(LOG_TAG, String.format("%s returned %s", cmd, output));
                    return output.contains(externalStore);
                } catch (IOException e) {
                    Log.i(LOG_TAG, String.format("%s failed: %s", cmd, e.getMessage()));
                    return false;
                }
            }
        };
        int numAttempts = (int)(waitTime / CHECK_POLL_TIME);
        return RunUtil.runTimedRetry(MAX_OP_TIME, CHECK_POLL_TIME, numAttempts,
                storePollRunnable);
    }

    /**
     * {@inheritDoc}
     */
    public TestDeviceState getDeviceState() {
        return mDeviceState;
    }

    /**
     * {@inheritDoc}
     */
    public boolean waitForDeviceBootloader(long time) {
        return waitForDeviceState(TestDeviceState.FASTBOOT, time);
    }

    private boolean waitForDeviceState(TestDeviceState state, long time) {
        String deviceSerial = getSerialNumber();
        if (getDeviceState() == state) {
            Log.i(LOG_TAG, String.format("Device %s is already %s", deviceSerial, state));
            return true;
        }
        Log.i(LOG_TAG, String.format("Waiting for device %s to be %s...", deviceSerial, state));
        DeviceStateListener listener = new DeviceStateListener(state);
        addDeviceStateListener(listener);
        synchronized (listener) {
            try {
                listener.wait(time);
            } catch (InterruptedException e) {
                Log.w(LOG_TAG, "wait for device state interrupted");
            }
        }
        removeDeviceStateListener(listener);
        return getDeviceState().equals(state);
    }

    /**
     * @param listener
     */
    private void removeDeviceStateListener(DeviceStateListener listener) {
        synchronized (mStateListeners) {
            mStateListeners.remove(listener);
        }
    }

    /**
     * @param listener
     */
    private void addDeviceStateListener(DeviceStateListener listener) {
        synchronized (mStateListeners) {
            mStateListeners.add(listener);
        }
    }

    /**
     * {@inheritDoc}
     */
    public void setState(TestDeviceState deviceState) {
        mDeviceState = deviceState;
        // create a copy of listeners to prevent holding mStateListeners lock when notifying
        // and to protect from list modification when iterating
        Collection<DeviceStateListener> listenerCopy = new ArrayList<DeviceStateListener>(
                mStateListeners.size());
        synchronized (mStateListeners) {
            listenerCopy.addAll(mStateListeners);
        }
        for (DeviceStateListener listener: listenerCopy) {
            listener.stateChanged(deviceState);
        }
    }

    private static class DeviceStateListener {
        private final TestDeviceState mExpectedState;

        public DeviceStateListener(TestDeviceState expectedState) {
            mExpectedState = expectedState;
        }

        public void stateChanged(TestDeviceState newState) {
            if (mExpectedState.equals(newState)) {
                synchronized (this) {
                    notify();
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public String getSerialNumber() {
        return getIDevice().getSerialNumber();
    }
}
