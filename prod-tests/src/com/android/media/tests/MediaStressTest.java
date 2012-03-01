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

package com.android.media.tests;

import com.android.ddmlib.IDevice;
import com.android.ddmlib.Log;
import com.android.ddmlib.testrunner.IRemoteAndroidTestRunner;
import com.android.ddmlib.testrunner.RemoteAndroidTestRunner;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.SnapshotInputStreamSource;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.util.StreamUtil;

import junit.framework.Assert;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Runs the Media stress testcases.
 * FIXME: more details
 * <p/>
 * Note that this test will not run properly unless /sdcard is mounted and writable.
 */
public class MediaStressTest implements IDeviceTest, IRemoteTest {
    private static final String LOG_TAG = "MediaStressTest";

    ITestDevice mTestDevice = null;
    private static final String METRICS_RUN_NAME = "VideoRecordingStress";

    //Max test timeout - 5 hrs
    private static final int MAX_TEST_TIMEOUT = 5 * 60 * 60 * 1000;

    // Constants for running the tests
    private static final String mTestClassName =
            "com.android.mediaframeworktest.stress.MediaRecorderStressTest";
    private static final String mTestPackageName = "com.android.mediaframeworktest";
    private static final String mTestRunnerName = ".MediaRecorderStressTestRunner";

    // Constants for parsing the output file
    private static final String PREVIEW_STANZA = "Camera start preview stress:";
    private static final String SWITCH_STANZA = "Camera and video recorder preview switching";
    private static final String PLAYBACK_STANZA = "Video record and play back stress test:";
    private static final String RECORDING_STANZA = "H263 video record";
    private static final String TIMELAPSE_STANZA = "Start camera time lapse stress:";
    private static final Pattern EXPECTED_LOOP_COUNT_PATTERN =
            Pattern.compile("Total number of loops:\\s*(\\d+)");
    private static final Pattern ACTUAL_LOOP_COUNT_PATTERN =
            Pattern.compile("No of loop:.*,\\s*(\\d+)\\s*");

    private final String mOutputPath = "mediaStressOutput.txt";

    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        Assert.assertNotNull(mTestDevice);

        IRemoteAndroidTestRunner runner = new RemoteAndroidTestRunner(mTestPackageName,
                mTestRunnerName, mTestDevice.getIDevice());
        runner.setClassName(mTestClassName);
        runner.setMaxtimeToOutputResponse(MAX_TEST_TIMEOUT);

        cleanTmpFiles();
        mTestDevice.runInstrumentationTests(runner, listener);
        logOutputFile(listener);
        cleanTmpFiles();
    }

    /**
     * Clean up temp files from test runs
     */
    private void cleanTmpFiles() throws DeviceNotAvailableException {
        String extStore = mTestDevice.getMountPoint(IDevice.MNT_EXTERNAL_STORAGE);
        mTestDevice.executeShellCommand(String.format("rm %s/temp*.3gp", extStore));
        mTestDevice.executeShellCommand(String.format("rm %s/%s", extStore, mOutputPath));
    }

    /**
     * Pull the output file from the device, add it to the logs, and also parse out the relevant
     * test metrics and report them.
     */
    private void logOutputFile(ITestInvocationListener listener)
            throws DeviceNotAvailableException {
        File outputFile = null;
        InputStreamSource outputSource = null;
        try {
            outputFile = mTestDevice.pullFileFromExternal(mOutputPath);

            if (outputFile == null) {
                return;
            }

            Log.d(LOG_TAG, String.format("Sending %d byte file %s into the logosphere!",
                    outputFile.length(), outputFile));
            outputSource = new SnapshotInputStreamSource(new FileInputStream(outputFile));
            listener.testLog(mOutputPath, LogDataType.TEXT, outputSource);
            parseOutputFile(outputFile, listener);
        } catch (IOException e) {
            Log.e(LOG_TAG, String.format("IOException while reading or parsing output file: %s", e));
        } finally {
            if (outputFile != null) {
                outputFile.delete();
            }
            if (outputSource != null) {
                outputSource.cancel();
            }
        }
    }

    /**
     * Parse the relevant metrics from the Instrumentation test output file
     */
    private void parseOutputFile(File outputFile, ITestInvocationListener listener) {
        Map<String, String> runMetrics = new HashMap<String, String>();

        // try to parse it
        String contents;
        try {
            InputStream dataStream = new FileInputStream(outputFile);
            contents = StreamUtil.getStringFromStream(dataStream);
        } catch (IOException e) {
            Log.e(LOG_TAG, String.format("Got IOException: %s", e));
            return;
        }

        List<String> lines = Arrays.asList(contents.split("\n"));
        ListIterator<String> lineIter = lines.listIterator();
        String line;
        while (lineIter.hasNext()) {
            line = lineIter.next();
            String key = null;
            if (PREVIEW_STANZA.equals(line)) {
                key = "StopPreviewAndRelease";
            } else if (SWITCH_STANZA.equals(line)) {
                key = "SwitchModeCameraVideo";
            } else if (PLAYBACK_STANZA.equals(line)) {
                key = "VideoRecordPlayback";
            } else if (line.startsWith(RECORDING_STANZA)) {
                key = "VideoRecording";
            } else if (TIMELAPSE_STANZA.equals(line)) {
                key = "TimeLapseRecord";
            } else if (line.isEmpty()) {
                // ignore
                continue;
            } else {
                Log.e(LOG_TAG, String.format("Got unexpected line: %s", line));
                continue;
            }

            Integer countExpected = getIntFromOutput(lineIter, EXPECTED_LOOP_COUNT_PATTERN);
            Integer countActual = getIntFromOutput(lineIter, ACTUAL_LOOP_COUNT_PATTERN);
            int value = coalesceLoopCounts(countActual, countExpected);
            runMetrics.put(key, Integer.toString(value));
        }

        reportMetrics(listener, runMetrics);
    }

    /**
     * Report run metrics by creating an empty test run to stick them in
     * <p />
     * Exposed for unit testing
     */
    void reportMetrics(ITestInvocationListener listener, Map<String, String> metrics) {
        // Create an empty testRun to report the parsed runMetrics
        Log.d(LOG_TAG, String.format("About to report metrics: %s", metrics));
        listener.testRunStarted(METRICS_RUN_NAME, 0);
        listener.testRunEnded(0, metrics);
    }

    /**
     * Use the provided {@link Pattern} to parse a number out of the output file
     */
    private Integer getIntFromOutput(ListIterator<String> lineIter, Pattern numPattern) {
        Integer retval = null;
        String line = null;
        if (lineIter.hasNext()) {
            line = lineIter.next();
            Matcher m = numPattern.matcher(line);
            if (m.matches()) {
                retval = Integer.parseInt(m.group(1));
            } else {
                Log.e(LOG_TAG, String.format("Couldn't match pattern %s against line '%s'",
                        numPattern, line));
            }
        } else {
            Log.e(LOG_TAG, String.format("Encounted EOF while trying to match pattern %s",
                    numPattern));
        }

        return retval;
    }

    /**
     * Given an actual and an expected iteration count, determine a single metric to report.
     */
    private int coalesceLoopCounts(Integer actual, Integer expected) {
        if (expected == null || expected <= 0) {
            return -1;
        } else if (actual == null) {
            return expected;
        } else {
            return actual;
        }
    }

    @Override
    public void setDevice(ITestDevice device) {
        mTestDevice = device;
    }

    @Override
    public ITestDevice getDevice() {
        return mTestDevice;
    }
}

