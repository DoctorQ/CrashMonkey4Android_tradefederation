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
package com.android.performance.tests;

import com.android.ddmlib.IDevice;
import com.android.ddmlib.testrunner.ITestRunListener.TestFailure;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.SnapshotInputStreamSource;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.util.RunUtil;
import com.android.tradefed.util.StreamUtil;

import junit.framework.Assert;
import junit.framework.TestCase;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Runs the app launch test.
 * <p>
 * Launches each app and records the amount of time it took to launch the app.
 * </p>
 */
public class AppLaunchMetricsTest implements IDeviceTest, IRemoteTest {
    private static final String APP_LAUNCH = "/data/framework/app_launch";
    private static final String APP_LIST_FILE = "launch_list.txt";
    private static final String APP_OUTPUT_FILE = "launch_perf_output.txt";

    private static final String TEST_KEY = "ApplicationStartupTime";

    private static final String LAUNCH_TIME_NAME = "app_launch_times";
    private static final String BUGREPORT_NAME = "app_launch_bugreport";

    /** The pattern to match the app-name argument */
    private static final Pattern APP_NAME_PATTERN = Pattern.compile("(.+),(.+)");
    /** The pattern of the output */
    private static final Pattern APP_TIME_PATTERN = Pattern.compile("(.+)\\|(\\d+)");

    private ITestDevice mTestDevice;
    private String mAppListPath = null;
    private String mAppOutputPath = null;

    @Option(name = "app-name", description = "The name of the app in the launcher or an app, key "
            + "pair.  E.G. \"Browser\" or \"Browser,android_browser\". May be repeated.")
    private Collection<String> mAppNames = new ArrayList<String>();

    /**
     * Class that stores useful info about the app.
     */
    static class AppInfo {
        private String mName = null;
        private String mOutputKey = null;
        private String mPostKey = null;
        private Integer mTime = null;

        public AppInfo(String name) {
            mName = name;
            mPostKey = mOutputKey = makeOutputKey(name);
        }

        public AppInfo(String name, String key) {
            mName = name;
            mOutputKey = makeOutputKey(name);
            mPostKey = key;
        }

        public String getAppListEntry() {
            return String.format("%s,%s\n", mName, mPostKey);
        }

        public String getName() {
            return mName;
        }

        public String getPostKey() {
            return mPostKey;
        }

        public String getOutputKey() {
            return mOutputKey;
        }

        public Integer getTime() {
            return mTime;
        }

        public void setTime(Integer time) {
            mTime = time;
        }

        private String makeOutputKey(String name) {
            return name.toLowerCase().replaceAll(" ", "");
        }
    }

    private Map<String, AppInfo> mAppInfos = new HashMap<String, AppInfo>();

    /**
     * {@inheritDoc}
     */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        Assert.assertNotNull(mTestDevice);

        mAppListPath = new File(mTestDevice.getMountPoint(IDevice.MNT_EXTERNAL_STORAGE),
                APP_LIST_FILE).getAbsolutePath();
        mAppOutputPath = new File(mTestDevice.getMountPoint(IDevice.MNT_EXTERNAL_STORAGE),
                APP_OUTPUT_FILE).getAbsolutePath();

        setupAppInfos();

        // Setup the device
        mTestDevice.executeShellCommand(String.format("rm %s %s", mAppListPath, mAppOutputPath));
        mTestDevice.pushString(generateAppList(), mAppListPath);
        mTestDevice.executeShellCommand(String.format("chmod 750 %s", APP_LAUNCH));

        // Sleep 30 seconds to let device settle.
        RunUtil.getDefault().sleep(30 * 1000);

        // Run the test
        String output = mTestDevice.executeShellCommand(APP_LAUNCH);

