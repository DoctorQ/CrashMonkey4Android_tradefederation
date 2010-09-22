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

package com.android.tradefed.testtype;

import com.android.ddmlib.FileListingService;
import com.android.ddmlib.IShellOutputReceiver;
import com.android.ddmlib.Log;
import com.android.ddmlib.testrunner.ITestRunListener;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.IFileEntry;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.result.ITestInvocationListener;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * A Test that runs a native test package on given device.
 */
public class GTest extends AbstractRemoteTest implements IDeviceTest, IRemoteTest {

    private static final String LOG_TAG = "GTest";
    static final String DEFAULT_NATIVETEST_PATH = "data/nativetest";

    private ITestDevice mDevice = null;
    /** controls whether to run all tests in all subdirectories or just tests in the root dir */
    private boolean mRunAllTestsInAllSubdirectories = true;
    private boolean mRunDisabledTests = false;

    @Option(name = "native-test-device-path",
      description="The path on the device where native tests are located.")
    private String mNativeTestDevicePath = DEFAULT_NATIVETEST_PATH;

    @Option(name = "module-name",
            description="The name of the native test module to run.")
    private String mTestModule = null;

    @Option(name = "positive-testname-filter",
            description="The GTest-based positive filter of the test name to run.")
    private String mTestNamePositiveFilter = null;
    @Option(name = "negative-testname-filter",
            description="The GTest-based negative filter of the test name to run.")
    private String mTestNameNegativeFilter = null;

    // GTest flags...
    private static final String GTEST_FLAG_PRINT_TIME = "--gtest_print_time";
    private static final String GTEST_FLAG_FILTER = "--gtest_filter";
    private static final String GTEST_FLAG_RUN_DISABLED_TESTS = "--gtest_also_run_disabled_tests";

    /**
     * {@inheritDoc}
     */
    public void setDevice(ITestDevice device) {
        mDevice = device;
    }

    /**
     * {@inheritDoc}
     */
    public ITestDevice getDevice() {
        return mDevice;
    }

    /**
     * Set whether to run all tests in all subdirectories
     *
     * @param runAllSubTests set to true if tests in all subdirectories should be run (default),
     * false otherwise
     */
    public void setRunTestsInAllSubdirectories(boolean runAllSubTests) {
        mRunAllTestsInAllSubdirectories = runAllSubTests;
    }

    /**
     * Set the Android native test module to run.
     *
     * @param moduleName The name of the native test module to run
     */
    public void setModuleName(String moduleName) {
        mTestModule = moduleName;
    }

    /**
     * Get the Android native test module to run.
     *
     * @return the name of the native test module to run, or null if not set
     */
    public String getModuleName(String moduleName) {
        return mTestModule;
    }

    /**
     * Set whether GTest should run disabled tests.
     */
    public void setRunDisabled(boolean runDisabled) {
        mRunDisabledTests = runDisabled;
    }

    /**
     * Get whether GTest should run disabled tests.
     *
     * @return True if disabled tests should be run, false otherwise
     */
    public boolean getRunDisabledTests() {
        return mRunDisabledTests;
    }

    /**
     * Set the Android native test name to run (positive filter).
     *
     * @param testName A positive filter of the name of the native test to run
     */
    public void setTestNamePositiveFilter(String testName) {
        mTestNamePositiveFilter = testName;
    }

    /**
     * Get the Android native test name to run (positive filter).
     *
     * @return the positive filter of the name of the native test to run
     */
    public String getTestNamePositiveFilter() {
        return mTestNamePositiveFilter;
    }

    /**
     * Set the Android native test name to run (negative filter).
     *
     * @param testName A negative filter of the name of the native test to run
     */
    public void setTestNameNegativeFilter(String testName) {
        mTestNameNegativeFilter = testName;
    }

    /**
     * Get the Android native test name to run (negative filter).
     *
     * @return the negative filter of the name of the native test to run
     */
    public String getTestNameNegativeFilter() {
        return mTestNameNegativeFilter;
    }

