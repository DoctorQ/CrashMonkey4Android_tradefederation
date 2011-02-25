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

import java.util.Collection;

/**
 * Interface for managing the set of available devices for testing.
 */
public interface IDeviceManager {

    public enum FreeDeviceState {
        AVAILABLE, UNAVAILABLE, UNRESPONSIVE;
    }

    /**
     * A listener for fastboot state changes.
     */
    public static interface IFastbootListener {
        /**
         * Callback when fastboot state has been updated for all devices.
         */
        public void stateUpdated();
    }

    /**
     * Initialize the device manager. This must be called once and only once before any other
     * methods are called.
     */
    public void init();

    /**
     * Initialize the device manager with a device filter. This filter can be used to instruct
     * the DeviceManager to ignore certain connected devices.
     */
    public void init(IDeviceSelectionOptions globalDeviceFilter);

    /**
     * Request a device for testing, waiting indefinitely until one becomes available.
     *
     * @return a {@link ITestDevice} for testing, or <code>null</code> if interrupted
     */
    public ITestDevice allocateDevice();

    /**
     * Request a device for testing, waiting for timeout ms until one becomes available.
     *
     * @param timeout max time in ms to wait for a device to become available.
     * @return a {@link ITestDevice} for testing, or <code>null</code> if timeout expired before one
     *         became available
     */
    public ITestDevice allocateDevice(long timeout);

    /**
     * Request a device for testing that meets certain criteria.
     *
     * @param timeout max time in ms to wait for a device to become available.
     * @param options the {@link IDeviceSelectionOptions} the device should meet.
     * @return a {@link ITestDevice} for testing, or <code>null</code> if timeout expired before one
     *         became available
     */
    public ITestDevice allocateDevice(long timeout, IDeviceSelectionOptions options);

    /**
     * Return a device to the pool
     * <p/>
     * Attempts to return a device that hasn't been previously allocated will be ignored.
     *
     * @param device the {@link ITestDevice} to free
     * @param state the {@link FreeDeviceState}. Used to control if device is returned to available
     *            device pool.
     */
    public void freeDevice(ITestDevice device, FreeDeviceState state);

    /**
     * Stops device monitoring services, and terminates the ddm library.
     * <p/>
     * This must be called upon application termination.
     *
     * @see AndroidDebugBridge#terminate()
     */
    public void terminate();

    /**
     * Like {@link #terminate()}, but attempts to forcefully shut down adb as well.
     */
    public void terminateHard();

    /**
     * Diagnostic method that returns a list of the devices available for allocation.
     *
     * @return a {@link Collection} of device serials
     */
    public Collection<String> getAvailableDevices();

    /**
     * Diagnostic method that returns a list of the devices currently allocated for testing.
     *
     * @return a {@link Collection} of device serials
     */
    public Collection<String> getAllocatedDevices();

    /**
     * Diagnostic method that returns a list of the devices currently visible via adb, but not
     * deemed available for allocation.
     *
     * @return a {@link Collection} of device serials
     */
    public Collection<String> getUnavailableDevices();

    /**
     * Informs the manager that a listener is interested in fastboot state changes.
     * <p/>
     * Currently a {@link IDeviceManager} will only monitor devices in fastboot if there are one or
     * more active listeners.
     * <p/>
     * TODO: this is a bit of a hack - find a better solution
     *
     * @param listener
     */
    public void addFastbootListener(IFastbootListener listener);

    /**
     * Informs the manager that a listener is no longer interested in fastboot state changes.
     * @param listener
     */
    public void removeFastbootListener(IFastbootListener listener);
}
