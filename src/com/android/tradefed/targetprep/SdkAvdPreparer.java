/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.tradefed.targetprep;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.ISdkBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceManager;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.IDeviceManager;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.TestDeviceState;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.ArrayUtil;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;

import java.util.List;

import junit.framework.Assert;

/**
 * A {@link ITargetPreparer} that will create an avd and launch an emulator
 */
public class SdkAvdPreparer implements ITargetPreparer {

    private static final int ANDROID_TIMEOUT_MS = 15 * 1000;

    @Option(name = "sdk-target", description = "the name of SDK target to launch. " +
            "If unspecified, will use first target found")
    private String mTargetName = null;

    @Option(name = "boot-time", description =
        "the maximum time in minutes to wait for emulator to boot.")
    private long mMaxBootTime = 5;

    @Option(name = "window", description = "launch emulator with a graphical window display.")
    private boolean mWindow = false;

    @Option(name = "launch-attempts", description = "max number of attempts to launch emulator")
    private int mLaunchAttempts = 1;

    @Option(name = "sdcard-size", description = "capacity of the SD card")
    private String mSdcardSize = "10M";

    @Option(name = "gpu", description = "launch emulator with GPU on")
    private boolean mGpu = false;

    @Option(name = "abi", description = "abi to select for the avd")
    private String mAbi = null;

    private final IRunUtil mRunUtil;
    private final IDeviceManager mDeviceManager;

    /**
     * Creates a {@link SdkAvdPreparer}.
     */
    public SdkAvdPreparer() {
        this(new RunUtil(), DeviceManager.getInstance());
    }

