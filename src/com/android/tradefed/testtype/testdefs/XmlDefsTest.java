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
package com.android.tradefed.testtype.testdefs;

import com.android.ddmlib.Log;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.testtype.AbstractRemoteTest;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IResumableTest;
import com.android.tradefed.testtype.InstrumentationTest;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.xml.AbstractXmlParser.ParseException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Runs a set of instrumentation test's defined in test_defs.xml files.
 * <p/>
 * The test definition files can either be one or more files on local file system, and/or one or
 * more files stored on the device under test.
 */
public class XmlDefsTest extends AbstractRemoteTest implements IDeviceTest, IResumableTest {

    private static final String LOG_TAG = "XmlDefsTest";

    /** the metric key name for the test coverage target value */
    // TODO: move this to a more generic location
    public static final String COVERAGE_TARGET_KEY = "coverage_target";

    private ITestDevice mDevice;

    @Option(name = "timeout",
            description = "Fail any test that takes longer than the specified number of "
            + "milliseconds. Default 10 min.")
    private long mTestTimeout = 10 * 60 * 1000;  // default to 10 minutes

    @Option(name = "size",
            description = "Restrict tests to a specific test size")
    private String mTestSize = null;

    @Option(name = "rerun",
            description = "Rerun non-executed tests individually if test run fails to complete. Default true")
    private boolean mIsRerunMode = true;

    @Option(name = "local-file-path",
            description = "local file path to test_defs.xml file to run")
    private Collection<File> mLocalFiles = new ArrayList<File>();

    @Option(name = "device-file-path",
            description = "file path on device to test_defs.xml file to run")
    private Collection<String> mRemotePaths = new ArrayList<String>();

    @Option(name = "send-coverage",
            description = "Send coverage target info to test listeners. Default true.")
    private boolean mSendCoverage = true;

    private List<InstrumentationTestDef> mTestDefs = null;
    private InstrumentationTest mCurrentTest = null;

    public XmlDefsTest() {
    }

    /**
     * {@inheritDoc}
     */
    public ITestDevice getDevice() {
        return mDevice;
    }

    /**
     * {@inheritDoc}
     */
    public void setDevice(ITestDevice device) {
        mDevice = device;
    }

    /**
     * Adds a remote test def file path. Exposed for unit testing.
     */
    void addRemoteFilePath(String path) {
        mRemotePaths.add(path);
    }

    /**
     * {@inheritDoc}
     */
    public void run(List<ITestInvocationListener> listeners) throws DeviceNotAvailableException {
        if (getDevice() == null) {
            throw new IllegalArgumentException("Device has not been set");
        }
        if (mLocalFiles.isEmpty() && mRemotePaths.isEmpty()) {
            throw new IllegalArgumentException("No test definition files (local-file-path or " +
                    "device-file-path) have been provided.");
        }
        mLocalFiles.addAll(getRemoteFile(mRemotePaths));
        XmlDefsParser parser = createParser();
        for (File testDefFile : mLocalFiles) {
            try {
                Log.i(LOG_TAG, String.format("Parsing test def file %s",
                        testDefFile.getAbsolutePath()));
                parser.parse(new FileInputStream(testDefFile));
            } catch (FileNotFoundException e) {
                Log.e(LOG_TAG, String.format("Could not find test def file %s",
                        testDefFile.getAbsolutePath()));
            } catch (ParseException e) {
                Log.e(LOG_TAG, String.format("Could not parse test def file %s: %s",
                        testDefFile.getAbsolutePath(), e.getMessage()));
            } finally {
                testDefFile.delete();
            }
        }
        mTestDefs = new LinkedList<InstrumentationTestDef>(parser.getTestDefs());
        doRun(listeners);
    }

    /**
     * @param listeners
     * @throws DeviceNotAvailableException
     */
    private void doRun(List<ITestInvocationListener> listeners) throws DeviceNotAvailableException {
        while (!mTestDefs.isEmpty()) {
            InstrumentationTestDef def = mTestDefs.remove(0);
            // only run continuous for now. Consider making this configurable
            if (def.isContinuous()) {
                Log.d(LOG_TAG, String.format("Running test def %s on %s", def.getName(),
                        getDevice().getSerialNumber()));
                InstrumentationTest test = createInstrumentationTest();
                mCurrentTest = test;
                test.setDevice(getDevice());
                test.setPackageName(def.getPackage());
                if (def.getRunner() != null) {
                    test.setRunnerName(def.getRunner());
                }
                if (def.getClassName() != null) {
                    test.setClassName(def.getClassName());
                }
                test.setRerunMode(isRerunMode());
                test.setTestSize(getTestSize());
                test.setTestTimeout(getTestTimeout());
                test.run(listeners);
                if (mSendCoverage && def.getCoverageTarget() != null) {
                    sendCoverage(def.getPackage(), def.getCoverageTarget(), listeners);
                }
                mCurrentTest = null;
            }
        }
    }

    /**
     * Forwards the tests coverage target info as a test metric.
     *
     * @param packageName
     * @param coverageTarget
     * @param listeners
     */
    private void sendCoverage(String packageName, String coverageTarget,
            List<ITestInvocationListener> listeners) {
        Map<String, String> coverageMetric = new HashMap<String, String>(1);
        coverageMetric.put(COVERAGE_TARGET_KEY, coverageTarget);
        for (ITestInvocationListener listener : listeners) {
            listener.testRunStarted(packageName, 0);
            listener.testRunEnded(0, coverageMetric);
        }
    }

    /**
     * Retrieves a set of files from device into temporary files on local filesystem.
     *
     * @param remoteFilePaths
     */
    private Collection<File> getRemoteFile(Collection<String> remoteFilePaths)
            throws DeviceNotAvailableException {
        Collection<File> files = new ArrayList<File>();
        for (String remoteFilePath : remoteFilePaths) {
            try {
                File tmpFile = FileUtil.createTempFile("test_defs_", ".xml");
                getDevice().pullFile(remoteFilePath, tmpFile);
                files.add(tmpFile);
            } catch (IOException e) {
                Log.e(LOG_TAG, "Failed to create temp file");
                Log.e(LOG_TAG, e);
            }
        }
        return files;
    }

    long getTestTimeout() {
        return mTestTimeout;
    }

    String getTestSize() {
        return mTestSize;
    }

    boolean isRerunMode() {
        return mIsRerunMode;
    }

    /**
     * Creates the {@link XmlDefsParser} to use. Exposed for unit testing.
     */
    XmlDefsParser createParser() {
        return new XmlDefsParser();
    }

    /**
     * Creates the {@link InstrumentationTest} to use. Exposed for unit testing.
     */
    InstrumentationTest createInstrumentationTest() {
        return new InstrumentationTest();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void resume(List<ITestInvocationListener> listeners) throws DeviceNotAvailableException {
        if (mCurrentTest != null) {
            mCurrentTest.resume(listeners);
        }
        doRun(listeners);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void resume(ITestInvocationListener listener) throws DeviceNotAvailableException {
        List<ITestInvocationListener> list = new ArrayList<ITestInvocationListener>(1);
        list.add(listener);
        resume(list);
    }
}
