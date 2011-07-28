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

import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.FileListingService;
import com.android.ddmlib.FileListingService.FileEntry;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.IShellOutputReceiver;
import com.android.ddmlib.InstallException;
import com.android.ddmlib.Log;
import com.android.ddmlib.NullOutputReceiver;
import com.android.ddmlib.RawImage;
import com.android.ddmlib.ShellCommandUnresponsiveException;
import com.android.ddmlib.SyncException;
import com.android.ddmlib.SyncException.SyncError;
import com.android.ddmlib.SyncService;
import com.android.ddmlib.TimeoutException;
import com.android.ddmlib.testrunner.IRemoteAndroidTestRunner;
import com.android.ddmlib.testrunner.ITestRunListener;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.WifiHelper.WifiState;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ByteArrayInputStreamSource;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.SnapshotInputStreamSource;
import com.android.tradefed.result.StubTestRunListener;
import com.android.tradefed.util.ArrayUtil;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;
import com.android.tradefed.util.StreamUtil;

import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.SequenceInputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

/**
 * Default implementation of a {@link ITestDevice}
 */
class TestDevice implements IManagedTestDevice {

    private static final String LOG_TAG = "TestDevice";
    /** the default number of command retry attempts to perform */
    static final int MAX_RETRY_ATTEMPTS = 2;
    /** the max number of bytes to store in logcat tmp buffer */
    private static final int LOGCAT_BUFF_SIZE = 32 * 1024;
    private static final String LOGCAT_CMD = "logcat -v threadtime";
    private static final String BUGREPORT_CMD = "bugreport";
    /**
     * Allow pauses of up to 2 minutes while receiving bugreport.  Note that dumpsys may pause up to
     * a minute while waiting for unresponsive components, but should bail after that minute, if it
     *  will ever terminate on its own.
     */
    private static final int BUGREPORT_TIMEOUT = 2 * 60 * 1000;

    /** The password for encrypting and decrypting the device. */
    private static final String ENCRYPTION_PASSWORD = "android";
    /** Encrypting with inplace can take up to 2 hours. */
    private static final int ENCRYPTION_INPLACE_TIMEOUT = 2 * 60 * 60 * 1000;
    /** Encrypting with wipe can take up to 5 minutes. */
    private static final int ENCRYPTION_WIPE_TIMEOUT = 5 * 60 * 1000;
    /** Beginning of the string returned by vdc for "vdc enablecrypto". */
    private static final String ENCRYPTION_SUPPORTED_OUTPUT = "500 Usage: cryptfs enablecrypto";

    /** The time in ms to wait before starting logcat for a device */
    private int mLogStartDelay = 5*1000;
    /** The time in ms to wait for a device to boot into fastboot. */
    private static final int FASTBOOT_TIMEOUT = 1 * 60 * 1000;
    /** The time in ms to wait for a device to boot into recovery. */
    private static final int ADB_RECOVERY_TIMEOUT = 1 * 60 * 1000;
    /** The time in ms to wait for a device to reboot to full system. */
    private static final int REBOOT_TIMEOUT = 2 * 60 * 1000;
    /** The time in ms to wait for a device to become unavailable. Should usually be short */
    private static final int DEFAULT_UNAVAILABLE_TIMEOUT = 10 * 1000;
    /** The time in ms to wait for a recovery that we skip because of the NONE mode */
    static final int NONE_RECOVERY_MODE_DELAY = 1000;
    /** number of attempts made to clear dialogs */
    private static final int NUM_CLEAR_ATTEMPTS = 5;
    /** the command used to dismiss a error dialog. Currently sends a DPAD_CENTER key event */
    static final String DISMISS_DIALOG_CMD = "input keyevent 23";

    private static final String BUILD_ID_PROP = "ro.build.version.incremental";

    /** The time in ms to wait for a command to complete. */
    private int mCmdTimeout = 2 * 60 * 1000;
    /** The time in ms to wait for a 'long' command to complete. */
    private long mLongCmdTimeout = 12 * 60 * 1000;

    private IDevice mIDevice;
    private IDeviceRecovery mRecovery;
    private final IDeviceStateMonitor mMonitor;
    private TestDeviceState mState = TestDeviceState.ONLINE;
    private final Semaphore mFastbootLock = new Semaphore(1);
    private LogCatReceiver mLogcatReceiver;
    private IFileEntry mRootFile = null;
    private boolean mFastbootEnabled = true;

    // TODO: TestDevice is not loaded from configuration yet, so these options are currently fixed

    @Option(name = "enable-root", description = "enable adb root on boot.")
    private boolean mEnableAdbRoot = true;

    @Option(name = "disable-keyguard", description = "attempt to disable keyguard once complete.")
    private boolean mDisableKeyguard = true;

    @Option(name = "disable-keyguard-cmd", description = "shell command to disable keyguard.")
    private String mDisableKeyguardCmd = "input keyevent 82";

    /**
     * The maximum size of a tmp logcat file, in bytes.
     * <p/>
     * The actual size of the log info stored will be up to twice this number, as two logcat files
     * are stored.
     */
    @Option(name = "max-tmp-logcat-file", description =
        "The maximum size of a tmp logcat file, in bytes.")
    private long mMaxLogcatFileSize = 10 * 1024 * 1024;
    private Process mEmulatorProcess;

    private RecoveryMode mRecoveryMode = RecoveryMode.AVAILABLE;

    /**
     * Interface for a generic device communication attempt.
     */
    private abstract interface DeviceAction {

        /**
         * Execute the device operation.
         *
         * @return <code>true</code> if operation is performed successfully, <code>false</code>
         *         otherwise
         * @throws Exception if operation terminated abnormally
         */
        public boolean run() throws IOException, TimeoutException, AdbCommandRejectedException,
                ShellCommandUnresponsiveException, InstallException, SyncException;
    }

    /**
     * A {@link DeviceAction} for running a OS 'adb ....' command.
     */
    private class AdbAction implements DeviceAction {
        /** the output from the command */
        String mOutput = null;
        private String[] mCmd;

        AdbAction(String[] cmd) {
            mCmd = cmd;
        }

        public boolean run() throws TimeoutException, IOException {
            CommandResult result = getRunUtil().runTimedCmd(getCommandTimeout(), mCmd);
            // TODO: how to determine device not present with command failing for other reasons
            if (result.getStatus() == CommandStatus.EXCEPTION) {
                throw new IOException();
            } else if (result.getStatus() == CommandStatus.TIMED_OUT) {
                throw new TimeoutException();
            } else if (result.getStatus() == CommandStatus.FAILED) {
                // interpret as communication failure
                throw new IOException();
            }
            mOutput = result.getStdout();
            return true;
        }
    }

    /**
     * A {@link DeviceAction} that runs 'adb root'
     */
    private class AdbRootAction extends AdbAction {
        /**
         * flag to signal 'adb root' was successful, and that caller should wait for device to go
         * unavailable, then online again.
         */
        boolean mNeedWait = false;

        AdbRootAction(String[] fullCmd) {
            super(fullCmd);
        }

        @Override
        public boolean run() throws TimeoutException, IOException {
            mNeedWait = false;
            if (super.run()) {
                CLog.d("adb root on %s returned '%s'", getSerialNumber(), mOutput.trim());
                if (mOutput.contains("adbd is already running as root")) {
                    // this should rarely/never happen, since enableAdbRoot checks if adb runs as
                    // root..
                    return true;
                } else if (mOutput.contains("restarting adbd as root")) {
                    mNeedWait= true;
                    return true;

                } else {
                    // interpret as communication failure
                    throw new IOException(String.format("Unrecognized output from adb root: %s",
                            mOutput.trim()));
                }
            }
            return false;
        }
    }


    /**
     * Creates a {@link TestDevice}.
     *
     * @param device the associated {@link IDevice}
     * @param recovery the {@link IDeviceRecovery} mechanism to use
     */
    TestDevice(IDevice device, IDeviceStateMonitor monitor) {
        mIDevice = device;
        mMonitor = monitor;
    }

    /**
     * Get the {@link RunUtil} instance to use.
     * <p/>
     * Exposed for unit testing.
     */
    IRunUtil getRunUtil() {
        return RunUtil.getDefault();
    }

    /**
     * Sets the max size of a tmp logcat file.
     *
     * @param size max byte size of tmp file
     */
    void setTmpLogcatSize(long size) {
        mMaxLogcatFileSize = size;
    }

    /**
     * Sets the time in ms to wait before starting logcat capture for a online device.
     *
     * @param delay the delay in ms
     */
    void setLogStartDelay(int delay) {
        mLogStartDelay = delay;
    }

    /**
     * {@inheritDoc}
     */
    public IDevice getIDevice() {
        synchronized (mIDevice) {
            return mIDevice;
        }
    }

    /**
     * {@inheritDoc}
     */
    public void setIDevice(IDevice newDevice) {
        IDevice currentDevice = mIDevice;
        if (!getIDevice().equals(newDevice)) {
            synchronized (currentDevice) {
                mIDevice = newDevice;
            }
            mMonitor.setIDevice(mIDevice);
        }
    }

    /**
     * {@inheritDoc}
     */
    public String getSerialNumber() {
        return getIDevice().getSerialNumber();
    }

    private boolean nullOrEmpty(String string) {
        return string == null || string.isEmpty();
    }

