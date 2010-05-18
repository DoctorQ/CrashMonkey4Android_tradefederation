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
import com.android.tradefed.util.RunUtil;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    /** The  time in ms to wait for a device to be back online after a adb root. */
    private static final int ROOT_TIMEOUT = 1 * 60 * 1000;

    /** The  time in ms to wait for a device to boot into fastboot. */
    private static final int FASTBOOT_TIMEOUT = 1 * 60 * 1000;

    private final IDevice mIDevice;
    private final IDeviceRecovery mRecovery;
    private final IDeviceStateMonitor mMonitor;

    // TODO: make this an Option - consider moving postBootSetup elsewhere
    private boolean mEnableAdbRoot = true;

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
    TestDevice(IDevice device, IDeviceRecovery recovery, IDeviceStateMonitor monitor) {
        mIDevice = device;
        mRecovery = recovery;
        mMonitor = monitor;
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
        String output = receiver.getOutput();
        Log.d(LOG_TAG, String.format("%s on %s returned %s", command, getSerialNumber(), output));
        return output;
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
     * {@inheritDoc}
     */
    public boolean doesFileExist(String destPath) throws DeviceNotAvailableException {
        String lsGrep = executeShellCommand(String.format("ls \"%s\"", destPath));
        return !lsGrep.contains("No such file or directory");
    }

    /**
     * {@inheritDoc}
     */
    public long getExternalStoreFreeSpace() throws DeviceNotAvailableException {
        String externalStorePath = getIDevice().getMountPoint(
                IDevice.MNT_EXTERNAL_STORAGE);
        String output = executeShellCommand(String.format("df %s", externalStorePath));
        final Pattern freeSpacePattern = Pattern.compile("(\\d+)K available");
        Matcher patternMatcher = freeSpacePattern.matcher(output);
        if (patternMatcher.find()) {
            String freeSpaceString = patternMatcher.group(1);
            try {
                return Long.parseLong(freeSpaceString);
            } catch (NumberFormatException e) {
                // fall through
            }
        }
        Log.e(LOG_TAG, String.format(
                "free space command output \"%s\" did not match expected pattern ", output));
        return 0;
    }

    /**
     * {@inheritDoc}
     */
    public String executeAdbCommand(String... cmdArgs) throws DeviceNotAvailableException {
        final String[] fullCmd = buildAdbCommand(cmdArgs);
        final String[] output = new String[1];
        IDeviceAction adbAction = new IDeviceAction() {
            public boolean doAction() throws IOException {
                output[0] =  RunUtil.runTimedCmd(2*60*1000, fullCmd);
                if (output[0] == null) {
                    throw new IOException();
                }
                return true;
            }
        };
        performDeviceAction(String.format("adb %s", cmdArgs[0]), adbAction, MAX_RETRY_ATTEMPTS);
        return output[0];
    }

    /**
     * {@inheritDoc}
     */
    public boolean executeFastbootCommand(String... cmdArgs) throws DeviceNotAvailableException {
        final String[] fullCmd = buildFastbootCommand(cmdArgs);
        IDeviceAction fastbootAction = new IDeviceAction() {
            public boolean doAction() throws IOException {
                String result =  RunUtil.runTimedCmd(2*60*1000, fullCmd);
                if (result == null) {
                    throw new IOException();
                }
                return true;
            }
        };
        // TODO: change to do fastboot recovery
        return performDeviceAction(String.format("fastboot %s", cmdArgs[0]), fastbootAction,
                MAX_RETRY_ATTEMPTS);
    }

    /**
     * Builds the OS command for the given adb command and args
     */
    private String[] buildAdbCommand(String... commandArgs) {
        final int numAdbArgs = 3;
        String[] newCmdArgs = new String[commandArgs.length + numAdbArgs];
        // TODO: use full adb path
        newCmdArgs[0] = "adb";
        newCmdArgs[1] = "-s";
        newCmdArgs[2] = getSerialNumber();
        System.arraycopy(commandArgs, 0, newCmdArgs, numAdbArgs, commandArgs.length);
        return newCmdArgs;
    }

    /**
     * Builds the OS command for the given fastboot command and args
     */
    private String[] buildFastbootCommand(String... commandArgs) {
        final int numAdbArgs = 3;
        String[] newCmdArgs = new String[commandArgs.length + numAdbArgs];
        // TODO: use full fastboot path
        newCmdArgs[0] = "fastboot";
        newCmdArgs[1] = "-s";
        newCmdArgs[2] = getSerialNumber();
        System.arraycopy(commandArgs, 0, newCmdArgs, numAdbArgs, commandArgs.length);
        return newCmdArgs;
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
        mRecovery.recoverDevice(getIDevice(), mMonitor);
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
            interrupt();
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
                    Log.d(LOG_TAG, String.format("Starting logcat for %s.", getSerialNumber()));
                    getIDevice().executeShellCommand(LOGCAT_CMD, this);
                } catch (IOException e) {
                    final String msg = String.format(
                            "logcat capture interrupted for %s. Sleeping for %d ms. May see " +
                            "duplicate content in log.",
                            getSerialNumber(), LOGCAT_SLEEP_TIME);
                    Log.d(LOG_TAG, msg);
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

    IDeviceStateMonitor getDeviceStateMonitor() {
        return mMonitor;
    }

    /**
     * Perform instructions to configure device for testing that must be done after every boot.
     * <p/>
     * TODO: move this to another configurable interface
     *
     * @throws DeviceNotAvailableException if connection with device is lost and cannot be
     * recovered.
     */
    public void postBootSetup() throws DeviceNotAvailableException {
        if (mEnableAdbRoot) {
            enableAdbRoot();
        }
        // turn off keyguard
        executeShellCommand("input keyevent 82");
    }

    /**
     * {@inheritDoc}
     */
    public void rebootIntoBootloader() throws DeviceNotAvailableException {

        if (TestDeviceState.FASTBOOT == mMonitor.getDeviceState()) {
            Log.i(LOG_TAG, String.format("device %s already in fastboot. Rebooting anyway",
                    getSerialNumber()));
            executeFastbootCommand("reboot", "bootloader");
        } else {
            Log.i(LOG_TAG, String.format("Booting device %s into bootloader",
                    getSerialNumber()));
            doAdbReboot("bootloader");
        }
        if (!mMonitor.waitForDeviceBootloader(FASTBOOT_TIMEOUT)) {
            // TODO: add recoverBootloader method
            mRecovery.recoverDeviceBootloader(getIDevice(), mMonitor);
        }
        // TODO: check for fastboot responsiveness ?
    }

    /**
     * {@inheritDoc}
     */
    public void reboot() throws DeviceNotAvailableException {
        if (TestDeviceState.FASTBOOT == mMonitor.getDeviceState()) {
            Log.i(LOG_TAG, String.format("device %s in fastboot. Rebooting to userspace.",
                    getSerialNumber()));
            executeFastbootCommand("reboot");
        } else {
            Log.i(LOG_TAG, String.format("Rebooting device %s", getSerialNumber()));
            doAdbReboot(null);
            waitForDeviceNotAvailable("reboot", 2*60*1000);
        }
        if (!mMonitor.waitForDeviceAvailable()) {
            recoverDevice();
        }
        postBootSetup();
    }

    /**
     * Perform a adb reboot.
     *
     * @param into the bootloader name to reboot into, or <code>null</code> to just reboot the
     * device.
     * @throws DeviceNotAvailableException
     */
    private void doAdbReboot(final String into) throws DeviceNotAvailableException {
        IDeviceAction rebootAction = new IDeviceAction() {
            public boolean doAction() throws IOException {
                getIDevice().reboot(into);
                return true;
            }
        };
        performDeviceAction("reboot", rebootAction, MAX_RETRY_ATTEMPTS);
    }

    private void waitForDeviceNotAvailable(String operationDesc, long time) {
        // TODO: a bit of a race condition here. Would be better to start a device listener
        // before the operation
        if (!mMonitor.waitForDeviceNotAvailable(time)) {
            // above check is flaky, ignore till better solution is found
            Log.w(LOG_TAG, String.format(
                    "Did not detect device %s becoming unavailable after %s",
                    getSerialNumber(), operationDesc));
        }
    }

    /**
     * {@inheritDoc}
     */
    public boolean enableAdbRoot() throws DeviceNotAvailableException {
        Log.i(LOG_TAG, String.format("adb root on device %s", getSerialNumber()));

        String output = executeAdbCommand("root");
        boolean success = (output.contains("restarting adbd as root") ||
                           output.contains("adbd is already running as root"));
        if (!success) {
            return false;
        }
        // wait for device to disappear from adb
        waitForDeviceNotAvailable("root", 20*1000);
        // wait for device to be back online
        waitForDeviceAvailable(ROOT_TIMEOUT);
        return true;
    }


    /**
     * {@inheritDoc}
     */
    public void waitForDeviceOnline() throws DeviceNotAvailableException {
        if (!mMonitor.waitForDeviceOnline()) {
            recoverDevice();
        }
    }

    /**
     * {@inheritDoc}
     */
    public void waitForDeviceAvailable(long waitTime) throws DeviceNotAvailableException {
        if (!mMonitor.waitForDeviceAvailable(waitTime)) {
            recoverDevice();
        }
    }

    /**
     * {@inheritDoc}
     */
    public void waitForDeviceAvailable() throws DeviceNotAvailableException {
        if (!mMonitor.waitForDeviceAvailable()) {
            recoverDevice();
        }
    }

    void setEnableAdbRoot(boolean enable) {
        mEnableAdbRoot = enable;
    }
}
