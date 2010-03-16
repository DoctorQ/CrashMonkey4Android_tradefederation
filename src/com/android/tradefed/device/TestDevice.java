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
import com.android.ddmlib.testrunner.IRemoteAndroidTestRunner;
import com.android.ddmlib.testrunner.ITestRunListener;

import java.io.IOException;
import java.util.Collection;

/**
 * Default implementation of a {@link ITestDevice}
 */
class TestDevice implements ITestDevice {

    private final IDevice mIDevice;
    private final IDeviceRecovery mRecovery;

    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final String LOG_TAG = "TestDevice";

    /**
     * Interface for do a post-successful recovery action, such as retrying command that failed,
     * etc
     */
    private static interface IPostRecovery {
        void handleRecovery() throws DeviceNotAvailableException;
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
    public void executeShellCommand(String command, IShellOutputReceiver receiver)
            throws DeviceNotAvailableException {
        executeShellCommand(command, receiver, MAX_RETRY_ATTEMPTS);
    }

    /**
     * Run a single shell command, recursively re-trying up to remainingAttempts if
     * necessary.
     *
     * @param remainingAttempts the number of additional attempts if command fails but recovery
     * succeeds
     * @param receiver the {@link IShellOutputReceiver} of command
     * @throws DeviceNotAvailableException if command and recovery attempt fail
     */
    private void executeShellCommand(final String command, final IShellOutputReceiver receiver,
            final int remainingAttempts) throws DeviceNotAvailableException {
        try {
            mIDevice.executeShellCommand(command, receiver);
        } catch (IOException e) {
            Log.w(LOG_TAG, String.format("IOException %s when attempting shell cmd %s on %s",
                    e.getMessage(), command, getSerialNumber()));
            recoverDevice(remainingAttempts, new IPostRecovery() {

                public void handleRecovery() throws DeviceNotAvailableException {
                    executeShellCommand(command, receiver, remainingAttempts - 1);
                }
            });
        }
    }

    /**
     * Attempts to recover a device when a command fails
     *
     * @param attempts the number of recovery attempts made for current action
     * @param callback the action to perform if recovery attempt succeeds
     * @throws DeviceNotAvailableException if recovery attempt fails or if the number of recovery
     * attempts is > {@link MAX_CMD_ATTEMPTS}
     */
    private void recoverDevice(int remainingAttempts, IPostRecovery callback)
    throws DeviceNotAvailableException {

        if (remainingAttempts < 0) {
            Log.w(LOG_TAG, String.format("Gave up attempting to recover %s", getSerialNumber()));
            throw new DeviceNotAvailableException();
        }
        Log.i(LOG_TAG, String.format("Attempting recovery on %s", getSerialNumber()));
        mRecovery.recoverDevice(this);
        Log.i(LOG_TAG, String.format("Recovery successful for %s", getSerialNumber()));
        callback.handleRecovery();
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
            recoverDevice(1, new IPostRecovery() {

                public void handleRecovery() throws DeviceNotAvailableException {
                }
            });
        }
    }

    /**
     * {@inheritDoc}
     */
    public String getSerialNumber() {
        return getIDevice().getSerialNumber();
    }
}
