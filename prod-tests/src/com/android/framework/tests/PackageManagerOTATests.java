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

package com.android.framework.tests;

import com.android.tradefed.config.Option;
import com.android.tradefed.config.Option.Importance;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.util.FileUtil;

import java.io.File;

public class PackageManagerOTATests extends DeviceTestCase {

    @Option(name = "test-app-path", description =
            "path to the app repository containing test apks", importance = Importance.IF_UNSET)
    private File mTestAppRepositoryPath = null;

    private PackageManagerOTATestUtils mUtils = null;

    // String constants use for the tests.
    private static final String PACKAGE_XPATH = "/packages/package[@name=\"" +
            "com.android.frameworks.coretests.version_test\"]";
    private static final String UPDATE_PACKAGE_XPATH = "/packages/updated-package[@name=\"" +
            "com.android.frameworks.coretests.version_test\"]";
    private static final String VERSION_XPATH = "/packages/package[@name=\"" +
            "com.android.frameworks.coretests.version_test\"]/@version";
    private static final String FLAG_XPATH = "/packages/package[@name=\"" +
            "com.android.frameworks.coretests.version_test\"]/@flags";
    private static final String CODE_PATH_XPATH = "/packages/package[@name=\"" +
            "com.android.frameworks.coretests.version_test\"]/@codePath";
    private static final String VERSION_1_APK = "FrameworkCoreTests_version_1.apk";
    private static final String VERSION_2_APK = "FrameworkCoreTests_version_2.apk";
    private static final String VERSION_3_APK = "FrameworkCoreTests_version_3.apk";
    private static final String SYSTEM_APK = "version_test.apk";
    private static final String SYSTEM_DIFF_APK = "version_test_diff.apk";
    private static final String SYSTEM_APP_PATH = "/system/app/version_test.apk";
    private static final String DATA_APP_DIRECTORY = "/data/app/";
    private static final String DIFF_SYSTEM_APP_PATH = "/system/app/version_test_diff.apk";
    private static final String PACKAGE_NAME = "com.android.frameworks.coretests.version_test";
    private static final String VIBRATE_PERMISSION = "android.permission.VIBRATE";
    private static final String CACHE_PERMISSION = "android.permission.ACCESS_CACHE_FILESYSTEM";

    // Temporary file used when examine the packages xml file from the device.
    private File mPackageXml = null;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mUtils = new PackageManagerOTATestUtils(getDevice());

