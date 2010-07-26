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
import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.ddmlib.testrunner.ITestRunListener.TestFailure;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.LogDataType;

import org.easymock.EasyMock;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Map;


/**
 * Functional tests for {@link InstrumentationTest}.
 */
public class GTestFuncTest extends DeviceTestCase {

    private static final String LOG_TAG = "GTestFuncTest";
    private GTest mGTest = null;
    private ITestInvocationListener mMockListener = null;
    private static final Map<String, String> EMPTY_MAP = Collections.emptyMap();

    // Native test app constants
    public static final String NATIVE_TESTAPP_GTEST_CLASSNAME = "TradeFedNativeAppTest";
    public static final String NATIVE_TESTAPP_MODULE_NAME = "tfnativetests";
    public static final String NATIVE_TESTAPP_GTEST_CRASH_METHOD = "testNullPointerCrash";
    public static final String NATIVE_TESTAPP_GTEST_TIMEOUT_METHOD = "testInfiniteLoop";
    public static final int NATIVE_TESTAPP_TOTAL_TESTS = 2;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mGTest = new GTest();
        mGTest.setDevice(getDevice());
        mMockListener = EasyMock.createMock(ITestInvocationListener.class);
    }

    /**
     * Test normal run of the sample native test project (7 tests, one of which is a failure).
     */
    public void testRun() throws DeviceNotAvailableException {
        mGTest.setRunTestsInAllSubdirectories(false);
        Log.i(LOG_TAG, "testRun");
        mMockListener.testRunStarted(7);
        String[][] allTests = {
                {"FibonacciTest", "testRecursive_One"},
                {"FibonacciTest", "testRecursive_Ten"},
                {"FibonacciTest", "testIterative_Ten"},
                {"CelciusToFarenheitTest", "testNegative"},
                {"CelciusToFarenheitTest", "testPositive"},
                {"FarenheitToCelciusTest", "testExactFail"},
                {"FarenheitToCelciusTest", "testApproximatePass"},
        };
        for (String[] test : allTests) {
            String testClass = test[0];
            String testName = test[1];
            TestIdentifier id = new TestIdentifier(testClass, testName);
            mMockListener.testStarted(id);

            if (testName.endsWith("Fail")) {
              mMockListener.testFailed(EasyMock.eq(TestFailure.FAILURE),
                      EasyMock.eq(id),
                      EasyMock.isA(String.class));
            }
            else {
                mMockListener.testEnded(id);
            }
        }
        mMockListener.testRunEnded(EasyMock.anyLong(), EMPTY_MAP);
        mMockListener.testLog((String)EasyMock.anyObject(), (LogDataType)EasyMock.anyObject(),
                (InputStream)EasyMock.anyObject());
        EasyMock.replay(mMockListener);
        mGTest.run(mMockListener);
        mGTest.setRunTestsInAllSubdirectories(false);
    }

    /**
     * Helper to run tests in the Native Test App.
     *
     * @param testId the {%link TestIdentifier} of the test to run
     */
    private void doNativeTestAppRunSingleTestFailure(TestIdentifier testId) {
        mGTest.setModuleName(NATIVE_TESTAPP_MODULE_NAME);
        mMockListener.testRunStarted(1);
        mMockListener.testStarted(EasyMock.eq(testId));
        mMockListener.testFailed(EasyMock.eq(TestFailure.ERROR), EasyMock.eq(testId),
                EasyMock.isA(String.class));
        mMockListener.testEnded(EasyMock.eq(testId));
        mMockListener.testRunFailed((String)EasyMock.anyObject());
        mMockListener.testRunEnded(EasyMock.anyLong(), EMPTY_MAP);
        EasyMock.replay(mMockListener);
    }

    /**
     * Test run scenario where test process crashes while trying to access NULL ptr.
     */
    public void testRun_testCrash() throws DeviceNotAvailableException {
        Log.i(LOG_TAG, "testRun_testCrash");
        TestIdentifier testId = new TestIdentifier(NATIVE_TESTAPP_GTEST_CLASSNAME,
                NATIVE_TESTAPP_GTEST_CRASH_METHOD);
        doNativeTestAppRunSingleTestFailure(testId);
        // Set GTest to only run the crash test
        mGTest.setTestNamePositiveFilter(NATIVE_TESTAPP_GTEST_CRASH_METHOD);

        mGTest.run(mMockListener);
        EasyMock.verify(mMockListener);
    }

    /**
     * Test run scenario where device reboots during test run.
     */
    public void testRun_deviceReboot() throws Exception {
        Log.i(LOG_TAG, "testRun_deviceReboot");

        TestIdentifier testId = new TestIdentifier(NATIVE_TESTAPP_GTEST_CLASSNAME,
                NATIVE_TESTAPP_GTEST_TIMEOUT_METHOD);

        doNativeTestAppRunSingleTestFailure(testId);

        // Set GTest to only run the crash test
        mGTest.setTestNamePositiveFilter(NATIVE_TESTAPP_GTEST_TIMEOUT_METHOD);

        // fork off a thread to do the reboot
        Thread rebootThread = new Thread() {
            @Override
            public void run() {
                // wait for test run to begin
                try {
                    Thread.sleep(500);
                    Runtime.getRuntime().exec(
                            String.format("adb -s %s reboot", getDevice().getIDevice()
                                    .getSerialNumber()));
                } catch (InterruptedException e) {
                    Log.w(LOG_TAG, "interrupted");
                } catch (IOException e) {
                    Log.w(LOG_TAG, "IOException when rebooting");
                }
            }
        };
        rebootThread.start();
        mGTest.run(mMockListener);
        getDevice().waitForDeviceAvailable();
    }
}
