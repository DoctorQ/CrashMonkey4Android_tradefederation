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

/**
 * A ITestDevice that supports background logcat capture.
 */
interface ILogTestDevice extends ITestDevice {

    /**
     * Start capturing logcat output from device in the background.
     * <p/>
     * Will have no effect if logcat output is already being captured.
     * Data can be later retrieved via getLogcat.
     * <p/>
     * When the device is no longer in use, {@link #stopLogcat()} must be called.
     */
    public void startLogcat();

    /**
     * Stop capturing logcat output from device, and discard currently saved logcat data.
     * <p/>
     * Will have no effect if logcat output is not being captured.
     */
    public void stopLogcat();
}