        // Clean up any potential old files from previous tests.
        mUtils.removeSystemApp(SYSTEM_DIFF_APK, false);
        mUtils.removeAndWipe(SYSTEM_APK);
        getDevice().waitForDeviceAvailable();
        mPackageXml = mUtils.pullPackagesXML();
        assertNotNull("Failed to pull packages xml file from device", mPackageXml);
        assertFalse("Package should not be installed before test",
                mUtils.expectExists(mPackageXml, PACKAGE_XPATH));
        assertFalse("Updated-package should not be present before test",
                mUtils.expectExists(mPackageXml, UPDATE_PACKAGE_XPATH));
    }

    @Override
    protected void tearDown() throws Exception {
        // Clean up.
        if (mPackageXml != null) {
            FileUtil.deleteFile(mPackageXml);
        }
    }

    /**
     * Get the absolute file system location of test app with given filename
     *
     * @param fileName the file name of the test app apk
     * @return {@link String} of absolute file path
     */
    public File getTestAppFilePath(String fileName) {
        File file = FileUtil.getFileForPath(mTestAppRepositoryPath, fileName);
        CLog.d("Test path : %s", file.getAbsolutePath());
        return file;
    }

    /**
     * Helper method used to determine if the flag has been set or not.
     *
     * @param xmlFile
     * @param flagXPathString
     * @param expectedValue
     * @return
     */
    private boolean expectFlag(File xmlFile, String flagXPathString, int expectedValue) {
        int flagValue = mUtils.getIntForXPath(xmlFile, flagXPathString);
        return ((flagValue&expectedValue) == expectedValue);
    }

    /**
     * Test case when system app added is newer than update.
     * <p/>
     * Assumes adb is running as root in device under test.
     *
     * @throws DeviceNotAvailableException
     */
    public void testSystemAppAddedNewerThanUpdate() throws DeviceNotAvailableException {
        mUtils.installFile(getTestAppFilePath(VERSION_1_APK), true);
        mPackageXml = mUtils.pullPackagesXML();
        assertNotNull("Failed to pull packages xml file from device", mPackageXml);
        assertTrue("Initial package should be installed",
                mUtils.expectExists(mPackageXml, PACKAGE_XPATH));
        assertTrue("Package version should be 1",
                mUtils.expectEquals(mPackageXml, VERSION_XPATH, "1"));
        assertFalse("Updated-package should not be present",
                mUtils.expectExists(mPackageXml, UPDATE_PACKAGE_XPATH));
        assertTrue("Package should not have FLAG_SYSTEM", expectFlag(mPackageXml, FLAG_XPATH, 0));
        assertTrue("VIBRATE permission should be granted",
                mUtils.packageHasPermission(PACKAGE_NAME, VIBRATE_PERMISSION));
        assertFalse("ACCESS_CACHE_FILESYSTEM permission should NOT be granted",
                mUtils.packageHasPermission(PACKAGE_NAME, CACHE_PERMISSION));

        mUtils.pushSystemApp(getTestAppFilePath(VERSION_2_APK), SYSTEM_APP_PATH);
        mPackageXml = mUtils.pullPackagesXML();
        assertTrue("After system app push, package should still be installed",
                mUtils.expectExists(mPackageXml, PACKAGE_XPATH));
        assertTrue("After system app push, system app should be visible",
                mUtils.expectEquals(mPackageXml, VERSION_XPATH, "2"));
        assertFalse("Updated-package should not be present",
                mUtils.expectExists(mPackageXml, UPDATE_PACKAGE_XPATH));
        assertTrue("Package should have FLAG_SYSTEM", expectFlag(mPackageXml, FLAG_XPATH, 1));
        assertTrue("VIBRATE permission should be granted",
                mUtils.packageHasPermission(PACKAGE_NAME, VIBRATE_PERMISSION));
        assertTrue("ACCESS_CACHE_FILESYSTEM permission should be granted",
                mUtils.packageHasPermission(PACKAGE_NAME, CACHE_PERMISSION));
    }

    /**
     * Test case when system app added is older than update.
     * <p/>
     * Assumes adb is running as root in device under test.
     *
     * @throws DeviceNotAvailableException
     */
    public void testSystemAppAddedOlderThanUpdate() throws DeviceNotAvailableException {
        mUtils.installFile(getTestAppFilePath(VERSION_2_APK), true);
        mPackageXml = mUtils.pullPackagesXML();
        assertNotNull("Failed to pull packages xml file from device", mPackageXml);
        assertTrue("Initial package should be installed",
                mUtils.expectExists(mPackageXml, PACKAGE_XPATH));
        assertTrue("Package version should be 2",
                mUtils.expectEquals(mPackageXml, VERSION_XPATH, "2"));
        assertFalse("Updated-package should not be present",
                mUtils.expectExists(mPackageXml, UPDATE_PACKAGE_XPATH));
        assertFalse("package should not have FLAG_SYSTEM", expectFlag(mPackageXml, FLAG_XPATH, 1));
        assertTrue("VIBRATE permission should be granted",
                mUtils.packageHasPermission(PACKAGE_NAME, VIBRATE_PERMISSION));
        assertFalse("ACCESS_CACHE_FILESYSTEM permission should NOT be granted",
                mUtils.packageHasPermission(PACKAGE_NAME, CACHE_PERMISSION));

        mUtils.pushSystemApp(getTestAppFilePath(VERSION_1_APK), SYSTEM_APP_PATH);
        mPackageXml = mUtils.pullPackagesXML();
        assertTrue("After system app push, package should still be installed",
                mUtils.expectExists(mPackageXml, PACKAGE_XPATH));
        assertTrue("After system app push, system app should be visible",
                mUtils.expectEquals(mPackageXml, VERSION_XPATH, "2"));
        assertTrue("Updated-package should be present",
                mUtils.expectExists(mPackageXml, UPDATE_PACKAGE_XPATH));
        assertTrue("Package should have FLAG_SYSTEM", expectFlag(mPackageXml, FLAG_XPATH, 1));
        assertTrue("VIBRATE permission should be granted",
                mUtils.packageHasPermission(PACKAGE_NAME, VIBRATE_PERMISSION));
        assertTrue("ACCESS_CACHE_FILESYSTEM permission should be granted",
                mUtils.packageHasPermission(PACKAGE_NAME, CACHE_PERMISSION));
    }

    /**
     * Test when system app gets removed.
     * <p/>
     * Assumes adb is running as root in device under test.
     *
     * @throws DeviceNotAvailableException
     */
    public void testSystemAppRemoved() throws DeviceNotAvailableException {
        mUtils.pushSystemApp(getTestAppFilePath(VERSION_1_APK), SYSTEM_APP_PATH);
        mPackageXml = mUtils.pullPackagesXML();
        assertTrue("Initial package should be installed",
                mUtils.expectExists(mPackageXml, PACKAGE_XPATH));
        assertTrue("Package should have FLAG_SYSTEM", expectFlag(mPackageXml, FLAG_XPATH, 1));
        assertFalse("Updated-package should not be present",
                mUtils.expectExists(mPackageXml, UPDATE_PACKAGE_XPATH));
        mUtils.removeSystemApp(SYSTEM_APK, true);
        mPackageXml = mUtils.pullPackagesXML();
        assertFalse("Package should not be installed",
                mUtils.expectExists(mPackageXml, PACKAGE_XPATH));
        assertFalse("Updated-package should not be present",
                mUtils.expectExists(mPackageXml, UPDATE_PACKAGE_XPATH));
    }

    /**
     * Test when update has a newer version.
     * <p/>
     * Assumes adb is running as root in device under test.
     *
     * @throws DeviceNotAvailableException
     */
    public void testSystemAppUpdatedNewerVersion() throws DeviceNotAvailableException {
        mUtils.pushSystemApp(getTestAppFilePath(VERSION_2_APK), SYSTEM_APP_PATH);
        mPackageXml = mUtils.pullPackagesXML();
        assertTrue("The package should be installed",
                mUtils.expectExists(mPackageXml, PACKAGE_XPATH));
        assertTrue("Package version should be 2",
                mUtils.expectEquals(mPackageXml, VERSION_XPATH, "2"));
        assertFalse("Updated-package should not be present",
                mUtils.expectExists(mPackageXml, UPDATE_PACKAGE_XPATH));
        assertTrue("Package should have FLAG_SYSTEM", expectFlag(mPackageXml, FLAG_XPATH, 1));
        assertTrue("VIBRATE permission should be granted",
                mUtils.packageHasPermission(PACKAGE_NAME, VIBRATE_PERMISSION));
        assertTrue("ACCESS_CACHE_FILESYSTEM permission should be granted",
                mUtils.packageHasPermission(PACKAGE_NAME, CACHE_PERMISSION));

        mUtils.installFile(getTestAppFilePath(VERSION_3_APK), true);
        mPackageXml = mUtils.pullPackagesXML();
        assertFalse("After system app upgrade, the path should be the upgraded app on /data",
                mUtils.expectEquals(mPackageXml, CODE_PATH_XPATH, SYSTEM_APP_PATH));
        assertTrue("Package version should be 3",
                mUtils.expectEquals(mPackageXml, VERSION_XPATH, "3"));
        assertTrue("Updated-package should be present",
                mUtils.expectExists(mPackageXml, UPDATE_PACKAGE_XPATH));
        assertTrue("Package should have FLAG_SYSTEM", expectFlag(mPackageXml, FLAG_XPATH, 1));
        assertTrue("VIBRATE permission should be granted",
                mUtils.packageHasPermission(PACKAGE_NAME, VIBRATE_PERMISSION));
        assertTrue("ACCESS_CACHE_FILESYSTEM permission should be granted",
                mUtils.packageHasPermission(PACKAGE_NAME, CACHE_PERMISSION));

        getDevice().reboot();
        mPackageXml = mUtils.pullPackagesXML();
        assertFalse("After system app upgrade, the path should be the upgraded app on /data",
                mUtils.expectEquals(mPackageXml, CODE_PATH_XPATH, SYSTEM_APP_PATH));
        assertTrue("Package version should be 3",
                mUtils.expectEquals(mPackageXml, VERSION_XPATH, "3"));
        assertTrue("Updated-package should be present",
                mUtils.expectExists(mPackageXml, UPDATE_PACKAGE_XPATH));
        assertTrue("Package should have FLAG_SYSTEM", expectFlag(mPackageXml, FLAG_XPATH, 1));
        assertTrue("VIBRATE permission should be granted",
                mUtils.packageHasPermission(PACKAGE_NAME, VIBRATE_PERMISSION));
        assertTrue("ACCESS_CACHE_FILESYSTEM permission should be granted",
                mUtils.packageHasPermission(PACKAGE_NAME, CACHE_PERMISSION));

        getDevice().reboot();
        mPackageXml = mUtils.pullPackagesXML();
        assertFalse("After system app upgrade, the path should be the upgraded app on /data",
                mUtils.expectEquals(mPackageXml, CODE_PATH_XPATH, SYSTEM_APP_PATH));
        assertTrue("Package version should be 3",
                mUtils.expectEquals(mPackageXml, VERSION_XPATH, "3"));
        assertTrue("Updated-package should be present",
                mUtils.expectExists(mPackageXml, UPDATE_PACKAGE_XPATH));
        assertTrue("Package should have FLAG_SYSTEM", expectFlag(mPackageXml, FLAG_XPATH, 1));
        assertTrue("VIBRATE permission should be granted",
                mUtils.packageHasPermission(PACKAGE_NAME, VIBRATE_PERMISSION));
        assertTrue("ACCESS_CACHE_FILESYSTEM permission should be granted",
                mUtils.packageHasPermission(PACKAGE_NAME, CACHE_PERMISSION));
    }

    /**
     * Test when update has an older version.
     * <p/>
     * Assumes adb is running as root in device under test.
     *
     * @throws DeviceNotAvailableException
     */
    public void testSystemAppUpdatedOlderVersion() throws DeviceNotAvailableException {
        mUtils.pushSystemApp(getTestAppFilePath(VERSION_2_APK), SYSTEM_APP_PATH);
        mPackageXml = mUtils.pullPackagesXML();
        assertTrue("After system app push, the package should be installed",
                mUtils.expectExists(mPackageXml, PACKAGE_XPATH));
        assertTrue("Package version should be 2",
                mUtils.expectEquals(mPackageXml, VERSION_XPATH, "2"));
        assertFalse("Updated-package should not be present",
                mUtils.expectExists(mPackageXml, UPDATE_PACKAGE_XPATH));
        assertTrue("Package should have FLAG_SYSTEM", expectFlag(mPackageXml, FLAG_XPATH, 1));
        assertTrue("VIBRATE permission should be granted",
                mUtils.packageHasPermission(PACKAGE_NAME, VIBRATE_PERMISSION));
        assertTrue("ACCESS_CACHE_FILESYSTEM permission should be granted",
                mUtils.packageHasPermission(PACKAGE_NAME, CACHE_PERMISSION));

        // The "-d" command forces a downgrade.
        mUtils.installFile(getTestAppFilePath(VERSION_1_APK), true, "-d");
        mPackageXml = mUtils.pullPackagesXML();
        assertTrue("After system app upgrade, the path should be the upgraded app on /data",
                mUtils.expectStartsWith(mPackageXml, CODE_PATH_XPATH, 
                DATA_APP_DIRECTORY + PACKAGE_NAME));
        assertTrue("Package version should be 1",
                mUtils.expectEquals(mPackageXml, VERSION_XPATH, "1"));
        assertTrue("Updated-package should be present",
                mUtils.expectExists(mPackageXml, UPDATE_PACKAGE_XPATH));
        assertTrue("Package should have FLAG_SYSTEM", expectFlag(mPackageXml, FLAG_XPATH, 1));
        assertTrue("VIBRATE permission should be granted",
                mUtils.packageHasPermission(PACKAGE_NAME, VIBRATE_PERMISSION));
        assertTrue("ACCESS_CACHE_FILESYSTEM permission should be granted",
                mUtils.packageHasPermission(PACKAGE_NAME, CACHE_PERMISSION));

        getDevice().reboot();
        mPackageXml = mUtils.pullPackagesXML();
        assertTrue("After reboot, the path should be the be installed",
                mUtils.expectEquals(mPackageXml, CODE_PATH_XPATH, SYSTEM_APP_PATH));
        assertTrue("Package version should be 2",
                mUtils.expectEquals(mPackageXml, VERSION_XPATH, "2"));
        assertFalse("Updated-package should NOT be present",
                mUtils.expectExists(mPackageXml, UPDATE_PACKAGE_XPATH));
        assertTrue("Package should have FLAG_SYSTEM", expectFlag(mPackageXml, FLAG_XPATH, 1));
        assertTrue("VIBRATE permission should be granted",
                mUtils.packageHasPermission(PACKAGE_NAME, VIBRATE_PERMISSION));
        assertTrue("ACCESS_CACHE_FILESYSTEM permission should be granted",
                mUtils.packageHasPermission(PACKAGE_NAME, CACHE_PERMISSION));

        getDevice().reboot();
        mPackageXml = mUtils.pullPackagesXML();
        assertTrue("After reboot, the path should be the be installed",
                mUtils.expectEquals(mPackageXml, CODE_PATH_XPATH, SYSTEM_APP_PATH));
        assertTrue("Package version should be 2",
                mUtils.expectEquals(mPackageXml, VERSION_XPATH, "2"));
        assertFalse("Updated-package should NOT be present",
                mUtils.expectExists(mPackageXml, UPDATE_PACKAGE_XPATH));
        assertTrue("Package should have FLAG_SYSTEM", expectFlag(mPackageXml, FLAG_XPATH, 1));
        assertTrue("VIBRATE permission should be granted",
                mUtils.packageHasPermission(PACKAGE_NAME, VIBRATE_PERMISSION));
        assertTrue("ACCESS_CACHE_FILESYSTEM permission should be granted",
                mUtils.packageHasPermission(PACKAGE_NAME, CACHE_PERMISSION));
    }

    /**
     * Test when update has the same version.
     * <p/>
     * Assumes adb is running as root in device under test.
     *
     * @throws DeviceNotAvailableException
     */
    public void testSystemAppUpdatedSameVersion() throws DeviceNotAvailableException {
        mUtils.pushSystemApp(getTestAppFilePath(VERSION_2_APK), SYSTEM_APP_PATH);
        mPackageXml = mUtils.pullPackagesXML();
        assertTrue("The package should be installed",
                mUtils.expectExists(mPackageXml, PACKAGE_XPATH));
        assertTrue("Package version should be 2",
                mUtils.expectEquals(mPackageXml, VERSION_XPATH, "2"));
        assertFalse("Updated-package should not be present",
                mUtils.expectExists(mPackageXml, UPDATE_PACKAGE_XPATH));
        assertTrue("Package should have FLAG_SYSTEM", expectFlag(mPackageXml, FLAG_XPATH, 1));
        assertTrue("VIBRATE permission should be granted",
                mUtils.packageHasPermission(PACKAGE_NAME, VIBRATE_PERMISSION));
        assertTrue("ACCESS_CACHE_FILESYSTEM permission should be granted",
                mUtils.packageHasPermission(PACKAGE_NAME, CACHE_PERMISSION));

        mUtils.installFile(getTestAppFilePath(VERSION_2_APK), true);
        mPackageXml = mUtils.pullPackagesXML();
        assertFalse("After system app upgrade, the path should be the upgraded app on /data",
                mUtils.expectEquals(mPackageXml, CODE_PATH_XPATH, SYSTEM_APP_PATH));
        assertTrue("Package version should be 2",
                mUtils.expectEquals(mPackageXml, VERSION_XPATH, "2"));
        assertTrue("Updated-package should be present",
                mUtils.expectExists(mPackageXml, UPDATE_PACKAGE_XPATH));
        assertTrue("Package should have FLAG_SYSTEM", expectFlag(mPackageXml, FLAG_XPATH, 1));
        assertTrue("VIBRATE permission should be granted",
                mUtils.packageHasPermission(PACKAGE_NAME, VIBRATE_PERMISSION));
        assertTrue("ACCESS_CACHE_FILESYSTEM permission should be granted",
                mUtils.packageHasPermission(PACKAGE_NAME, CACHE_PERMISSION));

        getDevice().reboot();
        mPackageXml = mUtils.pullPackagesXML();
        assertTrue("After reboot, the path should be the be installed",
                mUtils.expectEquals(mPackageXml, CODE_PATH_XPATH, SYSTEM_APP_PATH));
        assertTrue("Package version should be 2",
                mUtils.expectEquals(mPackageXml, VERSION_XPATH, "2"));
        assertFalse("Updated-package should NOT be present",
                mUtils.expectExists(mPackageXml, UPDATE_PACKAGE_XPATH));
        assertTrue("Package should have FLAG_SYSTEM", expectFlag(mPackageXml, FLAG_XPATH, 1));
        assertTrue("VIBRATE permission should be granted",
                mUtils.packageHasPermission(PACKAGE_NAME, VIBRATE_PERMISSION));
        assertTrue("ACCESS_CACHE_FILESYSTEM permission should be granted",
                mUtils.packageHasPermission(PACKAGE_NAME, CACHE_PERMISSION));

        getDevice().reboot();
        mPackageXml = mUtils.pullPackagesXML();
        assertTrue("After reboot, the path should be the be installed",
                mUtils.expectEquals(mPackageXml, CODE_PATH_XPATH, SYSTEM_APP_PATH));
        assertTrue("Package version should be 2",
                mUtils.expectEquals(mPackageXml, VERSION_XPATH, "2"));
        assertFalse("Updated-package should NOT be present",
                mUtils.expectExists(mPackageXml, UPDATE_PACKAGE_XPATH));
        assertTrue("Package should have FLAG_SYSTEM", expectFlag(mPackageXml, FLAG_XPATH, 1));
        assertTrue("VIBRATE permission should be granted",
                mUtils.packageHasPermission(PACKAGE_NAME, VIBRATE_PERMISSION));
        assertTrue("ACCESS_CACHE_FILESYSTEM permission should be granted",
                mUtils.packageHasPermission(PACKAGE_NAME, CACHE_PERMISSION));
    }

    /**
     * Test when update has a different filename.
     * <p/>
     * Assumes adb is running as root in device under test.
     *
     * @throws DeviceNotAvailableException
     */
    public void testUpdatedSystemAppChangeFileName() throws DeviceNotAvailableException {
        mUtils.pushSystemApp(getTestAppFilePath(VERSION_1_APK), SYSTEM_APP_PATH);
        mPackageXml = mUtils.pullPackagesXML();
        assertNotNull("Failed to pull packages xml file from device", mPackageXml);
        assertTrue("After system app push, the package should be installed",
                mUtils.expectExists(mPackageXml, PACKAGE_XPATH));
        assertTrue("Package version should be 1",
                mUtils.expectEquals(mPackageXml, VERSION_XPATH, "1"));
        assertFalse("Updated-package should not be present",
                mUtils.expectExists(mPackageXml, UPDATE_PACKAGE_XPATH));
        assertTrue("Package should have FLAG_SYSTEM", expectFlag(mPackageXml, FLAG_XPATH, 1));
        assertTrue("VIBRATE permission should be granted",
                mUtils.packageHasPermission(PACKAGE_NAME, VIBRATE_PERMISSION));
        assertTrue("ACCESS_CACHE_FILESYSTEM permission should be granted",
                mUtils.packageHasPermission(PACKAGE_NAME, CACHE_PERMISSION));

        mUtils.installFile(getTestAppFilePath(VERSION_2_APK), true);
        mPackageXml = mUtils.pullPackagesXML();
        assertTrue("After system app upgrade, the path should be the upgraded app on /data",
                mUtils.expectStartsWith(mPackageXml, CODE_PATH_XPATH,
                DATA_APP_DIRECTORY + PACKAGE_NAME));
        assertTrue("Package version should be 2",
                mUtils.expectEquals(mPackageXml, VERSION_XPATH, "2"));
        assertTrue("Updated-package should be present",
                mUtils.expectExists(mPackageXml, UPDATE_PACKAGE_XPATH));
        assertTrue("Package should have FLAG_SYSTEM", expectFlag(mPackageXml, FLAG_XPATH, 1));
        assertTrue("VIBRATE permission should be granted",
                mUtils.packageHasPermission(PACKAGE_NAME, VIBRATE_PERMISSION));
        assertTrue("ACCESS_CACHE_FILESYSTEM permission should be granted",
                mUtils.packageHasPermission(PACKAGE_NAME, CACHE_PERMISSION));

        mUtils.removeSystemApp(SYSTEM_APK, false);
        mUtils.pushSystemApp(getTestAppFilePath(VERSION_2_APK), DIFF_SYSTEM_APP_PATH);

        mPackageXml = mUtils.pullPackagesXML();
        assertTrue("After reboot, the system path should be correct",
                mUtils.expectEquals(mPackageXml, CODE_PATH_XPATH, DIFF_SYSTEM_APP_PATH));
        assertTrue("Package version should be 2",
                mUtils.expectEquals(mPackageXml, VERSION_XPATH, "2"));
        assertFalse("Updated-package should not be present",
                mUtils.expectExists(mPackageXml, UPDATE_PACKAGE_XPATH));
        assertTrue("Package should have FLAG_SYSTEM", expectFlag(mPackageXml, FLAG_XPATH, 1));
        assertTrue("VIBRATE permission should be granted",
                mUtils.packageHasPermission(PACKAGE_NAME, VIBRATE_PERMISSION));
        assertTrue("ACCESS_CACHE_FILESYSTEM permission should be granted",
                mUtils.packageHasPermission(PACKAGE_NAME, CACHE_PERMISSION));
    }

    /**
     * Test when update has system app removed.
     * <p/>
     * Assumes adb is running as root in device under test.
     *
     * @throws DeviceNotAvailableException
     */
    public void testUpdatedSystemAppRemoved() throws DeviceNotAvailableException {
        mUtils.pushSystemApp(getTestAppFilePath(VERSION_1_APK), SYSTEM_APP_PATH);
        mUtils.installFile(getTestAppFilePath(VERSION_2_APK), true);
        mPackageXml = mUtils.pullPackagesXML();
        assertTrue("Package should be installed",
                mUtils.expectExists(mPackageXml, PACKAGE_XPATH));
        assertTrue("Updated-package should be present",
                mUtils.expectExists(mPackageXml, UPDATE_PACKAGE_XPATH));
        assertTrue("Package should have FLAG_SYSTEM", expectFlag(mPackageXml, FLAG_XPATH, 1));
        assertTrue("VIBRATE permission should be granted",
                mUtils.packageHasPermission(PACKAGE_NAME, VIBRATE_PERMISSION));
        assertTrue("ACCESS_CACHE_FILESYSTEM permission should be granted",
                mUtils.packageHasPermission(PACKAGE_NAME, CACHE_PERMISSION));

        mUtils.removeSystemApp(SYSTEM_APK, true);
        mPackageXml = mUtils.pullPackagesXML();
        assertTrue("Package should still be installed",
                mUtils.expectExists(mPackageXml, PACKAGE_XPATH));
        assertFalse("Updated-package entry should not be present",
                mUtils.expectExists(mPackageXml, UPDATE_PACKAGE_XPATH));
        assertFalse("Package should not have FLAG_SYSTEM", expectFlag(mPackageXml, FLAG_XPATH, 1));
        assertTrue("VIBRATE permission should be granted",
                mUtils.packageHasPermission(PACKAGE_NAME, VIBRATE_PERMISSION));
        assertFalse("ACCESS_CACHE_FILESYSTEM permission should NOT be granted",
                mUtils.packageHasPermission(PACKAGE_NAME, CACHE_PERMISSION));
    }
}
