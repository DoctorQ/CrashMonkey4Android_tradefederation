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

/**
 * Provides facilities for monitoring the state of a {@link IDevice}.
 */
interface IDeviceStateMonitor {

    /**
     * Waits for device to be online.
     * <p/>
     * Note: this method will return once device is visible via DDMS. It does not guarantee that the
     * device is actually responsive to adb commands - use {@link #waitForDeviceAvailable()}
     * instead.
     *
     * @param time the maximum time in ms to wait
     *
     * @return <code>true</code> if device becomes online before time expires
     */
    public boolean waitForDeviceOnline(long time);

    /**
     * Waits for device to be online using standard boot timeout.
     * <p/>
     * Note: this method will return once device is visible via DDMS. It does not guarantee that the
     * device is actually responsive to adb commands - use {@link #waitForDeviceAvailable()}
     * instead.
     *
     * @return <code>true</code> if device becomes online before time expires
     */
    public boolean waitForDeviceOnline();

    /**
     * Waits for the device to be responsive and available for testing.
     *
     * @param waitTime the time in ms to wait
     * @return <code>true</code> if device becomes responsive before time expires
     */
    public boolean waitForDeviceAvailable(final long waitTime);

    /**
     * Waits for the device to be responsive and available for testing.
     * <p/>
     * Equivalent to {@link #waitForDeviceAvailable(long)}, but uses default device
     * boot timeout.
     *
     * @return <code>true</code> if device becomes responsive before time expires
     */
    public boolean waitForDeviceAvailable();

    /**
     * Waits for the device to be in bootloader.
     *
     * @param waitTime the maximum time in ms to wait
     *
     * @return <code>true</code> if device is in bootloader before time expires
     */
    public boolean waitForDeviceBootloader(long waitTime);

    /**
     * Waits for the device to be not available
     *
     * @param waitTime the maximum time in ms to wait
     *
     * @return <code>true</code> if device becomes unavailable
     */
    public boolean waitForDeviceNotAvailable(long waitTime);


    /**
     * Gets the device state.
     *
     * @return the {@link TestDeviceState} of device
     */
    public TestDeviceState getDeviceState();
}
