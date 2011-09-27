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

package com.android.framework.tests;

import com.android.ddmlib.Log;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.Option.Importance;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.util.FileUtil;

import java.io.File;

/**
 * Set of tests that verify host side stress scenarios (large apps, multiple upgrades, etc.)
 */
public class PackageManagerStressHostTests extends DeviceTestCase {

    private static final String LOG_TAG = "PackageManagerStressHostTests";
    private PackageManagerHostTestUtils mPMHostUtils = null;

    // Path to the app repository and various subdirectories of it
    // Note: These stress tests require large apks that cannot be checked into the tree.
    // These variables define static locations that point to existing APKs (not built from
    // the tree) which can be used by the the stress tests in this file.
    private static final String LARGE_APPS_DIRECTORY_NAME = "largeApps";
    private static final String MISC_APPS_DIRECTORY_NAME = "miscApps";
    private static final String VERSIONED_APPS_DIRECTORY_NAME = "versionedApps";
    private static final String MANY_APPS_DIRECTORY_NAME = "manyApps";

    // Large apps (>1mb) - filenames and their corresponding package names:
    private static enum APK {
            FILENAME,
            PACKAGENAME;
    }
    private static final String[][] LARGE_APPS = {
       {"External1mb.apk", "com.appsonsd.mytests.External1mb"},
       {"External2mb.apk", "com.appsonsd.mytests.External2mb"},
       {"External3mb.apk", "com.appsonsd.mytests.External3mb"},
       {"External4mb.apk", "com.appsonsd.mytests.External4mb"},
       {"External5mb.apk", "com.appsonsd.mytests.External5mb"},
       {"External6mb.apk", "com.appsonsd.mytests.External6mb"},
       {"External7mb.apk", "com.appsonsd.mytests.External7mb"},
       {"External8mb.apk", "com.appsonsd.mytests.External8mb"},
       {"External9mb.apk", "com.appsonsd.mytests.External9mb"},
       {"External10mb.apk", "com.appsonsd.mytests.External10mb"},
       {"External16mb.apk", "com.appsonsd.mytests.External16mb"},
       {"External28mb.apk", "com.appsonsd.mytests.External28mb"},
       {"External34mb.apk", "com.appsonsd.mytests.External34mb"},
       {"External46mb.apk", "com.appsonsd.mytests.External46mb"},
       {"External58mb.apk", "com.appsonsd.mytests.External58mb"},
       {"External65mb.apk", "com.appsonsd.mytests.External65mb"},
       {"External72mb.apk", "com.appsonsd.mytests.External72mb"},
       {"External79mb.apk", "com.appsonsd.mytests.External79mb"},
       {"External86mb.apk", "com.appsonsd.mytests.External86mb"},
       {"External93mb.apk", "com.appsonsd.mytests.External93mb"}};

    // Various test files and their corresponding package names
    @SuppressWarnings("unused")
    private static final String AUTO_LOC_APK = "Auto241kb.apk";
    @SuppressWarnings("unused")
    private static final String AUTO_LOC_PKG = "com.appsonsd.mytests.Auto241kb";
    @SuppressWarnings("unused")
    private static final String INTERNAL_LOC_APK = "Internal781kb.apk";
    @SuppressWarnings("unused")
    private static final String INTERNAL_LOC_PKG = "com.appsonsd.mytests.Internal781kb";
    private static final String EXTERNAL_LOC_APK = "External931kb.apk";
    private static final String EXTERNAL_LOC_PKG = "com.appsonsd.mytests.External931kb";
    @SuppressWarnings("unused")
    private static final String NO_LOC_APK = "Internal751kb_EclairSDK.apk";
    @SuppressWarnings("unused")
    private static final String NO_LOC_PKG = "com.appsonsd.mytests.Internal751kb_EclairSDK";
    // Versioned test apps
    private static final String VERSIONED_APPS_FILENAME_PREFIX = "External455kb_v";
    private static final String VERSIONED_APPS_PKG = "com.appsonsd.mytests.External455kb";
    private static final int VERSIONED_APPS_START_VERSION = 1;  // inclusive
    private static final int VERSIONED_APPS_END_VERSION = 250;  // inclusive
    // Large number of app installs
    // @TODO: increase the max when we can install more apps
    private static final int MANY_APPS_START = 1;
    private static final int MANY_APPS_END = 100;
    private static final String MANY_APPS_PKG_PREFIX = "com.appsonsd.mytests.External49kb_";
    private static final String MANY_APPS_APK_PREFIX = "External49kb_";

