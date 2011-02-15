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

import com.android.ddmlib.DdmPreferences;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.Log;
import com.android.ddmlib.AndroidDebugBridge.IDeviceChangeListener;
import com.android.ddmlib.Log.LogLevel;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.ConditionPriorityBlockingQueue;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;
import com.android.tradefed.util.ConditionPriorityBlockingQueue.IMatcher;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@inheritDoc}
 */
public class DeviceManager implements IDeviceManager {

    private static final String LOG_TAG = "DeviceManager";

    /** max wait time in ms for fastboot devices command to complete */
    private static final long FASTBOOT_CMD_TIMEOUT = 1 * 60 * 1000;
    /**  time to wait in ms between fastboot devices requests */
    private static final long FASTBOOT_POLL_WAIT_TIME = 5*1000;
    /** time to monitor device adb connection before declaring it available for testing */
    // TODO: 5 seconds is a bit annoyingly long. Try to reduce in future
    private static final int CHECK_WAIT_DEVICE_AVAIL_MS = 5*1000;

    /** a {@link DeviceSelectionOptions} that matches any device */
    private static final DeviceSelectionOptions ANY_DEVICE_OPTIONS = new DeviceSelectionOptions();

    private static DeviceManager sInstance;

    /** A thread-safe map that tracks the devices currently allocated for testing.*/
    private Map<String, IManagedTestDevice> mAllocatedDeviceMap;
    /** A FIFO, thread-safe queue for holding devices visible on adb available for testing */
    private ConditionPriorityBlockingQueue<IDevice> mAvailableDeviceQueue;
    private IAndroidDebugBridge mAdbBridge;
    private final ManagedDeviceListener mManagedDeviceListener;
    private final FastbootMonitor mFastbootMonitor;
    private Map<String, IDeviceStateMonitor> mCheckDeviceMap;

    private boolean mEnableLogcat = true;

    private Set<IFastbootListener> mFastbootListeners;

    private static class DeviceMatcher implements IMatcher<IDevice> {

        private DeviceSelectionOptions mOptions;

        DeviceMatcher(DeviceSelectionOptions options) {
            mOptions = options;
        }

        /**
         * {@inheritDoc}
         */
        public boolean matches(IDevice device) {
            return DeviceSelectionMatcher.matches(device, mOptions);
        }

    }

    /**
     * Package-private constructor, should only be used by this class and its associated unit test.
     * Use {@link #getInstance()} instead.
     */
    DeviceManager() {
        // use Hashtable since it is synchronized
        mAllocatedDeviceMap = new Hashtable<String, IManagedTestDevice>();
        mAvailableDeviceQueue = new ConditionPriorityBlockingQueue<IDevice>();
        mCheckDeviceMap = new Hashtable<String, IDeviceStateMonitor>();
        // TODO: Temporarily increase default timeout as workaround for syncFiles timeouts
        DdmPreferences.setTimeOut(30*1000);
        mAdbBridge = createAdbBridge();
        // assume "adb" is in PATH
        // TODO: make this configurable
        mAdbBridge.init(false /* client support */, "adb");
        for (IDevice device : mAdbBridge.getDevices()) {
            if (device.getState() == IDevice.DeviceState.ONLINE) {
                checkAndAddAvailableDevice(device);
            }
        }
        mManagedDeviceListener = new ManagedDeviceListener();
        mAdbBridge.addDeviceChangeListener(mManagedDeviceListener);
        mFastbootListeners = Collections.synchronizedSet(new HashSet<IFastbootListener>());
        mFastbootMonitor = new FastbootMonitor();
        startFastbootMonitor();
    }

    /**
     * Start fastboot monitoring.
     * <p/>
     * Exposed for unit testing.
     */
    void startFastbootMonitor() {
        mFastbootMonitor.start();
    }

    /**
     * Get the {@link RunUtil} instance to use.
     * <p/>
     * Exposed for unit testing.
     */
    IRunUtil getRunUtil() {
        return RunUtil.getInstance();
    }

    /**
     * Toggle whether allocated devices should capture logcat in background
     */
    public void setEnableLogcat(boolean enableLogcat) {
        mEnableLogcat = enableLogcat;
    }

