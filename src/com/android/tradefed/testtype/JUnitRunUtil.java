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
package com.android.tradefed.testtype;

import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.JUnitToInvocationResultForwarder;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import junit.framework.Test;
import junit.framework.TestResult;

/**
 * A helper class for directing a {@link IRemoteTest#run(java.util.List)} call to a
 * {@link Test#run(TestResult)} call.
 */
class JUnitRunUtil {

    public static void runTest(List<ITestInvocationListener> listeners, Test junitTest) {

        for (ITestInvocationListener listener : listeners) {
            listener.testRunStarted(junitTest.getClass().getName(), junitTest.countTestCases());
        }
        long startTime = System.currentTimeMillis();
        // forward the JUnit results to the invocation listener
        JUnitToInvocationResultForwarder resultForwarder =
            new JUnitToInvocationResultForwarder(listeners);
        TestResult result = new TestResult();
        result.addListener(resultForwarder);
        junitTest.run(result);
        Map<String, String> emptyMap = Collections.emptyMap();
        for (ITestInvocationListener listener : listeners) {
            listener.testRunEnded(System.currentTimeMillis() - startTime, emptyMap);
        }
    }
}