    @Option(name = "test-app-path", description = "host file system path to test apps",
            importance = Importance.IF_UNSET)
    private File mTestAppPath = null;

    @Option(name = "app-repository-path", description =
            "path to the app repository containing large apks", importance = Importance.IF_UNSET)
    private File mAppRepositoryPath = null;

    /**
     * Get the host file to the package manager device-based test apps dir
     */
    private File getTestAppPath() {
        return mTestAppPath;
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        // setup the PackageManager host tests utilities class, and get various paths we'll need...
        mPMHostUtils = new PackageManagerHostTestUtils(getDevice());
        // ensure apk path has been set before test is run
        assertNotNull("Missing --test-app-path option", getTestAppPath());
        assertNotNull("Missing --app-repository-path option", mAppRepositoryPath);
    }

    /**
     * Get the {@link File} of repository test app with given filename
     * @param fileName the file name of the test app apk
     * @return {@link File}
     */
    private File getRepositoryTestAppFilePath(String fileDirectory, String fileName) {
        return FileUtil.getFileForPath(mAppRepositoryPath, fileDirectory, fileName);
    }

    /**
     * Get the {@link File} for test app with given filename
     * @param fileName the file name of the test app apk
     * @return {@link File}
     */
    public File getTestAppFilePath(String fileName) {
        return FileUtil.getFileForPath(getTestAppPath(), fileName);
    }

    /**
     * Stress test to verify that we can update an app multiple times on the SD card.
     * <p/>
     * Assumes adb is running as root in device under test.
     */
    public void testUpdateAppManyTimesOnSD() throws Exception {
        Log.i(LOG_TAG, "Test updating an app on SD numerous times");

        // cleanup test app just in case it already exists
        mPMHostUtils.uninstallApp(VERSIONED_APPS_PKG);
        // grep for package to make sure its not installed
        assertFalse(mPMHostUtils.doesPackageExist(VERSIONED_APPS_PKG));

        try {
            for (int i = VERSIONED_APPS_START_VERSION; i <= VERSIONED_APPS_END_VERSION; ++i) {
                String currentApkName = String.format("%s%d.apk",
                        VERSIONED_APPS_FILENAME_PREFIX, i);

                Log.i(LOG_TAG, "Installing app " + currentApkName);
                mPMHostUtils.installFile(getRepositoryTestAppFilePath(VERSIONED_APPS_DIRECTORY_NAME,
                        currentApkName), true);
                mPMHostUtils.waitForPackageManager();
                assertTrue(mPMHostUtils.doesAppExistOnSDCard(VERSIONED_APPS_PKG));
                assertTrue(mPMHostUtils.doesPackageExist(VERSIONED_APPS_PKG));
            }
        }
        finally {
            // cleanup test app
            mPMHostUtils.uninstallApp(VERSIONED_APPS_PKG);
            // grep for package to make sure its not installed
            assertFalse(mPMHostUtils.doesPackageExist(VERSIONED_APPS_PKG));
        }
    }

    /**
     * Stress test to verify that an app can be installed, uninstalled, and
     * reinstalled on SD many times.
     * <p/>
     * Assumes adb is running as root in device under test.
     */
    public void testUninstallReinstallAppOnSDManyTimes() throws Exception {
        Log.i(LOG_TAG, "Test updating an app on the SD card stays on the SD card");

        // cleanup test app just in case it was already exists
        mPMHostUtils.uninstallApp(EXTERNAL_LOC_PKG);
        // grep for package to make sure its not installed
        assertFalse(mPMHostUtils.doesPackageExist(EXTERNAL_LOC_PKG));

        for (int i = 0; i <= 500; ++i) {
            CLog.i(LOG_TAG, "Installing app (%d)", i);

            try {
                // install the app
                mPMHostUtils.installFile(getRepositoryTestAppFilePath(MISC_APPS_DIRECTORY_NAME,
                        EXTERNAL_LOC_APK), false);
                mPMHostUtils.waitForPackageManager();
                assertTrue(mPMHostUtils.doesAppExistOnSDCard(EXTERNAL_LOC_PKG));
                assertTrue(mPMHostUtils.doesPackageExist(EXTERNAL_LOC_PKG));
            }
            finally {
                // now uninstall the app
                Log.i(LOG_TAG, "Uninstalling app");
                mPMHostUtils.uninstallApp(EXTERNAL_LOC_PKG);
                // TODO: is this needed
                mPMHostUtils.waitForPackageManager();
                assertFalse(mPMHostUtils.doesPackageExist(EXTERNAL_LOC_PKG));
            }
        }
    }

