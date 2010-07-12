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

import com.android.ddmlib.Log;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.FileListingServiceWrapper;
import com.android.tradefed.device.ICancelableReceiver;
import com.android.tradefed.device.IFileListingService;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.IFileListingService.IFileEntry;
import com.android.tradefed.result.ITestInvocationListener;

import junit.framework.TestResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Vector;

/**
 * A Test that runs a native test package on given device.
 */
public class GTest implements IDeviceTest, IRemoteTest {

    private static final String LOG_TAG = "GTest";
    private ITestDevice mDevice = null;
    /** controls whether to run all tests in all subdirectories or just tests in the root dir */
    private boolean mRunAllTestsInAllSubdirectories = true;
    private boolean mRunDisabledTests = false;

    @Option(name = "native-test-device-path",
      description="The path on the device where native tests are located.")
    private String nativeTestDevicePath = "data/nativetest";

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

        String flags = String.format("%s %s",
                GTEST_FLAG_PRINT_TIME, getGTestFilters());

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
    private String[] getTestPath() {
        nativeTestDevicePath = nativeTestDevicePath.trim();

        // Discard the initial "/" if it was included
        if (nativeTestDevicePath.startsWith("/")) {
          nativeTestDevicePath = nativeTestDevicePath.substring(1);
        }

        String[] pathComponents = nativeTestDevicePath.split("/");
        if (mTestModule != null) {
            ArrayList<String> list = new ArrayList<String>(Arrays.asList(pathComponents));
            list.add(mTestModule);
            return list.toArray(new String[pathComponents.length + 1]);
        }
        return pathComponents;
    }

    /**
     * {@inheritDoc}
     */
    public int countTestCases() {
        // TODO: not sure we even want to support this
        throw new UnsupportedOperationException();    }

    /**
     * unsupported
     */
    public void run(TestResult result) {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns the IFileListingService for this device; exposed for unit testing
     */
    protected IFileListingService getFileListingService() {
        if (mDevice == null) {
            throw new NullPointerException("Trying to get FileListingService when no Device set!");
        }
        return FileListingServiceWrapper.getFileListingSericeForDevice(
                mDevice.getIDevice());
    }

    /**
     * Executes all native tests in a folder as well as in all subfolders recursively.
     *
     * @param rootEntry The root folder to begin searching for native tests
     * @param testDevice The device to run tests on
     * @param outputReceiver The output receiver where the shell output will be piped to
     * @return true if any tests ran, false otherwise
     * @throws DeviceNotAvailableException
     */
    protected boolean doRunAllTestsInSubdirectory(IFileEntry rootEntry, ITestDevice testDevice,
            ICancelableReceiver outputReceiver) throws DeviceNotAvailableException {

        Vector<IFileEntry> folders = new Vector<IFileEntry>();
        Vector<String> files = new Vector<String>();
        boolean testsRan = false;

        IFileListingService fileListingService = getFileListingService();

        // Get the full directory contents of the native test folder
        IFileEntry[] children = fileListingService.getChildren(rootEntry, true, null);

        for (IFileEntry file : children) {
            String binaryPath = file.getFullEscapedPath();
            if (file.isDirectory()) {
                folders.add(file);
            }
            else if (!file.isAppFileName()) {
                // they shouldn't be here anyway, but in case we find one, don't execute .apks!
                files.add(binaryPath);
            }
        }

        if (mRunAllTestsInAllSubdirectories) {
            // First recursively run tests in all subdirectories
            Iterator<IFileEntry> folderIter = folders.iterator();
            while (folderIter.hasNext()) {
                IFileEntry nextFolder = folderIter.next();
                testsRan |= doRunAllTestsInSubdirectory(nextFolder, testDevice, outputReceiver);
            }
        }

        // Then run any actual tests in the current directory
        Iterator<String> it = files.iterator();
        while (it.hasNext()) {
            String fullPath = it.next();
            String flags = getAllGTestFlags();
            testDevice.executeShellCommand(String.format("%s %s", fullPath, flags), outputReceiver);
            testsRan = true;
        }
        return testsRan;
    }

    /**
     * {@inheritDoc}
     *
     * @throws DeviceNotAvailableException
     */
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        // @TODO: add support for rerunning tests

        if (mDevice == null) {
            throw new IllegalArgumentException("Device has not been set");
        }

        GTestResultParser resultParser = new GTestResultParser(listener);

        IFileListingService fileListingService = getFileListingService();

        IFileEntry nativeTestDirectory = fileListingService.getRoot();

        // recurse down the path until we find the test folder
        for (String pathSubcomponent : getTestPath()) {
            fileListingService.getChildren(nativeTestDirectory, false, null);
            IFileEntry nextDirectory = nativeTestDirectory.findChild(pathSubcomponent);

            if (nextDirectory == null) {
                Log.e(LOG_TAG, String.format("Cound not find subfolder %s in %s!", pathSubcomponent,
                        nativeTestDirectory.getFullEscapedPath()));
                return;
            }
            else {
                nativeTestDirectory = nextDirectory;
            }
        }
        boolean testsRan = doRunAllTestsInSubdirectory(nativeTestDirectory, mDevice, resultParser);
    }

    //@TODO: Add timeout: public void testTimeout(TestIdentifier test) {
}
