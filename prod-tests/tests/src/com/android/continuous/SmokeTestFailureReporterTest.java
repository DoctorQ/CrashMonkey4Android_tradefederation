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

package com.android.continuous;

import com.android.ddmlib.testrunner.ITestRunListener.TestFailure;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.build.BuildInfo;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.log.LogUtil.CLog;

import junit.framework.TestCase;

import java.util.Collections;
import java.util.Map;

public class SmokeTestFailureReporterTest extends TestCase {
    private SmokeTestFailureReporter mReporter = null;

    private static final String TAG = "DeviceSmokeTests";
    private static final String BID = "123456";
    private static final String TARGET = "target?";
    private static final String FLAVOR = "generic-userdebug";
    private static final String BRANCH = "git_master";

    @Override
    public void setUp() {
        mReporter = new SmokeTestFailureReporter();
    }

    public void testSingleFail() {
        final String expSubject = "git_master SmokeFAST failed on generic-userdebug @123456";
        final String expBodyStart = "FooTest#testFoo failed\n\n";

        final Map<String, String> emptyMap = Collections.emptyMap();
        final TestIdentifier testId = new TestIdentifier("FooTest", "testFoo");
        final String trace = "this is a trace";

        IBuildInfo build = new BuildInfo(BID, TAG, TARGET);
        build.setBuildFlavor(FLAVOR);
        build.setBuildBranch(BRANCH);

        mReporter.invocationStarted(build);
        mReporter.testRunStarted("testrun", 1);
        mReporter.testStarted(testId);
        mReporter.testFailed(TestFailure.FAILURE, testId, trace);
        mReporter.testEnded(testId, emptyMap);
        mReporter.testRunEnded(2, emptyMap);
        mReporter.invocationEnded(1);

        final String subj = mReporter.generateEmailSubject();
        final String body = mReporter.generateEmailBody();
        CLog.i("subject: %s", subj);
        CLog.i("body:\n%s", body);
        assertEquals(expSubject, subj);
        assertTrue(String.format(
                "Expected body to start with \"\"\"%s\"\"\".  Body was actually: %s\n",
                expBodyStart, body), body.startsWith(expBodyStart));
    }

    public void testTwoPassOneFail() {
        final String expSubject = "git_master SmokeFAST failed on generic-userdebug @123456";
        final String expBodyStart = "FooTest#testFail failed\n\n";

        final Map<String, String> emptyMap = Collections.emptyMap();
        final String trace = "this is a trace";
        final TestIdentifier testFail = new TestIdentifier("FooTest", "testFail");
        final TestIdentifier testPass1 = new TestIdentifier("FooTest", "testPass1");
        final TestIdentifier testPass2 = new TestIdentifier("FooTest", "testPass2");

        IBuildInfo build = new BuildInfo(BID, TAG, TARGET);
        build.setBuildFlavor(FLAVOR);
        build.setBuildBranch(BRANCH);

        mReporter.invocationStarted(build);
        mReporter.testRunStarted("testrun", 1);
        mReporter.testStarted(testPass1);
        mReporter.testEnded(testPass1, emptyMap);

        mReporter.testStarted(testFail);
        mReporter.testFailed(TestFailure.FAILURE, testFail, trace);
        mReporter.testEnded(testFail, emptyMap);

        mReporter.testStarted(testPass2);
        mReporter.testEnded(testPass2, emptyMap);
        mReporter.testRunEnded(2, emptyMap);
        mReporter.invocationEnded(1);

        final String subj = mReporter.generateEmailSubject();
        final String body = mReporter.generateEmailBody();
        CLog.i("subject: %s", subj);
        CLog.i("body:\n%s", body);
        assertEquals(expSubject, subj);
        assertTrue(String.format(
                "Expected body to start with \"\"\"%s\"\"\".  Body was actually: %s\n",
                expBodyStart, body), body.startsWith(expBodyStart));
    }
}