    /**
     * Stress test to verify that we can install, 20 large apps (>1mb each)
     * <p/>
     * Assumes adb is running as root in device under test.
     */
    public void testInstallManyLargeAppsOnSD() throws Exception {
        Log.i(LOG_TAG, "Test installing 20 large apps onto the sd card");

        try {
            // Install all the large apps
            for (int i=0; i < LARGE_APPS.length; ++i) {
                String apkName = LARGE_APPS[i][APK.FILENAME.ordinal()];
                String pkgName = LARGE_APPS[i][APK.PACKAGENAME.ordinal()];

                // cleanup test app just in case it already exists
                mPMHostUtils.uninstallApp(pkgName);
                // grep for package to make sure its not installed
                assertFalse(mPMHostUtils.doesPackageExist(pkgName));

                Log.i(LOG_TAG, "Installing app " + apkName);
                // install the app
                mPMHostUtils.installFile(getRepositoryTestAppFilePath(LARGE_APPS_DIRECTORY_NAME,
                        apkName), false);
                mPMHostUtils.waitForPackageManager();
                assertTrue(mPMHostUtils.doesAppExistOnSDCard(pkgName));
                assertTrue(mPMHostUtils.doesPackageExist(pkgName));
            }
        }
        finally {
            // Cleanup - ensure we uninstall all large apps if they were installed
            for (int i=0; i < LARGE_APPS.length; ++i) {
                String apkName = LARGE_APPS[i][APK.FILENAME.ordinal()];
                String pkgName = LARGE_APPS[i][APK.PACKAGENAME.ordinal()];

                Log.i(LOG_TAG, "Uninstalling app " + apkName);
                // cleanup test app just in case it was accidently installed
                mPMHostUtils.uninstallApp(pkgName);
                // grep for package to make sure its not installed anymore
                assertFalse(mPMHostUtils.doesPackageExist(pkgName));
                assertFalse(mPMHostUtils.doesAppExistOnSDCard(pkgName));
            }
        }
    }

    /**
     * Stress test to verify that we can install many small apps onto SD.
     * <p/>
     * Assumes adb is running as root in device under test.
     */
    public void testInstallManyAppsOnSD() throws Exception {
        Log.i(LOG_TAG, "Test installing 500 small apps onto SD");

        try {
            for (int i = MANY_APPS_START; i <= MANY_APPS_END; ++i) {
                String currentPkgName = String.format("%s%d", MANY_APPS_PKG_PREFIX, i);

                // cleanup test app just in case it already exists
                mPMHostUtils.uninstallApp(currentPkgName);
                // grep for package to make sure its not installed
                assertFalse(mPMHostUtils.doesPackageExist(currentPkgName));

                String currentApkName = String.format("%s%d.apk", MANY_APPS_APK_PREFIX, i);
                Log.i(LOG_TAG, "Installing app " + currentApkName);
                mPMHostUtils.installFile(getRepositoryTestAppFilePath(MANY_APPS_DIRECTORY_NAME,
                        currentApkName), true);
                mPMHostUtils.waitForPackageManager();
                assertTrue(mPMHostUtils.doesAppExistOnSDCard(currentPkgName));
                assertTrue(mPMHostUtils.doesPackageExist(currentPkgName));
            }
        }
        finally {
            for (int i = MANY_APPS_START; i <= MANY_APPS_END; ++i) {
                String currentPkgName = String.format("%s%d", MANY_APPS_PKG_PREFIX, i);

                // cleanup test app
                mPMHostUtils.uninstallApp(currentPkgName);
                // grep for package to make sure its not installed
                assertFalse(mPMHostUtils.doesPackageExist(currentPkgName));
            }
        }
    }
}