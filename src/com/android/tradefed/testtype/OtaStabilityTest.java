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
package com.android.tradefed.testtype;

import com.android.ddmlib.Log;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IConfigurationReceiver;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.util.FileUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import junit.framework.Assert;
import junit.framework.AssertionFailedError;

/**
 * A test that will flash a build on a device, wait for the device to be OTA-ed to another build,
 * and then repeat N times.
 * <p/>
 * Note: this test assumes that the {@link ITargetPreparers} included in this test's
 * {@link IConfiguration} will flash the device back to a baseline build, and prepare the device
 * to receive the OTA to a new build.
 */
public class OtaStabilityTest implements IDeviceTest, IBuildReceiver, IConfigurationReceiver,
        IShardableTest {

    private static final String LOG_TAG = "OtaStabilityTest";
    private IBuildInfo mDeviceBuild;
    private IConfiguration mConfiguration;
    private ITestDevice mDevice;

    @Option(name = "run-name", description =
        "The name of the ota stability test run. Used to report metrics")
    private String mRunName = "ota-stability";

    @Option(name = "iterations", description =
        "Number of ota stability 'flash + wait for ota' iterations to run")
    private int mIterations = 10;

    @Option(name = "wait-recovery-time", description =
        "Number of seconds to wait for device to begin installing ota. Default 15 min")
    private int mWaitRecoveryTime = 15 * 60;

    @Option(name = "wait-install-time", description =
        "Number of seconds to wait for device to be online after beginning ota installation. " +
        "Default 10 min")
    private int mWaitInstallTime = 10 * 60;

    @Option(name = "shards", description = "Optional number of shards to split test into. " +
            "Iterations will be split evenly among shards.")
    private int mShards = 1;

    /**
     * {@inheritDoc}
     */
    @Override
    public void setConfiguration(IConfiguration configuration) {
        mConfiguration = configuration;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mDeviceBuild = buildInfo;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setDevice(ITestDevice device) {
        mDevice = device;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ITestDevice getDevice() {
        return mDevice;
    }

    /**
     * Set the wait recovery time
     */
    void setWaitRecoveryTime(int waitRecoveryTime) {
        mWaitRecoveryTime = waitRecoveryTime;
    }

    /**
     * Set the wait install time
     */
    void setWaitInstallTime(int waitInstallTime) {
        mWaitInstallTime = waitInstallTime;
    }

    /**
     * Set the run name
     */
    void setRunName(String runName) {
        mRunName = runName;
    }

    /**
     * Return the number of iterations.
     * <p/>
     * Exposed for unit testing
     */
    public int getIterations() {
        return mIterations;
    }

    /**
     * Set the iterations
     */
    void setIterations(int iterations) {
        mIterations = iterations;
    }

    /**
     * Set the number of shards
     */
    void setShards(int shards) {
        mShards = shards;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<IRemoteTest> split() {
        if (mShards <= 1) {
            return null;
        }
        Collection<IRemoteTest> shards = new ArrayList<IRemoteTest>(mShards);
        int remainingIterations = mIterations;
        for (int i = mShards; i > 0; i--) {
            OtaStabilityTest testShard = new OtaStabilityTest();
            // device and configuration will be set by test invoker
            testShard.setRunName(mRunName);
            testShard.setWaitInstallTime(mWaitInstallTime);
            testShard.setWaitRecoveryTime(mWaitRecoveryTime);
            // attempt to divide iterations evenly among shards with no remainder
            int iterationsForShard = Math.round(remainingIterations/i);
            if (iterationsForShard > 0) {
                testShard.setIterations(iterationsForShard);
                remainingIterations -= iterationsForShard;
                shards.add(testShard);
            }
        }
        return shards;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void run(List<ITestInvocationListener> listeners) throws DeviceNotAvailableException {
        checkFields();

        long startTime = System.currentTimeMillis();
        for (ITestInvocationListener listener : listeners) {
            listener.testRunStarted(mRunName, 0);
        }
        int actualIterations = 0;
        try {
            waitForOta(listeners);
            for (actualIterations = 1; actualIterations < mIterations; actualIterations++) {
                flashDevice();
                int buildId = waitForOta(listeners);
                Log.i(LOG_TAG, String.format(
                        "Device %s successfully OTA-ed to build %d. Iteration: %d",
                        mDevice.getSerialNumber(), buildId, actualIterations));
            }
        } catch (AssertionFailedError error) {
            Log.e(LOG_TAG, error);
        } catch (TargetSetupError e) {
            Log.e(LOG_TAG, e);
        } catch (BuildError e) {
            Log.e(LOG_TAG, e);
        } catch (ConfigurationException e) {
            Log.e(LOG_TAG, e);
        } finally {
            Map<String, String> metrics = new HashMap<String, String>(1);
            metrics.put("iterations", Integer.toString(actualIterations));
            long endTime = System.currentTimeMillis() - startTime;
            for (ITestInvocationListener listener : listeners) {
                listener.testRunEnded(endTime, metrics);
            }
        }
    }


    /**
     * Flash the device back to baseline build.
     * <p/>
     * Currently does this by re-running {@link ITargetPreparer#setUp(ITestDevice, IBuildInfo)}
     *
     * @throws DeviceNotAvailableException
     * @throws BuildError
     * @throws TargetSetupError
     * @throws ConfigurationException
     */
    private void flashDevice() throws TargetSetupError, BuildError, DeviceNotAvailableException,
            ConfigurationException {
        // assume the target preparers will flash the device back to device build
        for (ITargetPreparer preparer : mConfiguration.getTargetPreparers()) {
            preparer.setUp(mDevice, mDeviceBuild);
        }
    }

    /**
     * Blocks and waits for OTA package to be installed.
     *
     * @param listeners
     * @return the build id the device ota-ed to
     * @throws DeviceNotAvailableException
     * @throws AssertionFailedError
     */
    private int waitForOta(List<ITestInvocationListener> listeners)
            throws DeviceNotAvailableException, AssertionFailedError {
        int currentBuildId =  mDevice.getBuildId();
        Assert.assertEquals(String.format("device %s does not have expected build id on boot.",
                mDevice.getSerialNumber()), currentBuildId, mDeviceBuild.getBuildId());
        Assert.assertTrue(String.format(
                "Device %s did not enter recovery after %d s.",
                mDevice.getSerialNumber(), mWaitRecoveryTime),
                mDevice.waitForDeviceInRecovery(mWaitRecoveryTime * 1000));
        try {
            mDevice.waitForDeviceOnline(mWaitInstallTime * 1000);
        } catch (DeviceNotAvailableException e) {
            Log.e(LOG_TAG, String.format(
                    "Device %s did not come back online after leaving recovery",
                    mDevice.getSerialNumber()));
            sendRecoveryLog(listeners);
            throw e;
        }
        try {
            mDevice.waitForDeviceAvailable();
        } catch (DeviceNotAvailableException e) {
            Log.e(LOG_TAG, String.format(
                    "Device %s did not boot up successfully after leaving recovery/installing OTA",
                    mDevice.getSerialNumber()));
            throw e;
        }
        currentBuildId = mDevice.getBuildId();
        // TODO: should exact expected build id be checked?
        Assert.assertTrue(String.format("Device %s build id did not change after leaving recovery",
                mDevice.getSerialNumber()), currentBuildId !=  mDeviceBuild.getBuildId());
        return currentBuildId;
    }

    private void sendRecoveryLog(List<ITestInvocationListener> listeners) throws
            DeviceNotAvailableException {
        try {
            // get recovery log
            File destFile = FileUtil.createTempFile("recovery", "log");
            boolean gotFile = mDevice.pullFile("/tmp/recovery.log", destFile);
            if (gotFile) {
                for (ITestInvocationListener listener : listeners) {
                    listener.testLog("recovery_log", LogDataType.TEXT, new FileInputStream(destFile));
                }
            }
        } catch (IOException e) {
            Log.e(LOG_TAG, String.format("Failed to get recovery log from device %s",
                    mDevice.getSerialNumber()));
            Log.e(LOG_TAG, e);
        }
    }

    private void checkFields() {
        if (mDevice == null) {
            throw new IllegalArgumentException("missing device");
        }
        if (mConfiguration == null) {
            throw new IllegalArgumentException("missing configuration");
        }
        if (mDeviceBuild == null) {
            throw new IllegalArgumentException("missing build info");
        }
    }
}
