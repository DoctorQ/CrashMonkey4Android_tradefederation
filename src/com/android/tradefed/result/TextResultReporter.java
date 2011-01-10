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
package com.android.tradefed.result;

import com.android.ddmlib.testrunner.TestIdentifier;

import java.util.Map;

import junit.textui.ResultPrinter;

/**
 * A test result reporter that forwards results to the JUnit text result printer.
 */
public class TextResultReporter extends InvocationToJUnitResultForwarder
    implements ITestInvocationListener {

    /**
     * Creates a {@link TextResultReporter}.
     */
    public TextResultReporter() {
        super(new ResultPrinter(System.out));
    }

    /**
     * Overrides parent to explicitly print out failures. The ResultPrinter relies on the runner
     * calling "print" at end of test run to do this.
     * {@inheritDoc}
     */
    @Override
    public void testFailed(TestFailure status, TestIdentifier testId, String trace) {
        ResultPrinter printer = (ResultPrinter)getJUnitListener();
        printer.getWriter().format("\nTest %s: %s \n stack: %s ", status, testId, trace);
    }

    /**
     * Overrides parent to explicitly print out metrics.
     */
    @Override
    public void testRunEnded(long elapsedTime, Map<String, String> metrics) {
        super.testRunEnded(elapsedTime, metrics);
        ResultPrinter printer = (ResultPrinter)getJUnitListener();
        printer.getWriter().format("\nMetrics: %s\n", metrics);
    }
}