        CLog.d("App launch output: %s", output);
        logOutputFile(listener);
    }

    /**
     * Sets up the {@link AppInfo} map based on the app-name args.
     * <p>
     * Generates from {@link appNames}, a collection of Strings formated as either an app name or as
     * an app name, key pair with a comma separator.  The key for the map will be the lowercase name
     * with spaces removed.
     * </p>
     */
    private void setupAppInfos() {
        for (String app : mAppNames) {
            Matcher m = APP_NAME_PATTERN.matcher(app);
            AppInfo info;
            if (m.matches()) {
                info = new AppInfo(m.group(1), m.group(2));
            } else {
                info = new AppInfo(app);
            }
            mAppInfos.put(info.getOutputKey(), info);
        }
    }

    /**
     * Generate the app list as a String.
     *
     * @return the app list to push to the device.
     */
    private String generateAppList() {
        StringBuilder sb = new StringBuilder();
        for (AppInfo info : mAppInfos.values()) {
            sb.append(info.getAppListEntry());
        }
        return sb.toString();
    }

    /**
     * Parses and logs the output file.
     *
     * @param listener the {@link ITestInvocationListener}
     * @throws DeviceNotAvailableException If the device is not available.
     */
    private void logOutputFile(ITestInvocationListener listener)
            throws DeviceNotAvailableException {
        File outputFile = null;
        InputStreamSource outputSource = null;

        try {
            outputFile = mTestDevice.pullFile(mAppOutputPath);
            if (outputFile != null) {
                outputSource = new SnapshotInputStreamSource(new FileInputStream(outputFile));
                listener.testLog(LAUNCH_TIME_NAME, LogDataType.TEXT, outputSource);
                parseOutputFile(StreamUtil.getStringFromStream(new BufferedInputStream(
                        new FileInputStream(outputFile))));
            }
        } catch(IOException e) {
            CLog.e("Got IOException: %s", e);
        } finally {
            if (outputFile != null) {
                outputFile.delete();
            }
            if (outputSource != null) {
                outputSource.cancel();
            }
        }

        if (shouldTakeBugreport()) {
            InputStreamSource bugreport = mTestDevice.getBugreport();
            try {
                listener.testLog(BUGREPORT_NAME, LogDataType.TEXT, bugreport);
            } finally {
                bugreport.cancel();
            }
        }

        reportMetrics(listener);
    }

    /**
     * Parses the output file and populate the {@link AppInfo} objects with the launch times.
     *
     * @param contents The file contents.
     * @throws IOException If an IOException is caused.
     */
    private void parseOutputFile(String contents) throws IOException {
        for (String line : contents.split("\n")) {
            Matcher m = APP_TIME_PATTERN.matcher(line);
            if (m.matches()) {
                AppInfo appInfo = mAppInfos.get(m.group(1).toLowerCase());
                if (appInfo != null) {
                    appInfo.setTime(Integer.parseInt(m.group(2)));
                }
            }
        }
    }

    /**
     * Report the metrics and attach it to the listener.
     * <p>
     * If any of the app times are {@code null}, that app is assumed to not have launched and will
     * be marked as failed.
     * </p>
     * @param listener the {@link ITestInvocationListener}
     */
    private void reportMetrics(ITestInvocationListener listener) {
        listener.testRunStarted(TEST_KEY, 0);
        Map<String, String> metrics = new HashMap<String, String>();

        for (AppInfo appInfo : mAppInfos.values()) {
            TestIdentifier testId = new TestIdentifier(getClass().getCanonicalName(),
                    appInfo.getPostKey());
            listener.testStarted(testId);
            if (appInfo.getTime() != null) {
                metrics.put(appInfo.getPostKey(), Integer.toString(appInfo.getTime()));
            } else {
                listener.testFailed(TestFailure.FAILURE, testId, "No app launch time");
            }
            Map<String, String> empty = Collections.emptyMap();
            listener.testEnded(testId, empty);
        }
        CLog.d("About to report app launch metrics: %s", metrics);
        listener.testRunEnded(0, metrics);
    }

    /**
     * If a bugreport should be taken after the run.
     *
     * @return true if any of the apps have a {@code null} launch time.
     */
    private boolean shouldTakeBugreport() {
        for (AppInfo appInfo : mAppInfos.values()) {
            if (appInfo.getTime() == null) {
                return true;
            }
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setDevice(ITestDevice device) {
        mTestDevice = device;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ITestDevice getDevice() {
        return mTestDevice;
    }

    public static class MetaTest extends TestCase {
        AppLaunchMetricsTest mTestInstance = null;

        @Override
        public void setUp() throws Exception {
            mTestInstance = new AppLaunchMetricsTest();

            mTestInstance.mAppNames.add("App 1");
            mTestInstance.mAppNames.add("App 2,key2");
        }

        public void testAppInfo() throws Exception {
            AppInfo info = new AppInfo("app_name");
            assertEquals("app_name", info.getName());
            assertEquals("app_name", info.getOutputKey());
            assertEquals("app_name", info.getPostKey());

            info = new AppInfo("AppName");
            assertEquals("AppName", info.getName());
            assertEquals("appname", info.getOutputKey());
            assertEquals("appname", info.getPostKey());

            info = new AppInfo("App Name");
            assertEquals("App Name", info.getName());
            assertEquals("appname", info.getOutputKey());
            assertEquals("appname", info.getPostKey());

            info = new AppInfo("App & Name");
            assertEquals("App & Name", info.getName());
            assertEquals("app&name", info.getOutputKey());
            assertEquals("app&name", info.getPostKey());
            assertEquals("App & Name,app&name\n", info.getAppListEntry());

            info = new AppInfo("App Name", "key");
            assertEquals("App Name", info.getName());
            assertEquals("appname", info.getOutputKey());
            assertEquals("key", info.getPostKey());

            assertNull(info.getTime());
            info.setTime(0);
            assertEquals(new Integer(0), info.getTime());
            assertEquals("App Name,key\n", info.getAppListEntry());
        }

        public void testSetupAppInfos() throws Exception {
            mTestInstance.setupAppInfos();
            assertEquals(2, mTestInstance.mAppInfos.size());
            assertNotNull(mTestInstance.mAppInfos.get("app1"));
            assertEquals("App 1", mTestInstance.mAppInfos.get("app1").getName());
            assertEquals("app1", mTestInstance.mAppInfos.get("app1").getOutputKey());
            assertEquals("app1", mTestInstance.mAppInfos.get("app1").getPostKey());
            assertNotNull(mTestInstance.mAppInfos.get("app2"));
            assertEquals("App 2", mTestInstance.mAppInfos.get("app2").getName());
            assertEquals("app2", mTestInstance.mAppInfos.get("app2").getOutputKey());
            assertEquals("key2", mTestInstance.mAppInfos.get("app2").getPostKey());
        }

        public void testGenerateAppList() throws Exception {
            mTestInstance.setupAppInfos();
            assertEquals(2, mTestInstance.mAppInfos.size());

            assertTrue(mTestInstance.generateAppList().contains("App 1,app1\n"));
            assertTrue(mTestInstance.generateAppList().contains("App 2,key2\n"));
        }

        public void testParseOutputFile_success() throws Exception {
            mTestInstance.setupAppInfos();
            assertEquals(2, mTestInstance.mAppInfos.size());

            mTestInstance.parseOutputFile("app1|1234\napp2|5678\n");
            assertFalse(mTestInstance.shouldTakeBugreport());
            assertEquals(new Integer(1234), mTestInstance.mAppInfos.get("app1").getTime());
            assertEquals(new Integer(5678), mTestInstance.mAppInfos.get("app2").getTime());
        }

        public void testParseOutputFile_fail() throws Exception {
            mTestInstance.setupAppInfos();
            assertEquals(2, mTestInstance.mAppInfos.size());

            mTestInstance.parseOutputFile("app1|1234\n");
            assertTrue(mTestInstance.shouldTakeBugreport());
            assertEquals(new Integer(1234), mTestInstance.mAppInfos.get("app1").getTime());
            assertNull(mTestInstance.mAppInfos.get("app2").getTime());
        }
    }
}