    /**
     * Fetch a device property, from the ddmlib cache by default, and falling back to either
     * `adb shell getprop` or `fastboot getvar` depending on whether the device is in Fastboot or
     * not.
     *
     * @param prop The name of the device property as returned by `adb shell getprop`
     * @param fastbootVar The name of the equivalent fastboot variable to query
     * @param description A simple description of the variable.  First letter should be capitalized.
     * @return A string, possibly {@code null} or empty, containing the value of the given property
     */
    private String getProperty(String prop, String fastbootVar, String description)
            throws DeviceNotAvailableException, UnsupportedOperationException {
        String value = getIDevice().getProperty(prop);
        if (nullOrEmpty(value)) {
            /* DDMS may not have processed all of the properties yet, or the device may be in
             * fastboot, or the device may simply be misconfigured or malfunctioning.  Try querying
             * directly.
             */
            if (getDeviceState() == TestDeviceState.FASTBOOT) {
                CLog.i("%s for device %s is null, re-querying in fastboot", description,
                        getSerialNumber());
                value = getFastbootVariable(fastbootVar);
            } else {
                CLog.w("%s for device %s is null, re-querying", description, getSerialNumber());
                value = executeShellCommand(String.format("getprop '%s'", prop)).trim();
            }
        }
        return value;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getBootloaderVersion() throws UnsupportedOperationException,
            DeviceNotAvailableException {
        return getProperty("ro.bootloader", "version-bootloader", "Bootloader");
    }

    /**
     * {@inheritDoc}
     */
    public String getProductType() throws DeviceNotAvailableException {
        return internalGetProductType(MAX_RETRY_ATTEMPTS);
    }

    /**
     * {@see getProductType()}
     *
     * @param retryAttempts The number of times to try calling {@see recoverDevice()} if the
     *        device's product type cannot be found.
     */
    private String internalGetProductType(int retryAttempts) throws DeviceNotAvailableException {
        String productType = getProperty("ro.hardware", "product", "Product type");

        // Things will likely break if we don't have a valid product type.  Try recovery (in case
        // the device is only partially booted for some reason), and if that doesn't help, bail.
        if (nullOrEmpty(productType)) {
            if (retryAttempts > 0) {
                recoverDevice();
                productType = internalGetProductType(retryAttempts - 1);
            }

            if (nullOrEmpty(productType)) {
                throw new DeviceNotAvailableException(String.format(
                        "Could not determine product type for device %s.", getSerialNumber()));
            }
        }

        return productType;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getFastbootProductType()
            throws DeviceNotAvailableException, UnsupportedOperationException {
        return getFastbootVariable("product");
    }

    /**
     * {@inheritDoc}
     */
    public String getProductVariant() throws DeviceNotAvailableException {
        return getProperty("ro.product.device", "variant", "Product variant");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getFastbootProductVariant()
            throws DeviceNotAvailableException, UnsupportedOperationException {
        return getFastbootVariable("variant");
    }

    private String getFastbootVariable(String variableName)
            throws DeviceNotAvailableException, UnsupportedOperationException {
        CommandResult result = executeFastbootCommand("getvar", variableName);
        if (result.getStatus() == CommandStatus.SUCCESS) {
            Pattern fastbootProductPattern = Pattern.compile(variableName + ":\\s(.*)\\s");
            // fastboot is weird, and may dump the output on stderr instead of stdout
            String resultText = result.getStdout();
            if (resultText == null || resultText.length() < 1) {
                resultText = result.getStderr();
            }
            Matcher matcher = fastbootProductPattern.matcher(resultText);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    public String getBuildId() {
        String bid = getIDevice().getProperty(BUILD_ID_PROP);
        if (bid == null) {
            Log.w(LOG_TAG, String.format("Could not get device %s build id.", getSerialNumber()));
            return IBuildInfo.UNKNOWN_BUILD_ID;
        }
        return bid;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void executeShellCommand(final String command, final IShellOutputReceiver receiver)
            throws DeviceNotAvailableException {
        DeviceAction action = new DeviceAction() {
            public boolean run() throws TimeoutException, IOException,
                    AdbCommandRejectedException, ShellCommandUnresponsiveException {
                getIDevice().executeShellCommand(command, receiver, mCmdTimeout);
                return true;
            }
        };
        performDeviceAction(String.format("shell %s", command), action, MAX_RETRY_ATTEMPTS);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void executeShellCommand(final String command, final IShellOutputReceiver receiver,
            final int maxTimeToOutputShellResponse, int retryAttempts)
            throws DeviceNotAvailableException {
        DeviceAction action = new DeviceAction() {
            public boolean run() throws TimeoutException, IOException, AdbCommandRejectedException,
                    ShellCommandUnresponsiveException {
                getIDevice().executeShellCommand(command, receiver, maxTimeToOutputShellResponse);
                return true;
            }
        };
        performDeviceAction(String.format("shell %s", command), action, retryAttempts);
    }

    /**
     * {@inheritDoc}
     */
    public String executeShellCommand(String command) throws DeviceNotAvailableException {
        CollectingOutputReceiver receiver = new CollectingOutputReceiver();
        executeShellCommand(command, receiver);
        String output = receiver.getOutput();
        Log.v(LOG_TAG, String.format("%s on %s returned %s", command, getSerialNumber(), output));
        return output;
    }

    /**
     * {@inheritDoc}
     */
    public boolean runInstrumentationTests(final IRemoteAndroidTestRunner runner,
            final Collection<ITestRunListener> listeners) throws DeviceNotAvailableException {
        RunFailureListener failureListener = new RunFailureListener();
        listeners.add(failureListener);
        DeviceAction runTestsAction = new DeviceAction() {
            @Override
            public boolean run() throws IOException, TimeoutException, AdbCommandRejectedException,
                    ShellCommandUnresponsiveException, InstallException, SyncException {
                runner.run(listeners);
                return true;
            }

        };
        boolean result = performDeviceAction(String.format("run %s instrumentation tests",
                runner.getPackageName()), runTestsAction, 0);
        if (failureListener.isRunFailure()) {
            // run failed, might be system crash. Ensure device is up
            if (mMonitor.waitForDeviceAvailable(5 * 1000) == null) {
                // device isn't up, recover
                recoverDevice();
            }
        }
        return result;
    }

    private static class RunFailureListener extends StubTestRunListener {
        private boolean mIsRunFailure = false;

        @Override
        public void testRunFailed(String message) {
            mIsRunFailure = true;
        }

        public boolean isRunFailure() {
            return mIsRunFailure;
        }
    }

    /**
     * {@inheritDoc}
     */
    public boolean runInstrumentationTests(IRemoteAndroidTestRunner runner,
            ITestRunListener... listeners) throws DeviceNotAvailableException {
        List<ITestRunListener> listenerList = new ArrayList<ITestRunListener>();
        listenerList.addAll(Arrays.asList(listeners));
        return runInstrumentationTests(runner, listenerList);
    }

    /**
     * {@inheritDoc}
     */
    public String installPackage(final File packageFile, final boolean reinstall)
            throws DeviceNotAvailableException {
        // use array to store response, so it can be returned to caller
        final String[] response = new String[1];
        DeviceAction installAction = new DeviceAction() {
            public boolean run() throws InstallException {
                String result = getIDevice().installPackage(packageFile.getAbsolutePath(),
                        reinstall);
                response[0] = result;
                return result == null;
            }
        };
        performDeviceAction(String.format("install %s", packageFile.getAbsolutePath()),
                installAction, MAX_RETRY_ATTEMPTS);
        return response[0];
    }

    /**
     * {@inheritDoc}
     */
    public String uninstallPackage(final String packageName) throws DeviceNotAvailableException {
        // use array to store response, so it can be returned to caller
        final String[] response = new String[1];
        DeviceAction uninstallAction = new DeviceAction() {
            public boolean run() throws InstallException {
                String result = getIDevice().uninstallPackage(packageName);
                response[0] = result;
                return result == null;
            }
        };
        performDeviceAction(String.format("uninstall %s", packageName), uninstallAction,
                MAX_RETRY_ATTEMPTS);
        return response[0];
    }

    /**
     * {@inheritDoc}
     */
    public boolean pullFile(final String remoteFilePath, final File localFile)
            throws DeviceNotAvailableException {

        DeviceAction pullAction = new DeviceAction() {
            public boolean run() throws TimeoutException, IOException, AdbCommandRejectedException,
                    SyncException {
                SyncService syncService = null;
                boolean status = false;
                try {
                    syncService = getIDevice().getSyncService();
                    syncService.pullFile(remoteFilePath,
                            localFile.getAbsolutePath(), SyncService.getNullProgressMonitor());
                    status = true;
                } catch (SyncException e) {
                    CLog.w("Failed to pull %s from %s to %s. Message %s", remoteFilePath,
                            getSerialNumber(), localFile.getAbsolutePath(), e.getMessage());
                    throw e;
                } finally {
                    if (syncService != null) {
                        syncService.close();
                    }
                }
                return status;
            }
        };
        return performDeviceAction(String.format("pull %s to %s", remoteFilePath,
                localFile.getAbsolutePath()), pullAction, MAX_RETRY_ATTEMPTS);
    }

    /**
     * {@inheridDoc}
     */
    public File pullFile(String remoteFilePath) throws DeviceNotAvailableException {
        try {
            File localFile = FileUtil.createTempFileForRemote(remoteFilePath, null);
            if (pullFile(remoteFilePath, localFile)) {
                return localFile;
            }
        } catch (IOException e) {
            Log.w(LOG_TAG, String.format("Encountered IOException while trying to pull '%s': %s",
                    remoteFilePath, e));
        }
        return null;
    }

    /**
     * {@inheridDoc}
     */
    public File pullFileFromExternal(String remoteFilePath) throws DeviceNotAvailableException {
        String externalPath = getMountPoint(IDevice.MNT_EXTERNAL_STORAGE);
        String fullPath = (new File(externalPath, remoteFilePath)).getPath();
        return pullFile(fullPath);
    }

    /**
     * {@inheritDoc}
     */
    public boolean pushFile(final File localFile, final String remoteFilePath)
            throws DeviceNotAvailableException {
        DeviceAction pushAction = new DeviceAction() {
            public boolean run() throws TimeoutException, IOException, AdbCommandRejectedException,
                    SyncException {
                SyncService syncService = null;
                boolean status = false;
                try {
                    syncService = getIDevice().getSyncService();
                    syncService.pushFile(localFile.getAbsolutePath(),
                        remoteFilePath, SyncService.getNullProgressMonitor());
                    status = true;
                } catch (SyncException e) {
                    CLog.w("Failed to push %s to %s on device %s. Message %s",
                           localFile.getAbsolutePath(), remoteFilePath, getSerialNumber(),
                           e.getMessage());
                    throw e;
                } finally {
                    if (syncService != null) {
                        syncService.close();
                    }
                }
                return status;
            }
        };
        return performDeviceAction(String.format("push %s to %s", localFile.getAbsolutePath(),
                remoteFilePath), pushAction, MAX_RETRY_ATTEMPTS);
    }

    /**
     * {@inheritDoc}
     */
    public boolean pushString(final String contents, final String remoteFilePath)
            throws DeviceNotAvailableException {
        File tmpFile = null;
        try {
            tmpFile = FileUtil.createTempFile("temp", ".txt");
            FileUtil.writeToFile(contents, tmpFile);
            return pushFile(tmpFile, remoteFilePath);
        } catch (IOException e) {
            Log.e(LOG_TAG, e);
            return false;
        } finally {
            if (tmpFile != null) {
                tmpFile.delete();
            }
        }
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
        Log.i(LOG_TAG, String.format("Checking free space for %s", getSerialNumber()));
        String externalStorePath = getMountPoint(IDevice.MNT_EXTERNAL_STORAGE);
        String output = executeShellCommand(String.format("df %s", externalStorePath));
        Long available = parseFreeSpaceFromAvailable(output);
        if (available != null) {
            return available;
        }
        available = parseFreeSpaceFromFree(externalStorePath, output);
        if (available != null) {
            return available;
        }

        Log.e(LOG_TAG, String.format(
                "free space command output \"%s\" did not match expected patterns", output));
        return 0;
    }

    /**
     * Parses a partitions available space from the legacy output of a 'df' command.
     * <p/>
     * Assumes output format of:
     * <br>/
     * <code>
     * [partition]: 15659168K total, 51584K used, 15607584K available (block size 32768)
     * </code>
     * @param dfOutput the output of df command to parse
     * @return the available space in kilobytes or <code>null</code> if output could not be parsed
     */
    private Long parseFreeSpaceFromAvailable(String dfOutput) {
        final Pattern freeSpacePattern = Pattern.compile("(\\d+)K available");
        Matcher patternMatcher = freeSpacePattern.matcher(dfOutput);
        if (patternMatcher.find()) {
            String freeSpaceString = patternMatcher.group(1);
            try {
                return Long.parseLong(freeSpaceString);
            } catch (NumberFormatException e) {
                // fall through
            }
        }
        return null;
    }

    /**
     * Parses a partitions available space from the 'table-formatted' output of a 'df' command.
     * <p/>
     * Assumes output format of:
     * <br/>
     * <code>
     * Filesystem             Size   Used   Free   Blksize
     * <br/>
     * [partition]:              3G   790M  2G     4096
     * </code>
     * @param dfOutput the output of df command to parse
     * @return the available space in kilobytes or <code>null</code> if output could not be parsed
     */
    private Long parseFreeSpaceFromFree(String externalStorePath, String dfOutput) {
        Long freeSpace = null;
        final Pattern freeSpaceTablePattern = Pattern.compile(String.format(
                //fs   Size         Used         Free
                "%s\\s+[\\w\\d]+\\s+[\\w\\d]+\\s+(\\d+)(\\w)", externalStorePath));
        Matcher tablePatternMatcher = freeSpaceTablePattern.matcher(dfOutput);
        if (tablePatternMatcher.find()) {
            String numericValueString = tablePatternMatcher.group(1);
            String unitType = tablePatternMatcher.group(2);
            try {
                freeSpace = Long.parseLong(numericValueString);
                if (unitType.equals("M")) {
                    freeSpace = freeSpace * 1024;
                } else if (unitType.equals("G")) {
                    freeSpace = freeSpace * 1024 * 1024;
                }
            } catch (NumberFormatException e) {
                // fall through
            }
        }
        return freeSpace;
    }

    /**
     * {@inheritDoc}
     */
    public String getMountPoint(String mountName) {
        return mMonitor.getMountPoint(mountName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IFileEntry getFileEntry(String path) throws DeviceNotAvailableException {
        String[] pathComponents = path.split(FileListingService.FILE_SEPARATOR);
        if (mRootFile == null) {
            FileListingService service = getIDevice().getFileListingService();
            mRootFile = new FileEntryWrapper(this, service.getRoot());
        }
        return FileEntryWrapper.getDescendant(mRootFile, Arrays.asList(pathComponents));
    }

    /**
     * {@inheritDoc}
     */
    public boolean syncFiles(File localFileDir, String deviceFilePath)
            throws DeviceNotAvailableException {
        Log.i(LOG_TAG, String.format("Syncing %s to %s on device %s",
                localFileDir.getAbsolutePath(), deviceFilePath, getSerialNumber()));
        if (!localFileDir.isDirectory()) {
            Log.e(LOG_TAG, String.format("file %s is not a directory",
                    localFileDir.getAbsolutePath()));
            return false;
        }
        // get the real destination path. This is done because underlying syncService.push
        // implementation will add localFileDir.getName() to destination path
        deviceFilePath = String.format("%s/%s", deviceFilePath, localFileDir.getName());
        if (!doesFileExist(deviceFilePath)) {
            executeShellCommand(String.format("mkdir %s", deviceFilePath));
        }
        IFileEntry remoteFileEntry = getFileEntry(deviceFilePath);
        if (remoteFileEntry == null) {
            Log.e(LOG_TAG, String.format("Could not find remote file entry %s ", deviceFilePath));
            return false;
        }

        return syncFiles(localFileDir, remoteFileEntry);
    }

    /**
     * Recursively sync newer files.
     *
     * @param localFileDir the local {@link File} directory to sync
     * @param remoteFileEntry the remote destination {@link IFileEntry}
     * @return <code>true</code> if files were synced successfully
     * @throws DeviceNotAvailableException
     */
    private boolean syncFiles(File localFileDir, final IFileEntry remoteFileEntry)
            throws DeviceNotAvailableException {
        Log.d(LOG_TAG, String.format("Syncing %s to %s on %s", localFileDir.getAbsolutePath(),
                remoteFileEntry.getFullPath(), getSerialNumber()));
        // find newer files to sync
        File[] localFiles = localFileDir.listFiles(new NoHiddenFilesFilter());
        ArrayList<String> filePathsToSync = new ArrayList<String>();
        for (File localFile : localFiles) {
            IFileEntry entry = remoteFileEntry.findChild(localFile.getName());
            if (entry == null) {
                Log.d(LOG_TAG, String.format("Detected missing file path %s",
                        localFile.getAbsolutePath()));
                filePathsToSync.add(localFile.getAbsolutePath());
            } else if (localFile.isDirectory()) {
                // This directory exists remotely. recursively sync it to sync only its newer files
                // contents
                if (!syncFiles(localFile, entry)) {
                    return false;
                }
            } else if (isNewer(localFile, entry)) {
                Log.d(LOG_TAG, String.format("Detected newer file %s",
                        localFile.getAbsolutePath()));
                filePathsToSync.add(localFile.getAbsolutePath());
            }
        }

        if (filePathsToSync.size() == 0) {
            Log.d(LOG_TAG, "No files to sync");
            return true;
        }
        final String files[] = filePathsToSync.toArray(new String[filePathsToSync.size()]);
        DeviceAction syncAction = new DeviceAction() {
            public boolean run() throws TimeoutException, IOException, AdbCommandRejectedException,
                    SyncException {
                SyncService syncService = null;
                boolean status = false;
                try {
                    syncService = getIDevice().getSyncService();
                    syncService.push(files, remoteFileEntry.getFileEntry(),
                            SyncService.getNullProgressMonitor());
                    status = true;
                } catch (SyncException e) {
                    Log.w(LOG_TAG, String.format(
                            "Failed to sync files to %s on device %s. Message %s",
                            remoteFileEntry.getFullPath(), getSerialNumber(), e.getMessage()));
                    throw e;
                } finally {
                    if (syncService != null) {
                        syncService.close();
                    }
                }
                return status;
            }
        };
        return performDeviceAction(String.format("sync files %s", remoteFileEntry.getFullPath()),
                syncAction, MAX_RETRY_ATTEMPTS);
    }

    /**
     * Queries the file listing service for a given directory
     *
     * @param remoteFileEntry
     * @param service
     * @throws DeviceNotAvailableException
     */
     FileEntry[] getFileChildren(final FileEntry remoteFileEntry)
            throws DeviceNotAvailableException {
        // time this operation because its known to hang
        FileQueryAction action = new FileQueryAction(remoteFileEntry,
                getIDevice().getFileListingService());
        performDeviceAction("buildFileCache", action, MAX_RETRY_ATTEMPTS);
        return action.mFileContents;
    }

    private class FileQueryAction implements DeviceAction {

        FileEntry[] mFileContents = null;
        private final FileEntry mRemoteFileEntry;
        private final FileListingService mService;

        FileQueryAction(FileEntry remoteFileEntry, FileListingService service) {
            mRemoteFileEntry = remoteFileEntry;
            mService = service;
        }

        public boolean run() throws TimeoutException, IOException {
            mFileContents = mService.getChildren(mRemoteFileEntry, false, null);
            return true;
        }
    }

    /**
     * A {@link FilenameFilter} that rejects hidden (ie starts with ".") files.
     */
    private static class NoHiddenFilesFilter implements FilenameFilter {
        /**
         * {@inheritDoc}
         */
        public boolean accept(File dir, String name) {
            return !name.startsWith(".");
        }
    }

    /**
     * Return <code>true</code> if local file is newer than remote file.
     */
    private boolean isNewer(File localFile, IFileEntry entry) {
        // remote times are in GMT timezone
        final String entryTimeString = String.format("%s %s GMT", entry.getDate(), entry.getTime());
        try {
            // expected format of a FileEntry's date and time
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm zzz");
            Date remoteDate = format.parse(entryTimeString);
            // localFile.lastModified has granularity of ms, but remoteDate.getTime only has
            // granularity of minutes. Shift remoteDate.getTime() backward by one minute so newly
            // modified files get synced
            return localFile.lastModified() > (remoteDate.getTime() - 60 * 1000);
        } catch (ParseException e) {
            Log.e(LOG_TAG, String.format(
                    "Error converting remote time stamp %s for %s on device %s", entryTimeString,
                    entry.getFullPath(), getSerialNumber()));
        }
        // sync file by default
        return true;
    }

    /**
     * {@inheritDoc}
     */
    public String executeAdbCommand(String... cmdArgs) throws DeviceNotAvailableException {
        final String[] fullCmd = buildAdbCommand(cmdArgs);
        AdbAction adbAction = new AdbAction(fullCmd);
        performDeviceAction(String.format("adb %s", cmdArgs[0]), adbAction, MAX_RETRY_ATTEMPTS);
        return adbAction.mOutput;
    }

    /**
     * {@inheritDoc}
     */
    public CommandResult executeFastbootCommand(String... cmdArgs)
            throws DeviceNotAvailableException, UnsupportedOperationException {
        return doFastbootCommand(getCommandTimeout(), cmdArgs);
    }

    /**
     * {@inheritDoc}
     */
    public CommandResult executeLongFastbootCommand(String... cmdArgs)
            throws DeviceNotAvailableException, UnsupportedOperationException {
        return doFastbootCommand(getLongCommandTimeout(), cmdArgs);
    }

    /**
     * @param cmdArgs
     * @throws DeviceNotAvailableException
     */
    private CommandResult doFastbootCommand(final long timeout, String... cmdArgs)
            throws DeviceNotAvailableException, UnsupportedOperationException {
        if (!mFastbootEnabled) {
            throw new UnsupportedOperationException(String.format(
                    "Attempted to fastboot on device %s , but fastboot is not available. Aborting.",
                    getSerialNumber()));
        }
        final String[] fullCmd = buildFastbootCommand(cmdArgs);
        for (int i = 0; i < MAX_RETRY_ATTEMPTS; i++) {
            // block state changes while executing a fastboot command, since
            // device will disappear from fastboot devices while command is being executed
            try {
                mFastbootLock.acquire();
            } catch (InterruptedException e) {
                // ignore
            }
            CommandResult result = getRunUtil().runTimedCmd(timeout, fullCmd);
            mFastbootLock.release();
            if (!isRecoveryNeeded(result)) {
                return result;
            }
            recoverDeviceFromBootloader();
        }
        throw new DeviceUnresponsiveException(String.format("Attempted fastboot %s multiple "
                + "times on device %s without communication success. Aborting.", cmdArgs[0],
                getSerialNumber()));
    }

    /**
     * Evaluate the given fastboot result to determine if recovery mode needs to be entered
     *
     * @param fastbootResult the {@link CommandResult} from a fastboot command
     * @return <code>true</code> if recovery mode should be entered, <code>false</code> otherwise.
     */
    private boolean isRecoveryNeeded(CommandResult fastbootResult) {
        if (fastbootResult.getStatus().equals(CommandStatus.TIMED_OUT)) {
            // fastboot commands always time out if devices is not present
            return true;
        } else {
            // check for specific error messages in result that indicate bad device communication
            // and recovery mode is needed
            if (fastbootResult.getStderr() == null ||
                fastbootResult.getStderr().contains("data transfer failure (Protocol error)") ||
                fastbootResult.getStderr().contains("status read failed (No such device)")) {
                Log.w(LOG_TAG, String.format(
                        "Bad fastboot response from device %s. stderr: %s. Entering recovery",
                        getSerialNumber(), fastbootResult.getStderr()));
                return true;
            }
        }
        return false;
    }

    /**
     * Get the max time allowed in ms for commands.
     */
    int getCommandTimeout() {
        return mCmdTimeout;
    }

    /**
     * Set the max time allowed in ms for commands.
     */
    void setLongCommandTimeout(long timeout) {
        mLongCmdTimeout = timeout;
    }

    /**
     * Get the max time allowed in ms for commands.
     */
    long getLongCommandTimeout() {
        return mLongCmdTimeout;
    }

    /**
     * Set the max time allowed in ms for commands.
     */
    void setCommandTimeout(int timeout) {
        mCmdTimeout = timeout;
    }

    /**
     * Builds the OS command for the given adb command and args
     */
    private String[] buildAdbCommand(String... commandArgs) {
        return ArrayUtil.buildArray(new String[] {"adb", "-s", getSerialNumber()},
                commandArgs);
    }

    /**
     * Builds the OS command for the given fastboot command and args
     */
    private String[] buildFastbootCommand(String... commandArgs) {
        return ArrayUtil.buildArray(new String[] {"fastboot", "-s", getSerialNumber()},
                commandArgs);
    }

    /**
     * Performs an action on this device. Attempts to recover device and optionally retry command
     * if action fails.
     *
     * @param actionDescription a short description of action to be performed. Used for logging
     *            purposes only.
     * @param action the action to be performed
     * @param callback optional action to perform if action fails but recovery succeeds. If no post
     *            recovery action needs to be taken pass in <code>null</code>
     * @param retryAttempts the retry attempts to make for action if it fails but
     *            recovery succeeds
     * @returns <code>true</code> if action was performed successfully
     * @throws DeviceNotAvailableException if recovery attempt fails or max attempts done without
     *             success
     */
    private boolean performDeviceAction(String actionDescription, final DeviceAction action,
            int retryAttempts) throws DeviceNotAvailableException {

        for (int i=0; i < retryAttempts +1; i++) {
            try {
                return action.run();
            } catch (TimeoutException e) {
                logDeviceActionException(actionDescription, e);
            } catch (IOException e) {
                logDeviceActionException(actionDescription, e);
            } catch (InstallException e) {
                logDeviceActionException(actionDescription, e);
            } catch (SyncException e) {
                logDeviceActionException(actionDescription, e);
                // a SyncException is not necessarily a device communication problem
                // do additional diagnosis
                if (!e.getErrorCode().equals(SyncError.BUFFER_OVERRUN) &&
                        !e.getErrorCode().equals(SyncError.TRANSFER_PROTOCOL_ERROR)) {
                    // this is a logic problem, doesn't need recovery or to be retried
                    return false;
                }
            } catch (AdbCommandRejectedException e) {
                logDeviceActionException(actionDescription, e);
            } catch (ShellCommandUnresponsiveException e) {
                CLog.w("Device %s stopped responding when attempting %s", getSerialNumber(),
                        actionDescription);
            }
            // TODO: currently treat all exceptions the same. In future consider different recovery
            // mechanisms for time out's vs IOExceptions
            recoverDevice();
        }
        if (retryAttempts > 0) {
            throw new DeviceUnresponsiveException(String.format("Attempted %s multiple times "
                    + "on device %s without communication success. Aborting.", actionDescription,
                    getSerialNumber()));
        }
        return false;
    }

    /**
     * Log an entry for given exception
     *
     * @param actionDescription the action's description
     * @param e the exception
     */
    private void logDeviceActionException(String actionDescription, Exception e) {
        CLog.w("%s (%s) when attempting %s on device %s", e.getClass().getSimpleName(),
                getExceptionMessage(e), actionDescription, getSerialNumber());
    }

    /**
     * Make a best effort attempt to retrieve a meaningful short descriptive message for given
     * {@link Exception}
     *
     * @param e the {@link Exception}
     * @return a short message
     */
    private String getExceptionMessage(Exception e) {
        StringBuilder msgBuilder = new StringBuilder();
        if (e.getMessage() != null) {
            msgBuilder.append(e.getMessage());
        }
        if (e.getCause() != null) {
            msgBuilder.append(" cause: ");
            msgBuilder.append(e.getCause().getClass().getSimpleName());
            if (e.getCause().getMessage() != null) {
                msgBuilder.append(" (");
                msgBuilder.append(e.getCause().getMessage());
                msgBuilder.append(")");
            }
        }
        return msgBuilder.toString();
    }

    /**
     * Attempts to recover device communication.
     *
     * @throws DeviceNotAvailableException if device is not longer available
     */
    public void recoverDevice() throws DeviceNotAvailableException {
        if (mRecoveryMode.equals(RecoveryMode.NONE)) {
            CLog.i("Skipping recovery on %s", getSerialNumber());
            getRunUtil().sleep(NONE_RECOVERY_MODE_DELAY);
            return;
        }
        CLog.i("Attempting recovery on %s", getSerialNumber());
        mRecovery.recoverDevice(mMonitor, mRecoveryMode.equals(RecoveryMode.ONLINE));
        if (mRecoveryMode.equals(RecoveryMode.AVAILABLE)) {
            // turn off recovery mode to prevent reentrant recovery
            // TODO: look for a better way to handle this, such as doing postBootUp steps in
            // recovery itself
            mRecoveryMode = RecoveryMode.NONE;
            // this might be a runtime reset - still need to run post boot setup steps
            postBootSetup();
            mRecoveryMode = RecoveryMode.AVAILABLE;
        }
        CLog.i("Recovery successful for %s", getSerialNumber());
    }

    /**
     * Attempts to recover device fastboot communication.
     *
     * @throws DeviceNotAvailableException if device is not longer available
     */
    private void recoverDeviceFromBootloader() throws DeviceNotAvailableException {
        Log.i(LOG_TAG, String.format("Attempting recovery on %s in bootloader", getSerialNumber()));
        mRecovery.recoverDeviceBootloader(mMonitor);
        Log.i(LOG_TAG, String.format("Bootloader recovery successful for %s", getSerialNumber()));
    }

    private void recoverDeviceInRecovery() throws DeviceNotAvailableException {
        Log.i(LOG_TAG, String.format("Attempting recovery on %s in recovery", getSerialNumber()));
        mRecovery.recoverDeviceRecovery(mMonitor);
        Log.i(LOG_TAG,
                String.format("Recovery mode recovery successful for %s", getSerialNumber()));
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
     */
    @Override
    public void clearLogcat() {
        if (mLogcatReceiver != null) {
            mLogcatReceiver.clear();
        }
    }

    /**
     * {@inheritDoc}
     * <p/>
     * Works in two modes:
     * <li>If the logcat is currently being captured in the background (i.e. the manager of this
     * device is calling startLogcat and stopLogcat as appropriate), will return the current
     * contents of the background logcat capture.
     * <li>Otherwise, will return a static dump of the logcat data if device is currently responding
     */
    public InputStreamSource getLogcat() {
        if (mLogcatReceiver == null) {
            Log.w(LOG_TAG, String.format("Not capturing logcat for %s in background, " +
                    "returning a logcat dump", getSerialNumber()));
            return getLogcatDump();
        } else {
            return mLogcatReceiver.getLogcatData();
        }
    }

    /**
     * Get a dump of the current logcat for device.
     *
     * @return a {@link InputStream} of the logcat data. An empty stream is returned if fail to
     *         capture logcat data.
     */
    private InputStreamSource getLogcatDump() {
        byte[] output = new byte[0];
        try {
            // use IDevice directly because we don't want callers to handle
            // DeviceNotAvailableException for this method
            CollectingByteOutputReceiver receiver = new CollectingByteOutputReceiver();
            // add -d parameter to make this a non blocking call
            getIDevice().executeShellCommand(LOGCAT_CMD + " -d", receiver);
            output = receiver.getOutput();
        } catch (IOException e) {
            Log.w(LOG_TAG, String.format("Failed to get logcat dump from %s: ", getSerialNumber(),
                    e.getMessage()));
        } catch (TimeoutException e) {
            Log.w(LOG_TAG, String.format("Failed to get logcat dump from %s: timeout",
                    getSerialNumber()));
        } catch (AdbCommandRejectedException e) {
            Log.w(LOG_TAG, String.format("Failed to get logcat dump from %s: ", getSerialNumber(),
                    e.getMessage()));
        } catch (ShellCommandUnresponsiveException e) {
            Log.w(LOG_TAG, String.format("Failed to get logcat dump from %s: ", getSerialNumber(),
                    e.getMessage()));
        }
        return new ByteArrayInputStreamSource(output);
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
     * Factory method to create a {@link LogCatReceiver}.
     * <p/>
     * Exposed for unit testing.
     */
    LogCatReceiver createLogcatReceiver() {
        return new LogCatReceiver();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public InputStreamSource getBugreport() {
        CollectingByteOutputReceiver receiver = new CollectingByteOutputReceiver();
        try {
            executeShellCommand(BUGREPORT_CMD, receiver, BUGREPORT_TIMEOUT, 0 /* don't retry */);
        } catch (DeviceNotAvailableException e) {
            // Log, but don't throw, so the caller can get the bugreport contents even if the device
            // goes away
            Log.e(LOG_TAG, String.format("Device %s became unresponsive while retrieving bugreport",
                    getSerialNumber()));
        }

        return new ByteArrayInputStreamSource(receiver.getOutput());
    }

    /**
     * A background thread that captures logcat data into a temporary host file.
     * <p/>
     * This is done so:
     * <li>if device goes permanently offline during a test, the log data is retained.
     * <li>to capture more data than may fit in device's circular log.
     * <p/>
     * The maximum size of the tmp file is limited to approximately mMaxLogcatFileSize.
     * To prevent data loss when the limit has been reached, this file keeps two tmp host
     * files.
     */
    class LogCatReceiver extends Thread implements IShellOutputReceiver {

        private boolean mIsCancelled = false;
        private OutputStream mOutStream;
        /** the archived previous tmp file */
        private File mPreviousTmpFile = null;
        /** the current temp file which logcat data will be streamed into */
        private File mTmpFile = null;
        private long mTmpBytesStored = 0;

        /**
         * {@inheritDoc}
         */
        @Override
        public synchronized void addOutput(byte[] data, int offset, int length) {
            if (mIsCancelled || mOutStream == null) {
                return;
            }
            try {
                mOutStream.write(data, offset, length);
                mTmpBytesStored += length;
                if (mTmpBytesStored > mMaxLogcatFileSize) {
                    Log.i(LOG_TAG, String.format(
                            "Max tmp logcat file size reached for %s, swapping",
                            getSerialNumber()));
                    createTmpFile();
                    mTmpBytesStored = 0;
                }
            } catch (IOException e) {
                Log.w(LOG_TAG, String.format("failed to write logcat data for %s.",
                        getSerialNumber()));
            }
        }

        public synchronized InputStreamSource getLogcatData() {
            if (mTmpFile != null) {
                flush();
                try {
                    FileInputStream fileStream = new FileInputStream(mTmpFile);
                    if (mPreviousTmpFile != null) {
                        // return an input stream that first reads from mPreviousTmpFile, then reads
                        // from mTmpFile
                        InputStream stream = new SequenceInputStream(
                                new FileInputStream(mPreviousTmpFile), fileStream);
                        return new SnapshotInputStreamSource(stream);
                    } else {
                        // no previous file, just return a wrapper around mTmpFile's stream
                        return new SnapshotInputStreamSource(fileStream);
                    }
                } catch (IOException e) {
                    Log.e(LOG_TAG,
                            String.format("failed to get logcat data for %s.", getSerialNumber()));
                    Log.e(LOG_TAG, e);
                }
            }

            // return an empty InputStreamSource
            return new ByteArrayInputStreamSource(new byte[0]);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public synchronized void flush() {
            if (mOutStream == null) {
                return;
            }
            try {
                mOutStream.flush();
            } catch (IOException e) {
                Log.w(LOG_TAG, String.format("failed to flush logcat data for %s.",
                        getSerialNumber()));
            }
        }

        /**
         * Delete currently accumulated logcat data, and then re-create a new log file.
         */
        public synchronized void clear() {
            delete();

            try {
                createTmpFile();
            }  catch (IOException e) {
                CLog.w("failed to create logcat file for %s.",
                        getSerialNumber());
            }
        }

        /**
         * Delete all accumulated logcat data.
         */
        private void delete() {
            flush();
            closeLogStream();

            FileUtil.deleteFile(mTmpFile);
            mTmpFile = null;
            FileUtil.deleteFile(mPreviousTmpFile);
            mPreviousTmpFile = null;
            mTmpBytesStored = 0;
        }

        public synchronized void cancel() {
            mIsCancelled = true;
            interrupt();
            delete();
        }

        /**
         * Closes the stream to tmp log file
         */
        private void closeLogStream() {
            try {
                if (mOutStream != null) {
                    mOutStream.flush();
                    mOutStream.close();
                    mOutStream = null;
                }

            } catch (IOException e) {
                Log.w(LOG_TAG, String.format("failed to close logcat stream for %s.",
                        getSerialNumber()));
            }
        }

        /**
         * {@inheritDoc}
         */
        public synchronized boolean isCancelled() {
            return mIsCancelled;
        }

        @Override
        public void run() {
            try {
                createTmpFile();
            } catch (IOException e) {
                Log.e(LOG_TAG, String.format("failed to create tmp logcat file for %s.",
                        getSerialNumber()));
                Log.e(LOG_TAG, e);
                return;
            }

            // continually run in a loop attempting to grab logcat data, skipping recovery
            // this is done so logcat data can continue to be captured even if device goes away and
            // then comes back online
            while (!isCancelled()) {
                try {
                    // FIXME: Disgusting hack alert! Sleep for a small amount before starting
                    // logcat, as starting logcat immediately after a device comes online has caused
                    // adb instability
                    if (mLogStartDelay > 0) {
                        Log.d(LOG_TAG, String.format("Sleep for %d before starting logcat for %s.",
                                mLogStartDelay, getSerialNumber()));
                        getRunUtil().sleep(mLogStartDelay);
                    }
                    Log.d(LOG_TAG, String.format("Starting logcat for %s.", getSerialNumber()));
                    getIDevice().executeShellCommand(LOGCAT_CMD, this, 0);
                } catch (Exception e) {
                    final String msg = String.format("logcat capture interrupted for %s. Waiting"
                            + " for device to be back online. May see duplicate content in log.",
                            getSerialNumber());
                    Log.d(LOG_TAG, msg);
                    appendDeviceLogMsg(msg);
                    // sleep a small amount for device to settle
                    getRunUtil().sleep(5 * 1000);
                    // wait a long time for device to be online
                    mMonitor.waitForDeviceOnline(10 * 60 * 1000);
                }
            }
        }

        /**
         * Creates a new tmp file, closing the old one as necessary
         * @throws IOException
         * @throws FileNotFoundException
         */
        private synchronized void createTmpFile() throws IOException, FileNotFoundException {
            if (mIsCancelled) {
                Log.w(LOG_TAG, String.format(
                        "Attempted to createTmpFile() after cancel() for device %s, ignoring.",
                        getSerialNumber()));
                return;
            }

            closeLogStream();
            if (mPreviousTmpFile != null) {
                mPreviousTmpFile.delete();
            }
            mPreviousTmpFile = mTmpFile;
            mTmpFile = FileUtil.createTempFile(String.format("logcat_%s_", getSerialNumber()),
                    ".txt");
            Log.i(LOG_TAG, String.format("Created tmp logcat file %s",
                    mTmpFile.getAbsolutePath()));
            mOutStream = new BufferedOutputStream(new FileOutputStream(mTmpFile),
                    LOGCAT_BUFF_SIZE);
            // add an initial message to log, to give info to viewer
            if (mPreviousTmpFile == null) {
                // first log!
                appendDeviceLogMsg(String.format("Logcat for device %s running system build %s",
                        getSerialNumber(), getBuildId()));
            } else {
                appendDeviceLogMsg(String.format(
                        "Continuing logcat capture for device %s running system build %s. " +
                        "Previous content may have been truncated.", getSerialNumber(),
                        getBuildId()));
            }
        }

        /**
         * Adds a message to the captured device log.
         *
         * @param msg
         */
        private synchronized void appendDeviceLogMsg(String msg) {
            if (mOutStream == null) {
                return;
            }
            // add the msg to device tmp log, so readers will know logcat was interrupted
            try {
                mOutStream.write("\n*******************\n".getBytes());
                mOutStream.write(msg.getBytes());
                mOutStream.write("\n*******************\n".getBytes());
            } catch (IOException e) {
                Log.w(LOG_TAG, String.format("failed to write logcat data for %s.",
                        getSerialNumber()));
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public InputStreamSource getScreenshot() throws DeviceNotAvailableException {
        ScreenshotAction action = new ScreenshotAction();
        if (performDeviceAction("screenshot", action, MAX_RETRY_ATTEMPTS)) {
            byte[] pngData = compressRawImageAsPng(action.mRawScreenshot);
            if (pngData != null) {
                return new ByteArrayInputStreamSource(pngData);
            }
        }
        return null;
    }

    private class ScreenshotAction implements DeviceAction {

        RawImage mRawScreenshot;

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean run() throws IOException, TimeoutException, AdbCommandRejectedException,
                ShellCommandUnresponsiveException, InstallException, SyncException {
            mRawScreenshot = getIDevice().getScreenshot();
            return mRawScreenshot != null;
        }
    }

    private byte[] compressRawImageAsPng(RawImage rawImage) {
        BufferedImage image = new BufferedImage(rawImage.width, rawImage.height,
                BufferedImage.TYPE_INT_ARGB);

        // borrowed conversion logic from platform/sdk/screenshot/.../Screenshot.java
        int index = 0;
        int IndexInc = rawImage.bpp >> 3;
        for (int y = 0 ; y < rawImage.height ; y++) {
            for (int x = 0 ; x < rawImage.width ; x++) {
                int value = rawImage.getARGB(index);
                index += IndexInc;
                image.setRGB(x, y, value);
            }
        }
        // store compressed image in memory, and let callers write to persistent storage
        // use initial buffer size of 128K
        byte[] pngData = null;
        ByteArrayOutputStream imageOut = new ByteArrayOutputStream(128*1024);
        try {
            if (ImageIO.write(image, "png", imageOut)) {
                pngData = imageOut.toByteArray();
            } else {
                CLog.e("Failed to compress screenshot to png");
            }
        } catch (IOException e) {
            CLog.e("Failed to compress screenshot to png");
            CLog.e(e);
        }
        StreamUtil.closeStream(imageOut);
        return pngData;
    }

    /**
     * {@inheritDoc}
     */
    public boolean connectToWifiNetwork(String wifiSsid, String wifiPsk)
            throws DeviceNotAvailableException {
        Log.i(LOG_TAG, String.format("Connecting to wifi network %s on %s", wifiSsid,
                getSerialNumber()));
        IWifiHelper wifi = createWifiHelper();
        wifi.enableWifi();
        // TODO: return false here if failed?
        wifi.waitForWifiState(WifiState.SCANNING, WifiState.COMPLETED);

        Integer networkId = null;
        if (wifiPsk != null) {
            networkId = wifi.addWpaPskNetwork(wifiSsid, wifiPsk);
        } else {
            networkId = wifi.addOpenNetwork(wifiSsid);
        }

        if (networkId == null) {
            Log.e(LOG_TAG, String.format("Failed to add wifi network %s on %s", wifiSsid,
                    getSerialNumber()));
            return false;
        }
        if (!wifi.associateNetwork(networkId)) {
            Log.e(LOG_TAG, String.format("Failed to enable wifi network %s on %s", wifiSsid,
                    getSerialNumber()));
            return false;
        }
        if (!wifi.waitForWifiState(WifiState.COMPLETED)) {
            Log.e(LOG_TAG, String.format("wifi network %s failed to associate on %s", wifiSsid,
                    getSerialNumber()));
            return false;
        }
        // TODO: make timeout configurable
        if (!wifi.waitForIp(30 * 1000)) {
            Log.e(LOG_TAG, String.format("dhcp timeout when connecting to wifi network %s on %s",
                    wifiSsid, getSerialNumber()));
            return false;
        }
        // wait for ping success
        for (int i = 0; i < 10; i++) {
            String pingOutput = executeShellCommand("ping -c 1 -w 5 www.google.com");
            if (pingOutput.contains("1 packets transmitted, 1 received")) {
                return true;
            }
            getRunUtil().sleep(1 * 1000);
        }
        Log.e(LOG_TAG, String.format("ping unsuccessful after connecting to wifi network %s on %s",
                wifiSsid, getSerialNumber()));
        return false;
    }

    /**
     * {@inheritDoc}
     */
    public boolean disconnectFromWifi() throws DeviceNotAvailableException {
        IWifiHelper wifi = createWifiHelper();
        wifi.removeAllNetworks();
        wifi.disableWifi();
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getIpAddress() throws DeviceNotAvailableException {
        IWifiHelper wifi = createWifiHelper();
        return wifi.getIpAddress(null);
    }

    /**
     * Create a {@link WifiHelper} to use
     * <p/>
     * Exposed so unit tests can mock
     */
    IWifiHelper createWifiHelper() {
        return new WifiHelper(this);
    }

    /**
     * {@inheritDoc}
     */
    public boolean clearErrorDialogs() throws DeviceNotAvailableException {
        // attempt to clear error dialogs multiple times
        for (int i = 0; i < NUM_CLEAR_ATTEMPTS; i++) {
            int numErrorDialogs = getErrorDialogCount();
            if (numErrorDialogs == 0) {
                return true;
            }
            doClearDialogs(numErrorDialogs);
        }
        if (getErrorDialogCount() > 0) {
            // at this point, all attempts to clear error dialogs completely have failed
            // it might be the case that the process keeps showing new dialogs immediately after
            // clearing. There's really no workaround, but to dump an error
            Log.e(LOG_TAG, String.format("error dialogs still exist on %s.", getSerialNumber()));
            return false;
        }
        return true;
    }

    /**
     * Detects the number of crash or ANR dialogs currently displayed.
     * <p/>
     * Parses output of 'dump activity processes'
     *
     * @return count of dialogs displayed
     * @throws DeviceNotAvailableException
     */
    private int getErrorDialogCount() throws DeviceNotAvailableException {
        int errorDialogCount = 0;
        Pattern crashPattern = Pattern.compile(".*crashing=true.*AppErrorDialog.*");
        Pattern anrPattern = Pattern.compile(".*notResponding=true.*AppNotRespondingDialog.*");
        String systemStatusOutput = executeShellCommand("dumpsys activity processes");
        Matcher crashMatcher = crashPattern.matcher(systemStatusOutput);
        while (crashMatcher.find()) {
            errorDialogCount++;
        }
        Matcher anrMatcher = anrPattern.matcher(systemStatusOutput);
        while (anrMatcher.find()) {
            errorDialogCount++;
        }

        return errorDialogCount;
    }

    private void doClearDialogs(int numDialogs) throws DeviceNotAvailableException {
        Log.i(LOG_TAG, String.format("Attempted to clear %d dialogs on %s", numDialogs,
                getSerialNumber()));
        for (int i=0; i < numDialogs; i++) {
            // send DPAD_CENTER
            executeShellCommand(DISMISS_DIALOG_CMD);
        }
    }

    IDeviceStateMonitor getDeviceStateMonitor() {
        return mMonitor;
    }

    /**
     * {@inheritDoc}
     */
    public void postBootSetup() throws DeviceNotAvailableException  {
        if (mEnableAdbRoot) {
            enableAdbRoot();
        }
        if (mDisableKeyguard) {
            Log.i(LOG_TAG, String.format("Attempting to disable keyguard on %s using %s",
                    getSerialNumber(), mDisableKeyguardCmd));
            executeShellCommand(mDisableKeyguardCmd);
        }
    }

    /**
     * Gets the adb shell command to disable the keyguard for this device.
     * <p/>
     * Exposed for unit testing.
     */
    String getDisableKeyguardCmd() {
        return mDisableKeyguardCmd;
    }

    /**
     * {@inheritDoc}
     */
    public void rebootIntoBootloader()
            throws DeviceNotAvailableException, UnsupportedOperationException {
        if (!mFastbootEnabled) {
            throw new UnsupportedOperationException(
                    "Fastboot is not available and cannot reboot into bootloader");
        }
        CLog.i("Rebooting device %s in state %s into bootloader", getSerialNumber(),
                getDeviceState());
        if (TestDeviceState.FASTBOOT.equals(getDeviceState())) {
            Log.i(LOG_TAG, String.format("device %s already in fastboot. Rebooting anyway",
                    getSerialNumber()));
            executeFastbootCommand("reboot-bootloader");
        } else {
            Log.i(LOG_TAG, String.format("Booting device %s into bootloader", getSerialNumber()));
            doAdbRebootBootloader();
        }
        if (!mMonitor.waitForDeviceBootloader(FASTBOOT_TIMEOUT)) {
            recoverDeviceFromBootloader();
        }
    }

    private void doAdbRebootBootloader() throws DeviceNotAvailableException {
        try {
            getIDevice().reboot("bootloader");
            return;
        } catch (IOException e) {
            Log.w(LOG_TAG, String.format("IOException '%s' when rebooting %s into bootloader",
                    e.getMessage(), getSerialNumber()));
            recoverDeviceFromBootloader();
            // no need to try multiple times - if recoverDeviceFromBootloader() succeeds device is
            // successfully in bootloader mode

        } catch (TimeoutException e) {
            Log.w(LOG_TAG, String.format("TimeoutException when rebooting %s into bootloader",
                    getSerialNumber()));
            recoverDeviceFromBootloader();
            // no need to try multiple times - if recoverDeviceFromBootloader() succeeds device is
            // successfully in bootloader mode

        } catch (AdbCommandRejectedException e) {
            Log.w(LOG_TAG, String.format(
                    "AdbCommandRejectedException '%s' when rebooting %s into bootloader",
                    e.getMessage(), getSerialNumber()));
            recoverDeviceFromBootloader();
            // no need to try multiple times - if recoverDeviceFromBootloader() succeeds device is
            // successfully in bootloader mode

        }
    }

    /**
     * {@inheritDoc}
     */
    public void reboot() throws DeviceNotAvailableException {
        rebootUntilOnline();

        RecoveryMode cachedRecoveryMode = getRecoveryMode();
        setRecoveryMode(RecoveryMode.ONLINE);

        if (isEncryptionSupported() && isDeviceEncrypted()) {
            unlockDevice();
        }

        setRecoveryMode(cachedRecoveryMode);

        if (mMonitor.waitForDeviceAvailable(REBOOT_TIMEOUT) != null) {
            postBootSetup();
            return;
        } else {
            recoverDevice();
        }
    }

    /**
     * {@inheritDoc}
     */
    public void rebootUntilOnline() throws DeviceNotAvailableException {
        doReboot();
        RecoveryMode cachedRecoveryMode = getRecoveryMode();
        setRecoveryMode(RecoveryMode.ONLINE);
        if (mMonitor.waitForDeviceOnline() != null) {
            if (mEnableAdbRoot) {
                enableAdbRoot();
            }
        } else {
            recoverDevice();
        }
        setRecoveryMode(cachedRecoveryMode);
    }

    /**
     * {@inheritDoc}
     */
    public void rebootIntoRecovery() throws DeviceNotAvailableException {
        if (TestDeviceState.FASTBOOT == getDeviceState()) {
            Log.w(LOG_TAG, String.format(
                    "device %s in fastboot when requesting boot to recovery. " +
                    "Rebooting to userspace first.", getSerialNumber()));
            rebootUntilOnline();
        }
        doAdbReboot("recovery");
        if (!waitForDeviceInRecovery(ADB_RECOVERY_TIMEOUT)) {
            recoverDeviceInRecovery();
        }
    }

    /**
     * {@inheritDoc}
     */
    public void nonBlockingReboot() throws DeviceNotAvailableException {
        doReboot();
    }

    /**
     * Exposed for unit testing.
     *
     * @throws DeviceNotAvailableException
     */
    void doReboot() throws DeviceNotAvailableException, UnsupportedOperationException {
        if (TestDeviceState.FASTBOOT == getDeviceState()) {
            Log.i(LOG_TAG, String.format("device %s in fastboot. Rebooting to userspace.",
                    getSerialNumber()));
            executeFastbootCommand("reboot");
        } else {
            Log.i(LOG_TAG, String.format("Rebooting device %s", getSerialNumber()));
            doAdbReboot(null);
            waitForDeviceNotAvailable("reboot", DEFAULT_UNAVAILABLE_TIMEOUT);
        }
    }

    /**
     * Perform a adb reboot.
     *
     * @param into the bootloader name to reboot into, or <code>null</code> to just reboot the
     *            device.
     * @throws DeviceNotAvailableException
     */
    private void doAdbReboot(final String into) throws DeviceNotAvailableException {
        DeviceAction rebootAction = new DeviceAction() {
            public boolean run() throws TimeoutException, IOException, AdbCommandRejectedException {
                getIDevice().reboot(into);
                return true;
            }
        };
        performDeviceAction("reboot", rebootAction, MAX_RETRY_ATTEMPTS);
    }

    private void waitForDeviceNotAvailable(String operationDesc, long time) {
        // TODO: a bit of a race condition here. Would be better to start a
        // before the operation
        if (!mMonitor.waitForDeviceNotAvailable(time)) {
            // above check is flaky, ignore till better solution is found
            Log.w(LOG_TAG, String.format("Did not detect device %s becoming unavailable after %s",
                    getSerialNumber(), operationDesc));
        }
    }

    /**
     * {@inheritDoc}
     */
    public boolean enableAdbRoot() throws DeviceNotAvailableException {
        // adb root is a relatively intensive command, so do a brief check first to see
        // if its necessary or not
        if (isAdbRoot()) {
            CLog.i("adb is already running as root on %s", getSerialNumber());
            return true;
        }
        CLog.i("adb root on device %s", getSerialNumber());
        final String[] fullCmd = buildAdbCommand("root");
        AdbRootAction rootAction = new AdbRootAction(fullCmd);

        performDeviceAction("adb root", rootAction, MAX_RETRY_ATTEMPTS);
        if (rootAction.mNeedWait) {
            // wait for device to disappear from adb
            waitForDeviceNotAvailable("root", 30 * 1000);
            // wait for device to be back online
            waitForDeviceOnline();
        }
        return true;
    }

    private boolean isAdbRoot() throws DeviceNotAvailableException {
        String output = executeShellCommand("id");
        return output.contains("uid=0(root)");
    }

    /**
     * {@inheritDoc}
     */
    public boolean encryptDevice(boolean inplace) throws DeviceNotAvailableException,
            UnsupportedOperationException {
        if (!isEncryptionSupported()) {
            throw new UnsupportedOperationException(String.format("Can't encrypt device %s: "
                    + "encryption not supported", getSerialNumber()));
        }

        if (isDeviceEncrypted()) {
            CLog.d("Device %s is already encrypted, skipping", getSerialNumber());
            return true;
        }

        String encryptMethod;
        int timeout;
        if (inplace) {
            encryptMethod = "inplace";
            timeout = ENCRYPTION_INPLACE_TIMEOUT;
        } else {
            encryptMethod = "wipe";
            timeout = ENCRYPTION_WIPE_TIMEOUT;
        }

        CLog.i("Encrypting device %s via %s", getSerialNumber(), encryptMethod);
        executeShellCommand(String.format("vdc cryptfs enablecrypto %s \"%s\"", encryptMethod,
                ENCRYPTION_PASSWORD), new NullOutputReceiver(), timeout, 1);

        waitForDeviceNotAvailable("reboot", getCommandTimeout());
        waitForDeviceOnline();  // Device will not become available until the user data is unlockedu.

        return isDeviceEncrypted();
    }

    /**
     * {@inheritDoc}
     */
    public boolean unencryptDevice() throws DeviceNotAvailableException,
            UnsupportedOperationException {
        if (!isEncryptionSupported()) {
            throw new UnsupportedOperationException(String.format("Can't unencrypt device %s: "
                    + "encryption not supported", getSerialNumber()));
        }

        if (!isDeviceEncrypted()) {
            CLog.d("Device %s is already unencrypted, skipping", getSerialNumber());
            return true;
        }

        CLog.i("Unencrypting device %s", getSerialNumber());

        // Determine if we need to format partition instead of wipe.
        boolean format = false;
        String output = executeShellCommand("vdc volume list");
        String[] splitOutput;
        if (output != null) {
            splitOutput = output.split("\r\n");
            for (String line : splitOutput) {
                if (line.startsWith("110 ") && line.contains("sdcard /mnt/sdcard") &&
                        !line.endsWith("0")) {
                    format = true;
                }
            }
        }

        rebootIntoBootloader();
        executeLongFastbootCommand("erase", "userdata");

        if (format) {
            CLog.d("Need to format sdcard for device %s", getSerialNumber());

            rebootUntilOnline();
            RecoveryMode cachedRecoveryMode = getRecoveryMode();
            setRecoveryMode(RecoveryMode.ONLINE);

            output = executeShellCommand("vdc volume format sdcard");
            if (output == null) {
                CLog.e("Command vdc volume format sdcard failed will no output for device %s:\n%s",
                        getSerialNumber());
                setRecoveryMode(cachedRecoveryMode);
                return false;
            }
            splitOutput = output.split("\r\n");
            if (!splitOutput[splitOutput.length - 1].startsWith("200 ")) {
                CLog.e("Command vdc volume format sdcard failed for device %s:\n%s",
                        getSerialNumber(), output);
                setRecoveryMode(cachedRecoveryMode);
                return false;
            }

            setRecoveryMode(cachedRecoveryMode);
        }

        reboot();

        return true;
    }

    /**
     * {@inheritDoc}
     */
    public boolean unlockDevice() throws DeviceNotAvailableException,
            UnsupportedOperationException {
        if (!isEncryptionSupported()) {
            throw new UnsupportedOperationException(String.format("Can't unlock device %s: "
                    + "encryption not supported", getSerialNumber()));
        }

        if (!isDeviceEncrypted()) {
            CLog.d("Device %s is not encrypted, skipping", getSerialNumber());
            return true;
        }

        CLog.i("Unlocking device %s", getSerialNumber());

        // FIXME: currently, vcd checkpw can return an empty string when it never should.  Try 3
        // times.
        String output;
        int i = 0;
        do {
            // Enter the password. Output will be:
            // "200 -1" if the password has already been entered correctly,
            // "200 0" if the password is entered correctly,
            // "200 N" where N is any positive number if the password is incorrect,
            // any other string if there is an error.
            output = executeShellCommand(String.format("vdc cryptfs checkpw \"%s\"",
                    ENCRYPTION_PASSWORD)).trim();

            if ("200 -1".equals(output)) {
                return true;
            }

            if (!"".equals(output) && !"200 0".equals(output)) {
                CLog.e("checkpw gave output '%s' while trying to unlock device %s",
                        output, getSerialNumber());
                return false;
            }

            getRunUtil().sleep(500);
        } while ("".equals(output) && i++ < 3);

        if ("".equals(output)) {
            CLog.e("checkpw gave no output while trying to unlock device %s");
        }

        // Restart the framework. Output will be:
        // "200 0" if the user data partition can be mounted,
        // "200 -1" if the user data partition can not be mounted (no correct password given),
        // any other string if there is an error.
        output = executeShellCommand("vdc cryptfs restart").trim();

        if (!"200 0".equals(output)) {
            CLog.e("restart gave output '%s' while trying to unlock device %s", output,
                    getSerialNumber());
            return false;
        }

        waitForDeviceAvailable();

        return true;
    }

    /**
     * {@inheritDoc}
     */
    public boolean isDeviceEncrypted() {
        String output = getIDevice().getProperty("ro.crypto.state");

        // TODO: Remove cruft once getProperty() null caching issue is fixed.
        if (output == null) {
            try {
                output = executeShellCommand("getprop ro.crypto.state").trim();
                if ("".equals(output)) {
                    output = null;
                }
            } catch (DeviceNotAvailableException e) {
                output = null;
            }
        }

        return "encrypted".equals(output);
    }

    /**
     * {@inheritDoc}
     */
    public boolean isEncryptionSupported() throws DeviceNotAvailableException {
        String output = executeShellCommand("vdc cryptfs enablecrypto").trim();
        return (output != null && output.startsWith(ENCRYPTION_SUPPORTED_OUTPUT));
    }

    /**
     * {@inheritDoc}
     */
    public void waitForDeviceOnline(long waitTime) throws DeviceNotAvailableException {
        if (mMonitor.waitForDeviceOnline(waitTime) == null) {
            recoverDevice();
        }
    }

    /**
     * {@inheritDoc}
     */
    public void waitForDeviceOnline() throws DeviceNotAvailableException {
        if (mMonitor.waitForDeviceOnline() == null) {
            recoverDevice();
        }
    }

    /**
     * {@inheritDoc}
     */
    public void waitForDeviceAvailable(long waitTime) throws DeviceNotAvailableException {
        if (mMonitor.waitForDeviceAvailable(waitTime) == null) {
            recoverDevice();
        }
    }

    /**
     * {@inheritDoc}
     */
    public void waitForDeviceAvailable() throws DeviceNotAvailableException {
        if (mMonitor.waitForDeviceAvailable() == null) {
            recoverDevice();
        }
    }

    /**
     * {@inheritDoc}
     */
    public boolean waitForDeviceNotAvailable(long waitTime) {
        return mMonitor.waitForDeviceNotAvailable(waitTime);
    }

    /**
     * {@inheritDoc}
     */
    public boolean waitForDeviceInRecovery(long waitTime) {
        return mMonitor.waitForDeviceInRecovery(waitTime);
    }

    void setEnableAdbRoot(boolean enable) {
        mEnableAdbRoot = enable;
    }

    /**
     * Retrieve this device's recovery mechanism.
     * <p/>
     * Exposed for unit testing.
     */
    IDeviceRecovery getRecovery() {
        return mRecovery;
    }

    /**
     * {@inheritDoc}
     */
    public void setRecovery(IDeviceRecovery recovery) {
        mRecovery = recovery;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setRecoveryMode(RecoveryMode mode) {
        mRecoveryMode = mode;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RecoveryMode getRecoveryMode() {
        return mRecoveryMode;
    }

    /**
     * {@inheritDoc}
     */
    public void setFastbootEnabled(boolean fastbootEnabled) {
        mFastbootEnabled = fastbootEnabled;
    }

    /**
     * {@inheritDoc}
     */
    public void setDeviceState(final TestDeviceState deviceState) {
        if (!deviceState.equals(getDeviceState())) {
            // disable state changes while fastboot lock is held, because issuing fastboot command
            // will disrupt state
            if (getDeviceState().equals(TestDeviceState.FASTBOOT) && !mFastbootLock.tryAcquire()) {
                return;
            }
            CLog.d("Device %s state is now %s", getSerialNumber(), deviceState);
            mState = deviceState;
            mFastbootLock.release();
            mMonitor.setState(deviceState);
        }
    }

    /**
     * {@inheritDoc}
     */
    public TestDeviceState getDeviceState() {
        return mState;
    }

    @Override
    public boolean isAdbTcp() {
        return mMonitor.isAdbTcp();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String switchToAdbTcp() throws DeviceNotAvailableException {
        String ipAddress = getIpAddress();
        if (ipAddress == null) {
            Log.e(LOG_TAG, String.format("connectToTcp failed: Device %s doesn't have an IP",
                    getSerialNumber()));
            return null;
        }
        String port = "5555";
        executeAdbCommand("tcpip", port);
        // TODO: analyze result? wait for device offline?
        return String.format("%s:%s", ipAddress, port);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean switchToAdbUsb() throws DeviceNotAvailableException {
        executeAdbCommand("usb");
        // TODO: analyze result? wait for device offline?
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setEmulatorProcess(Process p) {
        mEmulatorProcess = p;

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Process getEmulatorProcess() {
        return mEmulatorProcess;
    }
}