    /**
     * Alternate constructor for injecting dependencies.
     *
     * @param runUtil
     */
    SdkAvdPreparer(IRunUtil runUtil, IDeviceManager deviceManager) {
        mRunUtil = runUtil;
        mDeviceManager = deviceManager;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setUp(ITestDevice device, IBuildInfo buildInfo) throws TargetSetupError,
            DeviceNotAvailableException, BuildError {
        Assert.assertTrue("Provided build is not a ISdkBuildInfo",
                buildInfo instanceof ISdkBuildInfo);
        ISdkBuildInfo sdkBuildInfo = (ISdkBuildInfo)buildInfo;
        launchEmulatorForAvd(sdkBuildInfo, device, createAvd(sdkBuildInfo));
    }

    /**
     * Finds SDK target based on the {@link ISdkBuildInfo}, creates AVD for
     * this target and returns its name.
     *
     * @param sdkBuildInfo the {@link ISdkBuildInfo}
     * @return the created AVD name
     * @throws TargetSetupError if could not get targets
     * @throws BuildError if failed to create the AVD
     */
    public String createAvd(ISdkBuildInfo sdkBuildInfo)
          throws TargetSetupError, BuildError {
        String[] targets = getSdkTargets(sdkBuildInfo);
        setAndroidSdkHome(sdkBuildInfo);
        String target = findTargetToLaunch(targets);
        return createAvdForTarget(sdkBuildInfo, target);
    }

    /**
     * Launch an emulator for given avd, and wait for it to become available.
     *
     * @param avd the avd to launch
     * @throws DeviceNotAvailableException
     * @throws TargetSetupError if could not get targets
     * @throws BuildError if emulator fails to boot
     */
    public void launchEmulatorForAvd(ISdkBuildInfo sdkBuild, ITestDevice device, String avd)
            throws DeviceNotAvailableException, TargetSetupError, BuildError {
        if (!device.getDeviceState().equals(TestDeviceState.NOT_AVAILABLE)) {
            CLog.w("Emulator %s is already running, killing", device.getSerialNumber());
            mDeviceManager.killEmulator(device);
        }
        List<String> emulatorArgs = ArrayUtil.list(sdkBuild.getEmulatorToolPath(), "-avd", avd);

        if (!mWindow) {
            emulatorArgs.add("-no-window");
        }

        if (mGpu) {
            emulatorArgs.add("-gpu");
            emulatorArgs.add("on");
        }

        launchEmulator(device, avd, emulatorArgs);
        if (!device.getIDevice().getAvdName().equals(avd)) {
            // not good. Either emulator isn't reporting its avd name properly, or somehow
            // the wrong emulator launched. Treat as a BuildError
            throw new BuildError(String.format(
                    "Emulator booted with incorrect avd name '%s'. Expected: '%s'.",
                    device.getIDevice().getAvdName(), avd));
        }
    }

    /**
     * Sets programmatically whether the gpu should be on or off.
     *
     * @param gpu
     */
    public void setGpu(boolean gpu) {
        mGpu = gpu;
    }

    /**
     * Gets the list of sdk targets from the given sdk.
     *
     * @param sdkBuild
     * @return a list of defined targets
     * @throws TargetSetupError if could not get targets
     */
    private String[] getSdkTargets(ISdkBuildInfo sdkBuild) throws TargetSetupError {
        CommandResult result = mRunUtil.runTimedCmd(ANDROID_TIMEOUT_MS,
                sdkBuild.getAndroidToolPath(), "list", "targets", "--compact");
        if (!result.getStatus().equals(CommandStatus.SUCCESS)) {
            throw new TargetSetupError(String.format(
                    "Unable to get list of SDK targets using %s. Result %s. stdout: %s, err: %s",
                    sdkBuild.getAndroidToolPath(), result.getStatus(), result.getStdout(),
                    result.getStderr()));
        }
        String[] targets = result.getStdout().split("\n");
        if (result.getStdout().trim().isEmpty() || targets.length == 0) {
            throw new TargetSetupError(String.format("No targets found in SDK %s.",
                    sdkBuild.getSdkDir().getAbsolutePath()));
        }
        return targets;
    }

    /**
     * Sets the ANDROID_SDK_HOME environment variable. The SDK home directory is used as the
     * location for SDK file storage of AVD definition files, etc.
     *
     * @param sdkBuild
     */
    private void setAndroidSdkHome(ISdkBuildInfo sdkBuild) {
        // store avds etc in sdk build location
        // this has advantage that they will be cleaned up after run
        mRunUtil.setEnvVariable("ANDROID_SDK_HOME", sdkBuild.getSdkDir().getAbsolutePath());
    }

    /**
     * Find the SDK target to use.
     * <p/>
     * Will use the 'sdk-target' option if specified, otherwise will return last target in target
     * list.
     *
     * @param targets the list of targets in SDK
     * @return the SDK target name
     * @throws TargetSetupError if specified 'sdk-target' cannot be found
     */
    private String findTargetToLaunch(String[] targets) throws TargetSetupError {
        if (mTargetName != null) {
            for (String foundTarget : targets) {
                if (foundTarget.equals(mTargetName)) {
                    return mTargetName;
                }
            }
            throw new TargetSetupError(String.format("Could not find target %s in sdk",
                    mTargetName));
        }
        // just return last target
        return targets[targets.length - 1];
    }

    /**
     * Create an AVD for given SDK target.
     *
     * @param sdkBuild the {@link ISdkBuildInfo}
     * @param target the SDK target name
     * @return the created AVD name
     * @throws BuildError if failed to create the AVD
     *
     */
    private String createAvdForTarget(ISdkBuildInfo sdkBuild, String target)
            throws BuildError {
        // answer 'no' when prompted for creating a custom hardware profile
        final String cmdInput = "no\r\n";
        final String successPattern = String.format("Created AVD '%s'", target);
        CLog.d("Creating avd for target %s with name %s", target, target);

        List<String> avdCommand = ArrayUtil.list(sdkBuild.getAndroidToolPath(),
              "create", "avd", "--target", target, "--name", target, "--sdcard",
              mSdcardSize, "--force");

        if (mAbi != null) {
            avdCommand.add("--abi");
            avdCommand.add(mAbi);
        }

        CommandResult result = mRunUtil.runTimedCmdWithInput(ANDROID_TIMEOUT_MS,
              cmdInput, avdCommand);
        if (!result.getStatus().equals(CommandStatus.SUCCESS) || result.getStdout() == null ||
                !result.getStdout().contains(successPattern)) {
            // stdout usually doesn't contain useful data, so don't want to add it to the
            // exception message. However, log it here as a debug log so the info is captured
            // in log
            CLog.d("AVD creation failed. stdout: %s", result.getStdout());
            // treat as BuildError
            throw new BuildError(String.format(
                    "Unable to create avd for target '%s'. stderr: '%s'", target,
                    result.getStderr()));
        }
        return target;
    }

    /**
     * Launch emulator, performing multiple attempts if necessary as specified.
     *
     * @param device
     * @param avd
     * @param emulatorArgs
     * @throws BuildError
     */
    void launchEmulator(ITestDevice device, String avd, List<String> emulatorArgs)
            throws BuildError {
        for (int i = 1; i <= mLaunchAttempts; i++) {
            try {
                mDeviceManager.launchEmulator(device, mMaxBootTime * 60 * 1000, mRunUtil,
                        emulatorArgs);
                // hack alert! adb to emulator communication on first boot is notoriously flaky
                // b/4644136
                // send it a few adb commands to ensure the communication channel is stable
                CLog.d("Testing adb to %s communication", device.getSerialNumber());
                for (int j = 0; j < 3; j++) {
                    device.executeShellCommand("pm list instrumentation");
                    mRunUtil.sleep(2 * 1000);
                }

                // hurray - launched!
                return;
            } catch (DeviceNotAvailableException e) {
                CLog.w("Emulator for avd '%s' failed to launch on attempt %d of %d. Cause: %s",
                        avd, i, mLaunchAttempts, e);
            }
            try {
                // ensure process has been killed
                mDeviceManager.killEmulator(device);
            } catch (DeviceNotAvailableException e) {
                // ignore
            }
        }
        throw new DeviceFailedToBootError(
                String.format("Emulator for avd '%s' failed to boot.", avd));
    }

    /**
     * Sets the number of launch attempts to perform.
     *
     * @param mLaunchAttempts
     */
    void setLaunchAttempts(int launchAttempts) {
        mLaunchAttempts = launchAttempts;
    }
}