    /**
     * Asynchronously checks if device is available, and adds to queue
     * @param device
     */
    private void checkAndAddAvailableDevice(final IDevice device) {
        if (mCheckDeviceMap.containsKey(device.getSerialNumber())) {
            // device already being checked, ignore
            Log.d(LOG_TAG, String.format("Already checking new device %s, ignoring",
                    device.getSerialNumber()));
            return;
        }
        final IDeviceStateMonitor monitor = createStateMonitor(device);
        mCheckDeviceMap.put(device.getSerialNumber(), monitor);

        final String threadName = String.format("Check device %s", device.getSerialNumber());
        Thread checkThread = new Thread(threadName) {
            @Override
            public void run() {
                Log.d(LOG_TAG, String.format("checking new device %s responsiveness",
                        device.getSerialNumber()));
                // Ensure the device adb connection is stable before allocating for testing.
                // To do so, wait a small amount to ensure device does not disappear from adb
                boolean isAvail = false;
                if (!monitor.waitForDeviceNotAvailable(CHECK_WAIT_DEVICE_AVAIL_MS)) {
                    if (monitor.getDeviceState().equals(TestDeviceState.ONLINE)) {
                        Log.logAndDisplay(LogLevel.INFO, LOG_TAG, String.format(
                                "Detected new device %s", device.getSerialNumber()));
                        addAvailableDevice(device);
                        isAvail = true;
                    }
                }
                if (!isAvail) {
                    Log.e(LOG_TAG, String.format(
                            "Device %s adb connection is not stable, skip adding to available pool",
                            device.getSerialNumber()));
                }
                mCheckDeviceMap.remove(device.getSerialNumber());
            }
        };
        checkThread.start();
    }

    /**
     * Creates a {@link IDeviceStateMonitor} to use.
     * <p/>
     * Exposed so unit tests can mock
     */
    IDeviceStateMonitor createStateMonitor(IDevice device) {
        return new DeviceStateMonitor(this, device);
    }

    private void addAvailableDevice(IDevice device) {
        mAvailableDeviceQueue.add(device);

    }

    /**
     * Return the {@link IDeviceManager} singleton, creating if necessary.
     */
    public synchronized static IDeviceManager getInstance() {
        if (sInstance == null) {
            sInstance = new DeviceManager();
        }
        return sInstance;
    }

    /**
     * {@inheritDoc}
     */
    public ITestDevice allocateDevice() {
        IDevice allocatedDevice = takeAvailableDevice();
        if (allocatedDevice == null) {
            return null;
        }
        return createAllocatedDevice(allocatedDevice);
    }

    /**
     * Retrieves and removes a IDevice from the available device queue, waiting indefinitely if
     * necessary until an IDevice becomes available.
     *
     * @return the {@link IDevice} or <code>null</code> if interrupted
     */
    private IDevice takeAvailableDevice() {
        try {
            return mAvailableDeviceQueue.take();
        } catch (InterruptedException e) {
            Log.w(LOG_TAG, "interrupted while taking device");
            return null;
        }
    }

    /**
     * {@inheritDoc}
     */
    public ITestDevice allocateDevice(long timeout) {
        IDevice allocatedDevice = pollAvailableDevice(timeout, ANY_DEVICE_OPTIONS);
        if (allocatedDevice == null) {
            return null;
        }
        return createAllocatedDevice(allocatedDevice);
    }

    /**
     * {@inheritDoc}
     */
    public ITestDevice allocateDevice(long timeout, DeviceSelectionOptions options) {
        IDevice allocatedDevice = pollAvailableDevice(timeout, options);
        if (allocatedDevice == null) {
            return null;
        }
        return createAllocatedDevice(allocatedDevice);
    }

    /**
     * Retrieves and removes a IDevice from the available device queue, waiting for timeout if
     * necessary until an IDevice becomes available.
     *
     * @param timeout the number of ms to wait for device
     * @param options the {@link DeviceSelectionOptions} the returned device must meet
     *
     * @return the {@link IDevice} or <code>null</code> if interrupted
     */
    private IDevice pollAvailableDevice(long timeout, DeviceSelectionOptions options) {
        try {
            return mAvailableDeviceQueue.poll(timeout, TimeUnit.MILLISECONDS,
                    new DeviceMatcher(options));
        } catch (InterruptedException e) {
            Log.w(LOG_TAG, "interrupted while polling for device");
            return null;
        }
    }

    private ITestDevice createAllocatedDevice(IDevice allocatedDevice) {
        IManagedTestDevice testDevice =  createTestDevice(allocatedDevice,
                createStateMonitor(allocatedDevice));
        if (mEnableLogcat) {
            testDevice.startLogcat();
        }
        mAllocatedDeviceMap.put(allocatedDevice.getSerialNumber(), testDevice);
        Log.i(LOG_TAG, String.format("Allocated device %s", testDevice.getSerialNumber()));
        return testDevice;
    }

