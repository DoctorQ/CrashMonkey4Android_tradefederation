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
import com.android.ddmlib.IDevice.DeviceState;
import com.android.tradefed.util.RunUtil;
import com.android.tradefed.util.RunUtil.IRunnableResult;

import java.io.IOException;
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
    private final IDeviceRecovery mRecovery;

    /** The default maximum time in ms to wait for a device to be connected. */
    private static final int DEFAULT_MAX_WAIT_DEVICE_TIME = 10 * 1000;
    /** The default time in ms to wait for a device command to complete. */
    private static final int DEFAULT_CMD_TIMEOUT = 2 * 60 * 1000;

    /** The  time in ms to wait for a device to boot. */
    // TODO: make this configurable - or auto-scale according to device performance ?
    private static final int DEFAULT_BOOT_TIMEOUT = 4 * 60 * 1000;

    /** the time in ms to wait between 'poll for package manager' attempts */
    private static final int CHECK_PM_POLL_TIME = 5 * 1000;
    /** the maximum operation time in ms for a 'poll for package manager' command */
    private static final long MAX_PM_POLL_TIME = 30 * 1000;

    private static final int FASTBOOT_POLL_ATTEMPTS = 5;
    /** The  time in ms to wait for a device to boot. */
    private static final int FASTBOOT_TIMEOUT = 1 * 60 * 1000;

    /** the ratio of time to wait for device to be online vs responsive in
     * {@link DeviceManager#waitForDeviceAvailable(ITestDevice, long)}.
     */
    private static final float WAIT_DEVICE_RATIO = 0.4f;

    /**
     * A more fully featured representation of device state than {@link DeviceState}.
     * <p/>
     * Logically this should extend  {@link DeviceState} to just add the FASTBOOT and NOT_AVAILABLE
     * states, but extending enums is not allowed.
     */
    static enum TestDeviceState {
        FASTBOOT,
        ONLINE,
        OFFLINE,
        NOT_AVAILABLE;

        // TODO: add recovery state and means to detect it via ddms

        /**
         * Converts from {@link TestDeviceState} to {@link DeviceState}
         * @return the {@link DeviceState} or <code>null</code>
         */
        DeviceState getDdmsState() {
            switch (this) {
                case ONLINE:
                    return DeviceState.ONLINE;
                case OFFLINE:
                    return DeviceState.OFFLINE;
                default:
                    return null;
            }
        }
    }

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
        ILogTestDevice testDevice =  new TestDevice(allocatedDevice, mRecovery);
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
        waitForDeviceState(testDevice, TestDeviceState.ONLINE, time);
    }

    TestDeviceState getDeviceState(ITestDevice testDevice) {
        if (testDevice.getIDevice().isOnline() && isDeviceOnAdb(testDevice)) {
            return TestDeviceState.ONLINE;
        } else if (testDevice.getIDevice().isOffline() && isDeviceOnAdb(testDevice)) {
            return TestDeviceState.OFFLINE;
        } else if (isDeviceOnFastboot(testDevice)) {
            return TestDeviceState.FASTBOOT;
        } else {
            return TestDeviceState.NOT_AVAILABLE;
        }
    }

    private boolean isDeviceOnAdb(ITestDevice testDevice) {
        // if device has just disappeared from adb entirely, it may still be marked as online
        // ensure its active on adb bridge
        for (IDevice device : mAdbBridge.getDevices()) {
            if (device.getSerialNumber().equals(testDevice.getSerialNumber())) {
                return true;
            }
        }
        return false;
    }

    private boolean isDeviceOnFastboot(ITestDevice testDevice) {
        String fastbootOut = RunUtil.runTimedCmd(DEFAULT_CMD_TIMEOUT, "fastboot", "devices");
        Log.d(LOG_TAG, String.format("fastboot devices returned %s", fastbootOut));
        return fastbootOut != null && fastbootOut.contains(testDevice.getSerialNumber());
    }

    /**
     * {@inheritDoc}
     */
    public void waitForDeviceBootloader(ITestDevice testDevice, long time)
            throws DeviceNotAvailableException {
        long pollTime = time / FASTBOOT_POLL_ATTEMPTS;
        for (int i=0; i < FASTBOOT_POLL_ATTEMPTS; i++) {
            if (isDeviceOnFastboot(testDevice)) {
                return;
            }
            try {
                Thread.sleep(pollTime);
            } catch (InterruptedException e) {
                // ignore
            }
        }
        throw new DeviceNotAvailableException(String.format("Could not find  device %s in fastboot",
                testDevice.getSerialNumber()));
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

    private void waitForDeviceState(ITestDevice testDevice, TestDeviceState state, long time)
            throws DeviceNotAvailableException {
        String deviceSerial = testDevice.getSerialNumber();
        if (getDeviceState(testDevice) == state) {
            Log.i(LOG_TAG, String.format("Device %s is already %s", deviceSerial, state));
            return;
        }
        Log.i(LOG_TAG, String.format("Waiting for device %s to be %s...", deviceSerial, state));
        AdbDeviceListener listener = new AdbDeviceListener(deviceSerial, state);
        mAdbBridge.addDeviceChangeListener(listener);
        IDevice device = listener.waitForDevice(time);
        mAdbBridge.removeDeviceChangeListener(listener);
        if (device == null) {
            throw new DeviceNotAvailableException(String.format(
                    "Device %s could not be found in state %s", deviceSerial, state));
        } else {
            Log.i(LOG_TAG, String.format("Found device %s %s", device.getSerialNumber(), state));
        }
    }

    /**
     * {@inheritDoc}
     */
    public void rebootIntoBootloader(ITestDevice device) throws DeviceNotAvailableException {
        if (TestDeviceState.FASTBOOT == getDeviceState(device)) {
            Log.i(LOG_TAG, String.format("device %s already in fastboot. Rebooting anyway",
                    device.getSerialNumber()));
            device.executeFastbootCommand("reboot", "bootloader");
        } else {
            Log.i(LOG_TAG, String.format("Booting device %s into bootloader",
                    device.getSerialNumber()));
            device.executeAdbCommand("reboot", "bootloader");
        }
        waitForDeviceBootloader(device, FASTBOOT_TIMEOUT);
        // TODO: check for fastboot responsiveness ?
    }

    /**
     * {@inheritDoc}
     */
    public void reboot(ITestDevice device) throws DeviceNotAvailableException {
        if (TestDeviceState.FASTBOOT == getDeviceState(device)) {
            Log.i(LOG_TAG, String.format("device %s in fastboot. Rebooting to userspace.",
                    device.getSerialNumber()));
            device.executeFastbootCommand("reboot");
        } else {
            Log.i(LOG_TAG, String.format("Rebooting device %s", device.getSerialNumber()));
            device.executeAdbCommand("reboot");
            // TODO: a bit of a race condition here. Would be better to start a device listener
            // before the reboot
            try {
                waitForDeviceState(device, TestDeviceState.NOT_AVAILABLE, 20 * 1000);
            } catch (DeviceNotAvailableException e) {
                // above check is flaky, ignore exception till better solution is found
                Log.w(LOG_TAG, String.format(
                        "Did not detect device %s becoming unavailable after reboot",
                        device.getSerialNumber()));
            }
        }
        waitForDeviceAvailable(device);
    }

    /**
     * {@inheritDoc}
     */
    public void waitForDeviceAvailable(final ITestDevice device, final long waitTime)
            throws DeviceNotAvailableException {
        waitForDevice(device, (long)(waitTime*WAIT_DEVICE_RATIO));
        waitForPmResponsive(device, (long)(waitTime*(1-WAIT_DEVICE_RATIO)));
    }

    /**
     * {@inheritDoc}
     */
    public void waitForDeviceAvailable(final ITestDevice device)
        throws DeviceNotAvailableException {
        waitForDeviceAvailable(device, DEFAULT_BOOT_TIMEOUT);
    }

    /**
     * Waits for the device package manager to be responsive.
     *
     * @param device
     * @param waitTime
     * @throws DeviceNotAvailableException
     */
    void waitForPmResponsive(final ITestDevice device, final long waitTime)
            throws DeviceNotAvailableException {
        Log.i(LOG_TAG, String.format("Waiting for device %s package manager",
                device.getSerialNumber()));
        IRunnableResult pmPollRunnable = new IRunnableResult() {
            public boolean run() {
                final String cmd = "pm path android";
                try {
                    // TODO move collecting output command to IDevice
                    CollectingOutputReceiver receiver = new CollectingOutputReceiver();
                    // assume the 'adb shell pm path android' command will always
                    // return 'package: something' in the success case
                    // intentionally calling IDevice directly to avoid an infinite loop of
                    // recovery logic
                    device.getIDevice().executeShellCommand(cmd, receiver);
                    String output = receiver.getOutput();
                    Log.d(LOG_TAG, String.format("%s returned %s", cmd, output));
                    return output.contains("package:");
                } catch (IOException e) {
                    Log.i(LOG_TAG, String.format("%s failed: %s", cmd, e.getMessage()));
                    return false;
                }
            }
        };
        int numAttempts = (int)(waitTime / CHECK_PM_POLL_TIME);
        if(!RunUtil.runTimedRetry(MAX_PM_POLL_TIME, CHECK_PM_POLL_TIME, numAttempts,
                pmPollRunnable)) {
            Log.w(LOG_TAG, String.format("Device %s package manager is not responding.",
            device.getSerialNumber()));
            throw new DeviceNotAvailableException();
        }
    }

    /**
     * Listens to DDMS for an Android devices.
     */
    private static class AdbDeviceListener implements IDeviceChangeListener {
        private IDevice mDevice;
        private final String mSerial;
        private final TestDeviceState mExpectedState;

        public AdbDeviceListener(String serial, TestDeviceState expectedState) {
            mSerial = serial;
            mExpectedState = expectedState;
        }

        public void deviceChanged(IDevice device, int changeMask) {
            if ((changeMask & IDevice.CHANGE_STATE) != 0) {
               if (mExpectedState.getDdmsState() == device.getState()) {
                   setDevice(device);
               }
            }
        }

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

        public void deviceDisconnected(IDevice device) {
            if (mExpectedState == TestDeviceState.NOT_AVAILABLE &&
                    device.getSerialNumber().equals(mSerial)) {
                setDevice(device);
            }
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
