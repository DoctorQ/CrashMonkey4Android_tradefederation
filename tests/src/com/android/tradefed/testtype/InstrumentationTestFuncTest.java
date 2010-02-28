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
import com.android.tradefed.result.ITestInvocationListener;

import org.easymock.EasyMock;

import java.io.IOException;

/**
 * Functional tests for {@link InstrumentationTest}.
 */
public class InstrumentationTestFuncTest extends DeviceTestCase {

    private static final String TEST_PACKAGE_VALUE = "com.android.tradefed.testapp";
    private static final String TEST_CLASS_VALUE = "com.android.tradefed.testapp.OnDeviceTest";
    private static final String PASSED_TEST_METHOD = "testPassed";
    private static final String FAILED_TEST_METHOD = "testFailed";
    private static final String CRASH_TEST_METHOD = "testCrash";
    private static final String TIMEOUT_TEST_METHOD = "testNeverEnding";
    private static final String LOG_TAG = "InstrumentationTestFuncTest";

    /** The {@link InstrumentationTest} under test */
    private InstrumentationTest mInstrumentationTest;

    private ITestInvocationListener mMockListener;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mInstrumentationTest = new InstrumentationTest();
        mInstrumentationTest.setPackageName(TEST_PACKAGE_VALUE);
        mInstrumentationTest.setDevice(getDevice());
        // use no timeout by default
        mInstrumentationTest.setRunTimeout(-1);
        mMockListener = EasyMock.createStrictMock(ITestInvocationListener.class);
    }

    /**
     * Test normal run scenario with a single passed test result.
     */
    public void testRun() {
        TestIdentifier expectedTest = new TestIdentifier(TEST_CLASS_VALUE, PASSED_TEST_METHOD);
        mInstrumentationTest.setClassName(TEST_CLASS_VALUE);
        mInstrumentationTest.setMethodName(PASSED_TEST_METHOD);
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
    public void testRun_testFailed() {
        TestIdentifier expectedTest = new TestIdentifier(TEST_CLASS_VALUE, FAILED_TEST_METHOD);
        mInstrumentationTest.setClassName(TEST_CLASS_VALUE);
        mInstrumentationTest.setMethodName(FAILED_TEST_METHOD);
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
    public void testRun_testCrash() {
        TestIdentifier expectedTest = new TestIdentifier(TEST_CLASS_VALUE, CRASH_TEST_METHOD);
        mInstrumentationTest.setClassName(TEST_CLASS_VALUE);
        mInstrumentationTest.setMethodName(CRASH_TEST_METHOD);
        mMockListener.testRunStarted(1);
        mMockListener.testStarted(EasyMock.eq(expectedTest));
        mMockListener.testRunFailed("java.lang.RuntimeException");
        mMockListener.testRunEnded(EasyMock.anyLong());
        EasyMock.replay(mMockListener);
        mInstrumentationTest.run(mMockListener);
    }

    /**
     * Test run scenario where test run hangs indefinitely, and times out.
     */
    public void testRun_testTimeout() {
        TestIdentifier expectedTest = new TestIdentifier(TEST_CLASS_VALUE, TIMEOUT_TEST_METHOD);
        mInstrumentationTest.setClassName(TEST_CLASS_VALUE);
        mInstrumentationTest.setMethodName(TIMEOUT_TEST_METHOD);
        mInstrumentationTest.setRunTimeout(1000);
        mMockListener.testRunStarted(1);
        mMockListener.testStarted(EasyMock.eq(expectedTest));
        mMockListener.testRunFailed("timeout: test did not complete in 1000 ms");
        mMockListener.testRunEnded(EasyMock.anyLong());
        EasyMock.replay(mMockListener);
        mInstrumentationTest.run(mMockListener);
    }

    /**
     * Test run scenario where device reboots during test run.
     */
    public void testRun_deviceReboot() throws InterruptedException, IOException {
        TestIdentifier expectedTest = new TestIdentifier(TEST_CLASS_VALUE,
                TIMEOUT_TEST_METHOD);
        mInstrumentationTest.setClassName(TEST_CLASS_VALUE);
        mInstrumentationTest.setMethodName(TIMEOUT_TEST_METHOD);
        mMockListener.testRunStarted(1);
        mMockListener.testStarted(EasyMock.eq(expectedTest));
        mMockListener.testRunFailed((String)EasyMock.anyObject());
        EasyMock.replay(mMockListener);
        // fork off a thread to do the reboot
        Thread rebootThread = new Thread() {
            @Override
            public void run() {
                // wait for test run to begin
                try {
                    Thread.sleep(500);
                    Runtime.getRuntime().exec("adb reboot");
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
}
