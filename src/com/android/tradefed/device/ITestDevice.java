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
import com.android.ddmlib.IShellOutputReceiver;

/**
 *  Provides an reliable and slightly higher level API to a ddmlib {@link IDevice}.
 *
 *  Retries device commands for a configurable amount, and provides an optional device recovery
 *  interface for devices which have gone offline.
 */
public interface ITestDevice {

    /**
     * Executes the given adb shell command.
     *
     * @param command the adb shell command to run
     * @param receiver the {@link IShellOutputReceiver} to direct shell output to.
     * @throws DeviceNotAvailableException
     */

    public void executeShellCommand(String command, IShellOutputReceiver receiver)
        throws DeviceNotAvailableException;

    /**
     * Helper method which executes a adb shell command and returns output as a {@link String}.
     *
     * @param command the adb shell command to run
     * @return the shell output
     * @throws DeviceNotAvailableException
     */
    public String executeShellCommand(String command) throws DeviceNotAvailableException;

    /**
     * Returns a reference to the associated ddmlib {@link IDevice}.
     *
     * @return the {@link IDevice}
     */
    public IDevice getIDevice();

}