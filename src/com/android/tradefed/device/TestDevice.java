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

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;

/**
 * Default implementation of a {@link ITestDevice}
 */
class TestDevice implements ILogTestDevice {

    private static final String LOG_TAG = "TestDevice";
    /** the default number of command retry attempts to perform */
    private static final int MAX_RETRY_ATTEMPTS = 3;
    /** time in ms to wait before retrying a logcat operation if interrupted */
    private static final int LOGCAT_SLEEP_TIME = 10*1000;
    /** the max number of bytes to store in logcat tmp buffer */
    private static final int LOGCAT_BUFF_SIZE = 32 * 1024;
    private static final String LOGCAT_CMD = "logcat -v threadtime";
    /**
     * The maximum number of bytes returned by {@link TestDevice#getLogcat()}.
     * TODO: make this configurable
     */
    private static final int LOGCAT_MAX_DATA = 512 * 1024;

    private final IDevice mIDevice;
    private final IDeviceRecovery mRecovery;
    private LogCatReceiver mLogcatReceiver;

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

    /**
     * {@inheritDoc}
     */
    public void startLogcat() {
        if (mLogcatReceiver != null) {
            Log.d(LOG_TAG, String.format("Already capturing logcat for %s, ignoring",
                    getSerialNumber()));
            return;
        }
        mLogcatReceiver = new LogCatReceiver();
        mLogcatReceiver.start();
    }

    /**
     * {@inheritDoc}
     * <p/>
     * Works in two modes:
     * <li>If the logcat is currently being captured in the background (i.e. the
     * manager of this device is calling startLogcat and stopLogcat as appropriate), will return
     * the current contents of the background logcat capture, up to {@link #LOGCAT_MAX_DATA}.
     * <li>Otherwise, will return a static dump of the logcat data if device is currently responding
     */
    public InputStream getLogcat() {
        if (mLogcatReceiver == null) {
            Log.w(LOG_TAG, String.format("Not capturing logcat for %s, returning a dump for",
                    getSerialNumber()));
            return getLogcatDump();
        } else {
            return mLogcatReceiver.getLogcatData();
        }
    }

    /**
     * Get a dump of the current logcat for device, up to a max of {@link #LOGCAT_MAX_DATA}.
     *
     * @return a {@link InputStream} of the logcat data. An empty stream is returned if fail to
     * capture logcat data.
     */
    private InputStream getLogcatDump() {
        String output = "";
        try {
            // use IDevice directly because we don't want callers to handle
            // DeviceNotAvailableException for this method
            CollectingOutputReceiver receiver = new CollectingOutputReceiver();
            // add -d parameter to make this a non blocking call
            getIDevice().executeShellCommand(LOGCAT_CMD + " -d", receiver);
            output = receiver.getOutput();
            if (output.length() > LOGCAT_MAX_DATA) {
                output = output.substring(LOGCAT_MAX_DATA - output.length(), output.length());
            }
        } catch (IOException e) {
            Log.w(LOG_TAG, String.format("Failed to get logcat dump %s", getSerialNumber()));
        }
        return new ByteArrayInputStream(output.getBytes());
    }

    /**
     * {@inheritDoc}
     */
    public void stopLogcat() {
        if (mLogcatReceiver != null) {
            mLogcatReceiver.cancel();
            mLogcatReceiver = null;
        } else {
            Log.w(LOG_TAG, String.format("Attempting to stop logcat when not capturing for %s",
                    getSerialNumber()));
        }
    }

    /**
     * A background thread that captures logcat data into a temporary host file.
     * <p/>
     * This is done so:
     * <li>if device goes permanently offline during a test, the log data is retained.
     * <li>to capture more data than may fit in device's circular log.
     */
    private class LogCatReceiver extends Thread implements IShellOutputReceiver {

        private boolean mIsCancelled = false;
        private OutputStream mOutStream;
        private File mTmpFile;

        /**
         * {@inheritDoc}
         */
        public void addOutput(byte[] data, int offset, int length) {
            try {
                mOutStream.write(data, offset, length);
            } catch (IOException e) {
                Log.w(LOG_TAG, String.format(
                        "failed to write logcat data for %s.", getSerialNumber()));
            }
        }

        public InputStream getLogcatData() {
            flush();
            try {
                FileInputStream fileStream = new FileInputStream(mTmpFile);
                // adjust stream position to so only up to LOGCAT_MAX_DATA bytes can be read
                if (mTmpFile.length() > LOGCAT_MAX_DATA) {
                    fileStream.skip(mTmpFile.length() - LOGCAT_MAX_DATA);
                }
                return fileStream;
            } catch (IOException e) {
                Log.e(LOG_TAG, String.format(
                        "failed to get logcat data for %s.", getSerialNumber()));
                Log.e(LOG_TAG, e);
                // return an empty input stream
                return new ByteArrayInputStream(new byte[0]);
            }
        }

        /**
         * {@inheritDoc}
         */
        public void flush() {
            try {
                mOutStream.flush();
            } catch (IOException e) {
                Log.w(LOG_TAG, String.format(
                        "failed to flush logcat data for %s.", getSerialNumber()));
            }
        }

        public void cancel() {
            mIsCancelled = true;
            try {
                if (mOutStream != null) {
                    mOutStream.close();
                    mOutStream = null;
                }

            } catch (IOException e) {
                Log.w(LOG_TAG, String.format(
                        "failed to close logcat stream for %s.", getSerialNumber()));
            }
            if (mTmpFile != null) {
                mTmpFile.delete();
                mTmpFile = null;
            }
        }

        /**
         * {@inheritDoc}
         */
        public boolean isCancelled() {
            return mIsCancelled;
        }

        @Override
        public void run() {
            try {
                mTmpFile = File.createTempFile(String.format("logcat_%s_", getSerialNumber()),
                        ".txt");
                Log.i(LOG_TAG, String.format("Created tmp logcat file %s",
                        mTmpFile.getAbsolutePath()));
                mOutStream = new BufferedOutputStream(new FileOutputStream(mTmpFile),
                        LOGCAT_BUFF_SIZE);

            } catch (IOException e) {
                Log.w(LOG_TAG, String.format(
                        "failed to create tmp logcat file for %s.", getSerialNumber()));
                return;
            }

            // continually run in a loop attempting to grab logcat data, skipping recovery
            // this is done so logcat data can continue to be captured even if device goes away and
            // then comes back online
            while (!isCancelled()) {
                try {
                    Log.i(LOG_TAG, String.format("Starting logcat for %s.", getSerialNumber()));
                    getIDevice().executeShellCommand(LOGCAT_CMD, this);
                } catch (IOException e) {
                    final String msg = String.format(
                            "logcat capture interrupted for %s. Sleeping for %d ms. May see " +
                            "duplicate content in log.",
                            getSerialNumber(), LOGCAT_SLEEP_TIME);
                    Log.w(LOG_TAG, msg);
                    appendDeviceLogMsg(msg);
                }
                try {
                    sleep(LOGCAT_SLEEP_TIME);
                } catch (InterruptedException e) {
                    // ignore
                }
            }
        }

        /**
         * Adds a message to the captured device log.
         * @param msg
         */
        private void appendDeviceLogMsg(String msg) {
            // add the msg to device tmp log, so readers will know logcat was interrupted
            try {
                mOutStream.write("\n*******************\n".getBytes());
                mOutStream.write(msg.getBytes());
                mOutStream.write("\n*******************\n".getBytes());
            } catch (IOException e) {
                Log.w(LOG_TAG, String.format(
                        "failed to write logcat data for %s.", getSerialNumber()));
            }
        }
    }
}
