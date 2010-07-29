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
import com.android.tradefed.util.CommandResult;

import java.io.File;
import java.io.InputStream;
import java.util.Collection;

/**
 * Empty implementation of {@link ITestDevice}.
 * <p/>
 * Needed in order to handle the EasyMock andDelegateTo operation.
 */
public class StubTestDevice implements IManagedTestDevice {

    /**
     * {@inheritDoc}
     */
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

    public void runInstrumentationTests(IRemoteAndroidTestRunner runner,
            ITestRunListener... listeners) throws DeviceNotAvailableException {
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
    public String executeAdbCommand(String... commandArgs) throws DeviceNotAvailableException {
        // ignore
        return "";
    }

    /**
     * {@inheritDoc}
     */
    public CommandResult executeFastbootCommand(String... commandArgs)
            throws DeviceNotAvailableException {
        // ignore
        return new CommandResult();
    }

    /**
     * {@inheritDoc}
     */
    public CommandResult executeLongFastbootCommand(String... commandArgs)
            throws DeviceNotAvailableException {
        // ignore
        return new CommandResult();
    }

    /**
     * {@inheritDoc}
     */
    public boolean enableAdbRoot() throws DeviceNotAvailableException {
        // ignore
        return true;
    }

    /**
     * {@inheritDoc}
     */
    public void postBootSetup() throws DeviceNotAvailableException {
        // ignore
    }

    /**
     * {@inheritDoc}
     */
    public void reboot() throws DeviceNotAvailableException {
        // ignore

    }

    /**
     * {@inheritDoc}
     */
    public void rebootIntoBootloader() throws DeviceNotAvailableException {
        // ignore

    }

    /**
     * {@inheritDoc}
     */
    public void waitForDevice(long time) throws DeviceNotAvailableException {
        // ignore

    }

    /**
     * {@inheritDoc}
     */
    public void waitForDeviceAvailable(long waitTime) throws DeviceNotAvailableException {
        // ignore

    }

    /**
     * {@inheritDoc}
     */
    public void waitForDeviceAvailable() throws DeviceNotAvailableException {
        // ignore
    }

    /**
     * {@inheritDoc}
     */
    public void waitForDeviceOnline(long waitTime) throws DeviceNotAvailableException {
        // ignore
    }

    /**
     * {@inheritDoc}
     */
    public void waitForDeviceOnline() throws DeviceNotAvailableException {
        // ignore
    }

    /**
     * {@inheritDoc}
     */
    public long getExternalStoreFreeSpace() throws DeviceNotAvailableException {
        return 0;
    }

    /**
     * {@inheritDoc}
     */
    public boolean syncFiles(File localFileDir, String deviceFilePath)
            throws DeviceNotAvailableException {
        // ignore
        return false;
    }

    /**
     * {@inheritDoc}
     */
    public String getProductType() {
        // ignore
        return "";
    }

    /**
     * {@inheritDoc}
     */
    public void setIDevice(IDevice device) {
        // ignore
    }

    /**
     * {@inheritDoc}
     */
    public void setDeviceState(TestDeviceState deviceState) {
        // ignore
    }

    /**
     * {@inheritDoc}
     */
    public TestDeviceState getDeviceState() {
        return null;
    }

    public boolean connectToWifiNetwork(String wifiSsid) throws DeviceNotAvailableException {
        // ignore
        return false;
    }

    /**
     * {@inheritDoc}
     */
    public boolean disconnectFromWifi() throws DeviceNotAvailableException {
        // ignore
        return false;
    }

    /**
     * {@inheritDoc}
     */
    public boolean clearErrorDialogs() throws DeviceNotAvailableException {
        // ignore
        return false;
    }

    public void preBootSetup() {
       // ignore
    }

    /**
     * {@inheritDoc}
     */
    public boolean waitForDeviceNotAvailable(long waitTime) {
        // ignore
        return false;
    }

    /**
     * {@inheritDoc}
     */
    public String installPackage(File packageFile, boolean reinstall)
            throws DeviceNotAvailableException {
        // ignore
        return null;
    }

    /**
     * {@inheritDoc}
     */
    public String uninstallPackage(String packageName) throws DeviceNotAvailableException {
        // ignore
        return null;
    }

    /**
     * {@inheritDoc}
     */
    public String getMountPoint(String mountName) {
        return null;
    }
}
