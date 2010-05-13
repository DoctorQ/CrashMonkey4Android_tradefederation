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
 * Provides an reliable and slightly higher level API to a ddmlib {@link IDevice}.
 * <p/>
 * Retries device commands for a configurable amount, and provides a device recovery
 * interface for devices which are unresponsive.
 */
public interface ITestDevice {

    /**
     * Returns a reference to the associated ddmlib {@link IDevice}.
     *
     * @return the {@link IDevice}
     */
    public IDevice getIDevice();

    /**
     * Convenience method to get serial number of this device.
     *
     * @return the {@link String} serial number
     */
    public String getSerialNumber();

    /**
     * Executes the given adb shell command.
     *
     * @param command the adb shell command to run
     * @param receiver the {@link IShellOutputReceiver} to direct shell output to.
     * @throws DeviceNotAvailableException if connection with device is lost and cannot be
     * recovered.
     */
    public void executeShellCommand(String command, IShellOutputReceiver receiver)
        throws DeviceNotAvailableException;

    /**
     * Helper method which executes a adb shell command and returns output as a {@link String}.
     *
     * @param command the adb shell command to run
     * @return the shell output
     * @throws DeviceNotAvailableException if connection with device is lost and cannot be
     * recovered.
     */
    public String executeShellCommand(String command) throws DeviceNotAvailableException;

    /**
     * Helper method which executes a adb command as a system command.
     * <p/>
     * {@link #executeShellCommand(String)} should be used instead wherever possible, as that
     * method provides better failure detection and performance.
     *
     * @param commandArgs the adb command and arguments to run
     * @return the stdout from command. <code>null</code> if command failed to execute.
     * @throws DeviceNotAvailableException if connection with device is lost and cannot be
     * recovered.
     */
    public String executeAdbCommand(String... commandArgs) throws DeviceNotAvailableException;

    /**
     * Helper method which executes a fastboot command as a system command.
     * <p/>
     * Assumes device is already in fastboot mode
     *
     * @param commandArgs the fastboot command and arguments to run
     * @return <code>true</code>
     * @throws DeviceNotAvailableException if connection with device is lost and cannot be
     * recovered.
     */
    public boolean executeFastbootCommand(String... commandArgs) throws DeviceNotAvailableException;

    /**
     * Runs instrumentation tests, and provides device recovery.
     * <p/>
     * If connection with device is lost before test run completes, and recovery succeeds, this
     * method will simply return control to caller. eg test command will not be rerun.
     * <p/>
     * If connection with device is lost before test run completes, and recovery fails, all
     * listeners will be informed of testRunFailed and DeviceNotAvailableException will be thrown.
     *
     * @param runner the {@IRemoteAndroidTestRunner} which runs the tests
     * @param listeners the test result listeners
     * @throws DeviceNotAvailableException if connection with device is lost and cannot be
     * recovered.
     */
    public void runInstrumentationTests(IRemoteAndroidTestRunner runner,
            Collection<ITestRunListener> listeners) throws DeviceNotAvailableException;

    /**
     * Retrieves a file off device.
     *
     * @param remoteFilePath the absolute path to file on device.
     * @param localFile the local file to store contents in. If non-empty, contents will be
     * replaced.
     * @return <code>true</code> if file was retrieved successfully. <code>false</code> otherwise.
     * @throws DeviceNotAvailableException if connection with device is lost and cannot be
     * recovered.
     */
    public boolean pullFile(String remoteFilePath, File localFile)
            throws DeviceNotAvailableException;

    /**
     * Push a file to device
     *
     * @param localFile the local file to push
     * @param deviceFilePath the remote destination absolute file path
     * @return <code>true</code> if file was pushed successfully. <code>false</code> otherwise.
     * @throws DeviceNotAvailableException if connection with device is lost and cannot be
     * recovered.
     */
    public boolean pushFile(File localFile, String deviceFilePath)
            throws DeviceNotAvailableException;

    /**
     * Helper method to determine if file on device exists.
     *
     * @param deviceFilePath the absolute path of file on device to check
     * @return <code>true</code> if file exists, <code>false</code> otherwise.
     * @throws DeviceNotAvailableException if connection with device is lost and cannot be
     * recovered.
     */
    public boolean doesFileExist(String deviceFilePath) throws DeviceNotAvailableException;

    /**
     * Grabs a snapshot stream of the logcat data.
     */
    public InputStream getLogcat();

    /**
     * Reboots the device into bootloader mode.
     *
     * @throws DeviceNotAvailableException if connection with device is lost and cannot be
     * recovered.
     */
    public void rebootIntoBootloader() throws DeviceNotAvailableException;

    /**
     * Reboots the device into adb mode.
     *
     * @throws DeviceNotAvailableException if connection with device is lost and cannot be
     * recovered.
     */
    public void reboot() throws DeviceNotAvailableException;

    /**
     * Turns on adb root .
     *
     * @return <code>true</code> if successful.
     * @throws DeviceNotAvailableException if connection with device is lost and cannot be
     * recovered.
     */
    public boolean enableAdbRoot() throws DeviceNotAvailableException;

    /**
     * Waits for the device to be responsive and available for testing.
     *
     * @param waitTime the time in ms to wait
     * @throws DeviceNotAvailableException if connection with device is lost and cannot be
     * recovered.
     */
    public void waitForDeviceAvailable(final long waitTime) throws DeviceNotAvailableException;

    /**
     * Waits for the device to be responsive and available for testing. Uses default boot timeout.
     *
     * @throws DeviceNotAvailableException if connection with device is lost and cannot be
     * recovered.
     */
    public void waitForDeviceAvailable() throws DeviceNotAvailableException;
}