    /**
     * Helper to get the adb gtest filter of test to run.
     *
     * Note that filters filter on the function name only (eg: Google Test "Test"); all Google Test
     * "Test Cases" will be considered.
     *
     * @return the full filter flag to pass to the Gtest, or an empty string if none have been
     * specified
     */
    private String getGTestFilters() {
        String filter = "";
        if ((mTestNamePositiveFilter != null) || (mTestNameNegativeFilter != null)) {
            filter = GTEST_FLAG_FILTER + "=";
            if (mTestNamePositiveFilter != null) {
              filter = String.format("%s*.%s", filter, mTestNamePositiveFilter);
            }
            if (mTestNameNegativeFilter != null) {
              filter = String.format("%s-*.%s", filter, mTestNameNegativeFilter);
          }
        }
        return filter;
    }

    /**
     * Helper to get all the GTest flags to pass into the adb shell command.
     *
     * @return the {@link String} of all the GTest flags that should be passed to the GTest
     */
    private String getAllGTestFlags() {
        String flags = String.format("%s %s", GTEST_FLAG_PRINT_TIME, getGTestFilters());

        if (mRunDisabledTests) {
            flags = String.format("%s %s", flags, GTEST_FLAG_RUN_DISABLED_TESTS);
        }
        return flags;
    }

    /**
     * Gets the path where native tests live on the device.
     *
     * @return The path on the device where the native tests live.
     */
    private String getTestPath() {
        StringBuilder testPath = new StringBuilder(mNativeTestDevicePath);
        if (mTestModule != null) {
            testPath.append(FileListingService.FILE_SEPARATOR);
            testPath.append(mTestModule);
        }
        return testPath.toString();
    }

    /**
     * Executes all native tests in a folder as well as in all subfolders recursively.
     * <p/>
     * Exposed for unit testing.
     *
     * @param rootEntry The root folder to begin searching for native tests
     * @param testDevice The device to run tests on
     * @param listeners the run listeners
     * @throws DeviceNotAvailableException
     */
    void doRunAllTestsInSubdirectory(IFileEntry rootEntry, ITestDevice testDevice,
            Collection<ITestRunListener> listeners) throws DeviceNotAvailableException {

        if (rootEntry.isDirectory() && mRunAllTestsInAllSubdirectories) {
            // recursively run tests in all subdirectories
            for (IFileEntry childEntry : rootEntry.getChildren(true)) {
                doRunAllTestsInSubdirectory(childEntry, testDevice, listeners);
            }
        } else {
            // assume every file is a valid gtest binary.
            IShellOutputReceiver resultParser = createResultParser(rootEntry.getName(), listeners);
            String fullPath = rootEntry.getFullEscapedPath();
            String flags = getAllGTestFlags();
            Log.i(LOG_TAG, String.format("Running gtest %s %s on %s", fullPath, flags,
                    mDevice.getSerialNumber()));
            // force file to be executable
            testDevice.executeShellCommand(String.format("chmod 755 %s", fullPath));
            testDevice.executeShellCommand(String.format("%s %s", fullPath, flags), resultParser);
        }

    }

    /**
     * Factory method for creating a {@link IShellOutputReceiver} that parses test output and
     * forwards results to listeners.
     * <p/>
     * Exposed so unit tests can mock
     *
     * @param listeners
     * @param runName
     * @return a {@link IShellOutputReceiver}
     */
    IShellOutputReceiver createResultParser(String runName,
            Collection<ITestRunListener> listeners) {
        GTestResultParser resultParser = new GTestResultParser(runName, listeners);
        return resultParser;
    }

    /**
     * {@inheritDoc}
     */
    public void run(List<ITestInvocationListener> listeners) throws DeviceNotAvailableException {
        // @TODO: add support for rerunning tests
        if (mDevice == null) {
            throw new IllegalArgumentException("Device has not been set");
        }

        String testPath = getTestPath();
        IFileEntry nativeTestDirectory = mDevice.getFileEntry(testPath);
        if (nativeTestDirectory == null) {
            Log.w(LOG_TAG, String.format("Could not find native test directory %s in %s!",
                    testPath, mDevice.getSerialNumber()));
        }
        doRunAllTestsInSubdirectory(nativeTestDirectory, mDevice, convertListeners(listeners));
    }

    /**
     * Convert a list of {@link ITestInvocationListener} to a collection of {@link ITestRunListener}
     */
    private Collection<ITestRunListener> convertListeners(List<ITestInvocationListener> listeners) {
        ArrayList<ITestRunListener> copy = new ArrayList<ITestRunListener>(listeners.size());
        copy.addAll(listeners);
        return copy;
    }

    //@TODO: Add timeout: public void testTimeout(TestIdentifier test) {
}
