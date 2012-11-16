/*
 * Copyright (C) 2012 The Android Open Source Project
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

import com.android.ddmlib.FileListingService;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.StubTestInvocationListener;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class UiAutomatorTest implements IRemoteTest, IDeviceTest {

    private static final String SHELL_EXE_BASE = "/data/local/tmp/";

    private ITestDevice mDevice = null;
    private UiAutomatorRunner mRunner = null;

    @Option(name = "jar-path", description = "path to jars containing UI Automator test cases and"
            + " dependencies; May be repeated.", mandatory = true)
    private List<String> mJarPaths = new ArrayList<String>();

    @Option(name = "class",
            description = "test class to run, may be repeated; multiple classess will be run"
                    + " in the same order as provided in command line",
                    mandatory = true)
    private List<String> mClasses = new ArrayList<String>();

    @Option(name = "sync-time", description = "time to allow for initial sync, in ms")
    private long mSyncTime = 0;

    @Option(name = "run-arg",
            description = "Additional test specific arguments to provide.")
    private Map<String, String> mArgMap = new LinkedHashMap<String, String>();

    @Option(name = "timeout",
            description = "Aborts the test run if any test takes longer than the specified number "
                    + "of milliseconds. For no timeout, set to 0.")
    private int mTestTimeout = 30 * 60 * 1000;  // default to 30 minutes

    @Option(name = "capture-logs", description =
            "capture bugreport and screenshot after each failed test")
    private boolean mCaptureLogs = true;

    @Option(name = "runner-path", description = "path to uiautomator runner; may be null and "
            + "default will be used in this case")
    private String mRunnerPath = null;

    private String mRunName;

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

    public void setCaptureLogs(boolean captureLogs) {
        mCaptureLogs = captureLogs;
    }

    public void setRunName(String runName) {
        mRunName = runName;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        mRunner = new UiAutomatorRunner(getDevice().getIDevice(),
                getTestJarPaths().toArray(new String[]{}),
                mClasses.toArray(new String[]{}), mRunnerPath);
        mRunner.setRunName(mRunName);
        preTestSetup();
        getRunUtil().sleep(getSyncTime());
        mRunner.setMaxtimeToOutputResponse(mTestTimeout);
        for (Map.Entry<String, String> entry : getTestRunArgMap().entrySet()) {
            getTestRunner().addInstrumentationArg(entry.getKey(), entry.getValue());
        }
        if (mCaptureLogs) {
            getDevice().runInstrumentationTests(getTestRunner(), listener,
                    new FailureReportWrapper(listener));
        } else {
            getDevice().runInstrumentationTests(getTestRunner(), listener);
        }
    }

    /**
     * Add an argument to provide when running the UI Automator tests
     *
     * @param key the argument name
     * @param value the argument value
     */
    public void addRunArg(String key, String value) {
        getTestRunArgMap().put(key, value);
    }

    /**
     * Checks if the Geppeto components are present on device
     *
     * @throws DeviceNotAvailableException
     */
    protected void preTestSetup() throws DeviceNotAvailableException {
        String runnerPath = getTestRunner().getRunnerPath();
        if (!getDevice().doesFileExist(runnerPath)) {
            throw new RuntimeException("Missing UI Automator runner: " + runnerPath);
        }
        for (String jarPath : getTestJarPaths()) {
            if (!jarPath.startsWith(FileListingService.FILE_SEPARATOR)) {
                jarPath = SHELL_EXE_BASE + jarPath;
            }
            if (!getDevice().doesFileExist(jarPath)) {
                throw new RuntimeException("Missing UI Automator test jar on device: " + jarPath);
            }
        }
    }

    /**
     * Wraps an existing listener, capture some data in case of test failure
     */
    // TODO replace this once we have a generic event triggered reporter like
    // BugReportCollector
    private class FailureReportWrapper extends StubTestInvocationListener {

        ITestInvocationListener mListener;

        public FailureReportWrapper(ITestInvocationListener listener) {
            mListener = listener;
        }

        @Override
        public void testFailed(TestFailure status, TestIdentifier test, String trace) {
            InputStreamSource data = null;
            // get screen shot
            try {
                data = getDevice().getScreenshot();
                mListener.testLog(test.getTestName() + "_failure_screenshot.png", LogDataType.PNG,
                        data);
            } catch (DeviceNotAvailableException e) {
                CLog.e(e);
            } finally {
                if (data != null) {
                    data.cancel();
                }
            }
            // get bugreport
            data = getDevice().getBugreport();
            mListener.testLog(test.getTestName() + "_failure_bugreport.txt",
                    LogDataType.TEXT, data);
            if (data != null) {
                data.cancel();
            }
        }
    }

    protected IRunUtil getRunUtil() {
        return RunUtil.getDefault();
    }

    /**
     * @return the time allocated for the tests to sync.
     */
    public long getSyncTime() {
        return mSyncTime;
    }

    /**
     * @param syncTime the time for the tests files to sync.
     */
    public void setSyncTime(long syncTime) {
        mSyncTime = syncTime;
    }

    /**
     * @return the UI Automator Test Runner.
     */
    public UiAutomatorRunner getTestRunner() {
        return mRunner;
    }

    /**
     * @return the test jar path.
     */
    public List<String> getTestJarPaths() {
        return mJarPaths;
    }

    /**
     * @param jarPath {@link String} the location of the test jar.
     */
    public void setTestJarPaths(List<String> jarPaths) {
        mJarPaths = jarPaths;
    }

    /**
     * @return the arguments map to pass to the UiAutomatorRunner.
     */
    public Map<String, String> getTestRunArgMap() {
        return mArgMap;
    }

    /**
     * @param runArgMap the arguments to pass to the UiAutomatorRunner.
     */
    public void setTestRunArgMap(Map<String, String> runArgMap) {
        mArgMap = runArgMap;
    }

    /**
     * Add a test class name to run.
     */
    public void addClassName(String className) {
        mClasses.add(className);
    }

    /**
     * Add a test class name collection to run.
     */
    public void addClassNames(Collection<String> classNames) {
        mClasses.addAll(classNames);
    }
}
