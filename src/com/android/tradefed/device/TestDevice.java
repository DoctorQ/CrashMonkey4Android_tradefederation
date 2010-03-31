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
import com.android.ddmlib.Log;
import com.android.ddmlib.SyncService;
import com.android.ddmlib.SyncService.SyncResult;
import com.android.ddmlib.testrunner.IRemoteAndroidTestRunner;
import com.android.ddmlib.testrunner.ITestRunListener;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

/**
 * Default implementation of a {@link ITestDevice}
 */
class TestDevice implements ITestDevice {

    private static final String LOG_TAG = "TestDevice";
    /** the default number of command retry attempts to perform */
    private static final int MAX_RETRY_ATTEMPTS = 3;

    private final IDevice mIDevice;
    private final IDeviceRecovery mRecovery;

    /**
     * Interface for a generic device communication attempt.
     */
    private static interface IDeviceAction {

        /**
         * Perform action that requires device communication
         * @return <code>true</code> if command succeeded. <code>false</code> otherwise.
         * @throws IOException if device communication failed
         */
        boolean doAction() throws IOException;
    }

    /**
     * Creates a {@link TestDevice}.
     *
     * @param device the associated {@link IDevice}
     * @param recovery the {@link IDeviceRecovery} mechanism to use
     */
    TestDevice(IDevice device, IDeviceRecovery recovery) {
        mIDevice = device;
        mRecovery = recovery;
    }

    /**
     * {@inheritDoc}
     */
    public IDevice getIDevice() {
        return mIDevice;
    }

    /**
     * {@inheritDoc}
     */
    public String getSerialNumber() {
        return getIDevice().getSerialNumber();
    }

    /**
     * {@inheritDoc}
     */
    public void executeShellCommand(final String command, final IShellOutputReceiver receiver)
            throws DeviceNotAvailableException {
        IDeviceAction action = new IDeviceAction() {
            public boolean doAction() throws IOException {
                getIDevice().executeShellCommand(command, receiver);
                return true;
            }
        };
        performDeviceAction(String.format("shell %s", command), action, MAX_RETRY_ATTEMPTS);
    }

    /**
     * {@inheritDoc}
     */
    public String executeShellCommand(String command) throws DeviceNotAvailableException {
        CollectingOutputReceiver receiver = new CollectingOutputReceiver();
        executeShellCommand(command, receiver);
        return receiver.getOutput();
    }

    /**
     * {@inheritDoc}
     */
    public void runInstrumentationTests(IRemoteAndroidTestRunner runner,
            Collection<ITestRunListener> listeners) throws DeviceNotAvailableException {
        try {
            runner.run(listeners);
        } catch (IOException e) {
            Log.w(LOG_TAG, String.format("IOException %s when running tests %s on %s",
                    e.getMessage(), runner.getPackageName(), getSerialNumber()));
            for (ITestRunListener listener : listeners) {
                listener.testRunFailed("lost connection with device");
            }

            // TODO: no attempt tracking here. Would be good to catch scenario where repeated
            // test runs fail even though recovery is succeeding
            recoverDevice();
        }
    }

    /**
     * {@inheritDoc}
     */
    public boolean pullFile(final String remoteFilePath, final File localFile)
            throws DeviceNotAvailableException {
        IDeviceAction pullAction = new IDeviceAction() {
            public boolean doAction() throws IOException {
                SyncService syncService = getIDevice().getSyncService();
                SyncResult result = syncService.pullFile(remoteFilePath,
                        localFile.getAbsolutePath(), SyncService.getNullProgressMonitor());
                if (result.getCode() == SyncService.RESULT_OK) {
                    return true;
                } else {
                    // assume that repeated attempts won't help, just return immediately
                    Log.w(LOG_TAG, String.format("Failed to pull %s from %s. Reason code: %d, " +
                            "message %s", remoteFilePath, getSerialNumber(), result.getCode(),
                            result.getMessage()));
                    return false;
                }
            }
        };
        return performDeviceAction(String.format("pull %s", remoteFilePath), pullAction,
                MAX_RETRY_ATTEMPTS);
    }

    /**
     * {@inheritDoc}
     */
    public boolean pushFile(final File localFile, final String remoteFilePath)
            throws DeviceNotAvailableException {
        IDeviceAction pushAction = new IDeviceAction() {
            public boolean doAction() throws IOException {
                SyncService syncService = getIDevice().getSyncService();
                SyncResult result = syncService.pushFile(localFile.getAbsolutePath(),
                        remoteFilePath, SyncService.getNullProgressMonitor());
                if (result.getCode() == SyncService.RESULT_OK) {
                    return true;
                } else {
                    // assume that repeated attempts won't help, just return immediately
                    Log.w(LOG_TAG, String.format("Failed to push to %s on device %s. Reason " +
                            " code: %d, message %s", remoteFilePath, getSerialNumber(),
                            result.getCode(), result.getMessage()));
                    return false;
                }
            }
        };
        return performDeviceAction(String.format("push %s", remoteFilePath), pushAction,
                MAX_RETRY_ATTEMPTS);
    }

    /**
     * Helper method to determine if file on device exists.
     *
     * @param destPath the absolute path of file on device to check
     * @return <code>true</code> if file exists, <code>false</code> otherwise.
     * @throws DeviceNotAvailableException if device communication is lost
     */
    public boolean doesFileExist(String destPath) throws DeviceNotAvailableException {
        String lsGrep = executeShellCommand(String.format("ls \"%s\"", destPath));
        return !lsGrep.contains("No such file or directory");
    }

    /**
     * Recursively performs an action on this device. Attempts to recover device and retry command
     * if action fails.
     *
     * @param actionDescription a short description of action to be performed. Used for logging
     * purposes only.
     * @param action the action to be performed
     * @param callback optional action to perform if action fails but recovery succeeds. If no
     * post recovery action needs to be taken pass in <code>null</code>
     * @param remainingAttempts the remaining retry attempts to make for action if it fails but
     * recovery succeeds
     *
     * @returns <code>true</code> if action was performed successfully
     *
     * @throws DeviceNotAvailableException if recovery attempt fails or max attempts done without
     * success
     */
    private boolean performDeviceAction(String actionDescription, IDeviceAction action,
            int remainingAttempts) throws DeviceNotAvailableException {
        try {
            return action.doAction();
        } catch (IOException e) {
            Log.w(LOG_TAG, String.format("IOException %s when attempting %s on device %s",
                    e.getMessage(), actionDescription, getSerialNumber()));

            recoverDevice();

            if (remainingAttempts > 0) {
                return performDeviceAction(actionDescription, action, remainingAttempts - 1);
            } else {
                throw new DeviceNotAvailableException(String.format("Attempted %s multiple times " +
                        "on device %s without communication success. Aborting.", actionDescription,
                        getSerialNumber()));
            }
        }
    }

    /**
     * Attempts to recover device communication.
     *
     * @throws DeviceNotAvailableException if device is not longer available
     */
    private void recoverDevice() throws DeviceNotAvailableException {
        Log.i(LOG_TAG, String.format("Attempting recovery on %s", getSerialNumber()));
        mRecovery.recoverDevice(this);
        Log.i(LOG_TAG, String.format("Recovery successful for %s", getSerialNumber()));
    }
}
