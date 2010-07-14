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

/**
 * Interface for managing the set of available devices for testing.
 */
public interface IDeviceManager {

    public enum FreeDeviceState {
        AVAILABLE, UNAVAILABLE, UNRESPONSIVE;
    }

    /**
     * Request a device for testing, waiting indefinitely until one becomes available.
     *
     * @param recovery the {@link IDeviceRecovery} to use for device
     * @return a {@link ITestDevice} for testing, or <code>null</code> if interrupted
     */
    public ITestDevice allocateDevice(IDeviceRecovery recovery);

    /**
     * Request a device for testing, waiting for timeout ms until one becomes available.
     *
     * @param recovery the {@link IDeviceRecovery} to use for device
     * @param timeout
     * @return a {@link ITestDevice} for testing, or <code>null</code> if timeout expired before
     * one became available
     */
    public ITestDevice allocateDevice(IDeviceRecovery recovery, long timeout);

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
     * Terminates the ddm library. This must be called upon application termination.
     *
     * @see AndroidDebugBridge#terminate()
     */
    public void terminate();

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
    public void addFastbootListener(Object listener);

    /**
     * Informs the manager that a listener is no longer interested in fastboot state changes.
     * @param listener
     */
    public void removeFastbootListener(Object listener);
}
