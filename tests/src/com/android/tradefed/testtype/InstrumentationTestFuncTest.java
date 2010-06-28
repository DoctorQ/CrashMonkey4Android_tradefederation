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
import com.android.tradefed.TestAppConstants;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.result.CollectingTestListener;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.LogDataType;

import org.easymock.EasyMock;

import java.io.IOException;
import java.io.InputStream;

/**
 * Functional tests for {@link InstrumentationTest}.
 */
public class InstrumentationTestFuncTest extends DeviceTestCase {

    private static final String LOG_TAG = "InstrumentationTestFuncTest";

    /** The {@link InstrumentationTest} under test */
    private InstrumentationTest mInstrumentationTest;

    private ITestInvocationListener mMockListener;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mInstrumentationTest = new InstrumentationTest();
        mInstrumentationTest.setPackageName(TestAppConstants.TESTAPP_PACKAGE);
        mInstrumentationTest.setDevice(getDevice());
        // use no timeout by default
        mInstrumentationTest.setTestTimeout(-1);
        // set to no rerun by default
        mInstrumentationTest.setRerunMode(false);
        mMockListener = EasyMock.createStrictMock(ITestInvocationListener.class);
    }

    /**
     * Test normal run scenario with a single passed test result.
     */
    public void testRun() throws DeviceNotAvailableException {
        Log.i(LOG_TAG, "testRun");
        TestIdentifier expectedTest = new TestIdentifier(TestAppConstants.TESTAPP_CLASS,
                TestAppConstants.PASSED_TEST_METHOD);
        mInstrumentationTest.setClassName(TestAppConstants.TESTAPP_CLASS);
        mInstrumentationTest.setMethodName(TestAppConstants.PASSED_TEST_METHOD);
        mMockListener.testRunStarted(1);
        mMockListener.testStarted(EasyMock.eq(expectedTest));
        mMockListener.testEnded(EasyMock.eq(expectedTest));
        mMockListener.testRunEnded(EasyMock.anyLong());
        EasyMock.replay(mMockListener);
        mInstrumentationTest.run(mMockListener);
    }

    /**
     * Test normal run scenario with a single failed test result.
     */
    public void testRun_testFailed() throws DeviceNotAvailableException {
        Log.i(LOG_TAG, "testRun_testFailed");

        TestIdentifier expectedTest = new TestIdentifier(TestAppConstants.TESTAPP_CLASS,
                TestAppConstants.FAILED_TEST_METHOD);
        mInstrumentationTest.setClassName(TestAppConstants.TESTAPP_CLASS);
        mInstrumentationTest.setMethodName(TestAppConstants.FAILED_TEST_METHOD);
        mMockListener.testRunStarted(1);
        mMockListener.testStarted(EasyMock.eq(expectedTest));
        // TODO: add stricter checking on stackTrace
        mMockListener.testFailed(EasyMock.eq(TestFailure.FAILURE), EasyMock.eq(expectedTest),
                (String)EasyMock.anyObject());
        mMockListener.testEnded(EasyMock.eq(expectedTest));
        mMockListener.testRunEnded(EasyMock.anyLong());
        EasyMock.replay(mMockListener);
        mInstrumentationTest.run(mMockListener);
    }

    /**
     * Test run scenario where test process crashes.
     */
    public void testRun_testCrash() throws DeviceNotAvailableException {
        Log.i(LOG_TAG, "testRun_testCrash");

        TestIdentifier expectedTest = new TestIdentifier(TestAppConstants.TESTAPP_CLASS,
                TestAppConstants.CRASH_TEST_METHOD);
        mInstrumentationTest.setClassName(TestAppConstants.TESTAPP_CLASS);
        mInstrumentationTest.setMethodName(TestAppConstants.CRASH_TEST_METHOD);
        mMockListener.testRunStarted(1);
        mMockListener.testStarted(EasyMock.eq(expectedTest));
        mMockListener.testFailed(EasyMock.eq(TestFailure.ERROR), EasyMock.eq(expectedTest),
                (String)EasyMock.anyObject());
        mMockListener.testEnded(EasyMock.eq(expectedTest));
        mMockListener.testRunFailed((String)EasyMock.anyObject());
        mMockListener.testRunEnded(EasyMock.anyLong());
        EasyMock.replay(mMockListener);
        mInstrumentationTest.run(mMockListener);
    }

    /**
     * Test run scenario where test run hangs indefinitely, and times out.
     */
    public void testRun_testTimeout() throws DeviceNotAvailableException {
        Log.i(LOG_TAG, "testRun_testTimeout");

        final long timeout = 1000;
        TestIdentifier expectedTest = new TestIdentifier(TestAppConstants.TESTAPP_CLASS,
                TestAppConstants.TIMEOUT_TEST_METHOD);
        mInstrumentationTest.setClassName(TestAppConstants.TESTAPP_CLASS);
        mInstrumentationTest.setMethodName(TestAppConstants.TIMEOUT_TEST_METHOD);
        mInstrumentationTest.setTestTimeout(timeout);
        mMockListener.testRunStarted(1);
        mMockListener.testStarted(EasyMock.eq(expectedTest));
        mMockListener.testFailed(EasyMock.eq(TestFailure.ERROR), EasyMock.eq(expectedTest),
                (String)EasyMock.anyObject());
        mMockListener.testRunFailed(String.format(InstrumentationTest.TIMED_OUT_MSG, timeout));
        mMockListener.testLog((String)EasyMock.anyObject(), (LogDataType)EasyMock.anyObject(),
                (InputStream)EasyMock.anyObject());
        mMockListener.testRunEnded(EasyMock.anyLong());
        EasyMock.replay(mMockListener);
        mInstrumentationTest.run(mMockListener);
    }

    /**
     * Test run scenario where device reboots during test run.
     */
    public void testRun_deviceReboot() throws Exception {
        Log.i(LOG_TAG, "testRun_deviceReboot");

        TestIdentifier expectedTest = new TestIdentifier(TestAppConstants.TESTAPP_CLASS,
                TestAppConstants.TIMEOUT_TEST_METHOD);
        mInstrumentationTest.setClassName(TestAppConstants.TESTAPP_CLASS);
        mInstrumentationTest.setMethodName(TestAppConstants.TIMEOUT_TEST_METHOD);
        mMockListener.testRunStarted(1);
        mMockListener.testStarted(EasyMock.eq(expectedTest));
        mMockListener.testFailed(EasyMock.eq(TestFailure.ERROR), EasyMock.eq(expectedTest),
                (String)EasyMock.anyObject());
        mMockListener.testEnded(EasyMock.eq(expectedTest));
        mMockListener.testRunFailed((String)EasyMock.anyObject());
        EasyMock.replay(mMockListener);
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
        mInstrumentationTest.run(mMockListener);
    }

    /**
     * Test running all the tests with rerun on. At least one method will cause run to stop
     * (currently TIMEOUT_TEST_METHOD and CRASH_TEST_METHOD). Verify that results are recorded for
     * all tests in the suite.
     */
    public void testRun_rerun() throws Exception {
        Log.i(LOG_TAG, "testRun_rerun");

        // run all tests in class
        mInstrumentationTest.setClassName(TestAppConstants.TESTAPP_CLASS);
        mInstrumentationTest.setRerunMode(true);
        mInstrumentationTest.setTestTimeout(1000);
        CollectingTestListener listener = new CollectingTestListener();
        mInstrumentationTest.run(listener);
        assertEquals(TestAppConstants.TOTAL_TEST_CLASS_TESTS, listener.getTestResults().size());
        assertEquals(TestAppConstants.TOTAL_TEST_CLASS_PASSED_TESTS, listener.getNumPassedTests());
    }
}