    /**
     * Factory method to create a {@link IManagedTestDevice}.
     * <p/>
     * Exposed so unit tests can mock
     *
     * @param allocatedDevice
     * @param recovery
     * @param monitor
     * @return
     */
    IManagedTestDevice createTestDevice(IDevice allocatedDevice, IDeviceStateMonitor monitor) {
        return new TestDevice(allocatedDevice, monitor);
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
    public void freeDevice(ITestDevice device, FreeDeviceState deviceState) {
        if (device instanceof IManagedTestDevice) {
            final IManagedTestDevice managedDevice = (IManagedTestDevice)device;
            managedDevice.stopLogcat();
        }
        if (mAllocatedDeviceMap.remove(device.getSerialNumber()) == null) {
            Log.e(LOG_TAG, String.format("freeDevice called with unallocated device %s",
                        device.getSerialNumber()));
        } else if (deviceState == FreeDeviceState.UNRESPONSIVE) {
            // TODO: add class flag to control if unresponsive device's are returned to pool
            // TODO: also consider tracking unresponsive events received per device - so a
            // device that is continually unresponsive could be removed from available queue
            addAvailableDevice(device.getIDevice());
        } else if (deviceState == FreeDeviceState.AVAILABLE) {
            addAvailableDevice(device.getIDevice());
        } else if (deviceState == FreeDeviceState.UNAVAILABLE) {
            Log.logAndDisplay(LogLevel.WARN, LOG_TAG, String.format(
                    "Freed device %s is unavailable. Removing from use.",
                    device.getSerialNumber()));
        }
    }

    /**
     * {@inheritDoc}
     */
    public void terminate() {
        mAdbBridge.removeDeviceChangeListener(mManagedDeviceListener);
        mAdbBridge.terminate();
        mFastbootMonitor.terminate();
    }

    /**
     * {@inheritDoc}
     */
    public Collection<String> getAllocatedDevices() {
        Collection<String> allocatedDeviceSerials = new ArrayList<String>(
                mAllocatedDeviceMap.size());
        allocatedDeviceSerials.addAll(mAllocatedDeviceMap.keySet());
        return allocatedDeviceSerials;
    }

    /**
     * {@inheritDoc}
     */
    public Collection<String> getAvailableDevices() {
        Collection<String> availableDeviceSerials = new ArrayList<String>(
                mAvailableDeviceQueue.size());
        synchronized (mAvailableDeviceQueue) {
            for (IDevice device : mAvailableDeviceQueue) {
                availableDeviceSerials.add(device.getSerialNumber());
            }
        }
        return availableDeviceSerials;

    }

    /**
     * {@inheritDoc}
     */
    public Collection<String> getUnavailableDevices() {
        IDevice[] visibleDevices = mAdbBridge.getDevices();
        Collection<String> unavailableSerials = new ArrayList<String>(
                visibleDevices.length);
        Collection<String> availSerials = getAvailableDevices();
        Collection<String> allocatedSerials = getAllocatedDevices();
        for (IDevice device : visibleDevices) {
            if (!availSerials.contains(device.getSerialNumber()) &&
                    !allocatedSerials.contains(device.getSerialNumber())) {
                unavailableSerials.add(device.getSerialNumber());
            }
        }
        return unavailableSerials;
    }

    private class ManagedDeviceListener implements IDeviceChangeListener {

        /**
         * {@inheritDoc}
         */
        public void deviceChanged(IDevice device, int changeMask) {
            IManagedTestDevice testDevice = mAllocatedDeviceMap.get(device.getSerialNumber());
            if ((changeMask & IDevice.CHANGE_STATE) != 0) {
                if (testDevice != null) {
                    TestDeviceState newState = TestDeviceState.getStateByDdms(device.getState());
                    testDevice.setDeviceState(newState);
                } else if (mCheckDeviceMap.containsKey(device.getSerialNumber())) {
                    IDeviceStateMonitor monitor = mCheckDeviceMap.get(device.getSerialNumber());
                    monitor.setState(TestDeviceState.getStateByDdms(device.getState()));
                } else if (!mAvailableDeviceQueue.contains(device) &&
                        device.getState() == IDevice.DeviceState.ONLINE) {
                    checkAndAddAvailableDevice(device);
                }
            }
        }

        /**
         * {@inheritDoc}
         */
        public void deviceConnected(IDevice device) {
            Log.d(LOG_TAG, String.format("Detected device connect %s, id %d",
                    device.getSerialNumber(), device.hashCode()));
            IManagedTestDevice testDevice = mAllocatedDeviceMap.get(device.getSerialNumber());
            if (testDevice == null) {
                if (isValidDeviceSerial(device.getSerialNumber()) &&
                        device.getState() == IDevice.DeviceState.ONLINE) {
                    checkAndAddAvailableDevice(device);
                } else if (mCheckDeviceMap.containsKey(device.getSerialNumber())) {
                    IDeviceStateMonitor monitor = mCheckDeviceMap.get(device.getSerialNumber());
                    monitor.setState(TestDeviceState.getStateByDdms(device.getState()));
                }
            } else {
                // this device is known already. However DDMS will allocate a new IDevice, so need
                // to update the TestDevice record with the new device
                Log.d(LOG_TAG, String.format("Updating IDevice for device %s",
                        device.getSerialNumber()));
                testDevice.setIDevice(device);
                TestDeviceState newState = TestDeviceState.getStateByDdms(device.getState());
                testDevice.setDeviceState(newState);
            }
        }

        private boolean isValidDeviceSerial(String serial) {
            return serial.length() > 1 && !serial.contains("?");
        }

        /**
         * {@inheritDoc}
         */
        public void deviceDisconnected(IDevice disconnectedDevice) {
            if (mAvailableDeviceQueue.remove(disconnectedDevice)) {
                Log.i(LOG_TAG, String.format("Removed disconnected device %s from available queue",
                        disconnectedDevice.getSerialNumber()));
            }
            IManagedTestDevice testDevice = mAllocatedDeviceMap.get(
                    disconnectedDevice.getSerialNumber());
            if (testDevice != null) {
                testDevice.setDeviceState(TestDeviceState.NOT_AVAILABLE);
            } else if (mCheckDeviceMap.containsKey(disconnectedDevice.getSerialNumber())) {
                IDeviceStateMonitor monitor = mCheckDeviceMap.get(
                        disconnectedDevice.getSerialNumber());
                monitor.setState(TestDeviceState.NOT_AVAILABLE);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public void addFastbootListener(IFastbootListener listener) {
        mFastbootListeners.add(listener);
    }

    /**
     * {@inheritDoc}
     */
    public void removeFastbootListener(IFastbootListener listener) {
        mFastbootListeners.remove(listener);
    }

    private class FastbootMonitor extends Thread {

        private boolean mQuit = false;

        FastbootMonitor() {
            super("FastbootMonitor");
        }

        public void terminate() {
            mQuit = true;
            interrupt();
        }

        @Override
        public void run() {
            while (!mQuit) {
                // only poll fastboot devices if there are listeners, as polling it
                // indiscriminiately can cause fastboot commands to hang
                if (!mFastbootListeners.isEmpty()) {
                    CommandResult fastbootResult = getRunUtil().runTimedCmd(FASTBOOT_CMD_TIMEOUT,
                            "fastboot", "devices");
                    if (fastbootResult.getStatus() == CommandStatus.SUCCESS) {
                        Log.v(LOG_TAG, String.format("fastboot devices returned %s",
                                fastbootResult.getStdout()));
                        Set<String> serials = DeviceManager.getDevicesOnFastboot(
                                fastbootResult.getStdout());
                        for (String serial: serials) {
                            IManagedTestDevice testDevice = mAllocatedDeviceMap.get(serial);
                            if (testDevice != null &&
                                    !testDevice.getDeviceState().equals(TestDeviceState.FASTBOOT)) {
                                testDevice.setDeviceState(TestDeviceState.FASTBOOT);
                            }
                        }
                        // now update devices that are no longer on fastboot
                        synchronized (mAllocatedDeviceMap) {
                            for (IManagedTestDevice testDevice : mAllocatedDeviceMap.values()) {
                                if (!serials.contains(testDevice.getSerialNumber())
                                        && testDevice.getDeviceState().equals(
                                                TestDeviceState.FASTBOOT)) {
                                    testDevice.setDeviceState(TestDeviceState.NOT_AVAILABLE);
                                }
                            }
                        }
                    }
                    // create a copy of listeners for notification to prevent deadlocks
                    Collection<IFastbootListener> listenersCopy = new ArrayList<IFastbootListener>(
                            mFastbootListeners.size());
                    listenersCopy.addAll(mFastbootListeners);
                    for (IFastbootListener listener : listenersCopy) {
                        listener.stateUpdated();
                    }
                }
                getRunUtil().sleep(FASTBOOT_POLL_WAIT_TIME);
            }
        }
    }

    static Set<String> getDevicesOnFastboot(String fastbootOutput) {
        Set<String> serials = new HashSet<String>();
        Pattern fastbootPattern = Pattern.compile("([\\w\\d]+)\\s+fastboot\\s*");
        Matcher fastbootMatcher = fastbootPattern.matcher(fastbootOutput);
        while (fastbootMatcher.find()) {
            serials.add(fastbootMatcher.group(1));
        }
        return serials;
    }
}
