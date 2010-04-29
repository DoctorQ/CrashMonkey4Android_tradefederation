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
import com.android.ddmlib.testrunner.IRemoteAndroidTestRunner;
import com.android.ddmlib.testrunner.ITestRunListener;

import java.io.File;
import java.io.InputStream;
import java.util.Collection;

/**
 * Empty implementation of {@link ITestDevice}.
 * <p/>
 * Needed in order to handle the EasyMock andDelegateTo operation.
 */
public class StubTestDevice implements ILogTestDevice {

    public void executeShellCommand(String command, IShellOutputReceiver receiver)
            throws DeviceNotAvailableException {
        // ignore
    }

    public String executeShellCommand(String command) throws DeviceNotAvailableException {
        // ignore
        return null;
    }

    public IDevice getIDevice() {
         // ignore
        return null;
    }

    public String getSerialNumber() {
        // ignore
        return null;
    }

    public void runInstrumentationTests(IRemoteAndroidTestRunner runner,
            Collection<ITestRunListener> listeners) throws DeviceNotAvailableException {
        // ignore
    }

    public boolean pullFile(String remoteFilePath, File localFile)
            throws DeviceNotAvailableException {
        return false;
    }

    public boolean pushFile(File localFile, String deviceFilePath)
            throws DeviceNotAvailableException {
        return false;
    }

    public boolean doesFileExist(String deviceFilePath) throws DeviceNotAvailableException {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    public InputStream getLogcat() {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    public void startLogcat() {
    }

    /**
     * {@inheritDoc}
     */
    public void stopLogcat() {
    }

    /**
     * {@inheritDoc}
     */
    public boolean executeAdbCommand(String... commandArgs) throws DeviceNotAvailableException {
        // ignore
        return true;
    }

    /**
     * {@inheritDoc}
     */
    public boolean executeFastbootCommand(String... commandArgs) throws DeviceNotAvailableException {
        // ignore
        return true;
    }
}
