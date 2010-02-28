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
import com.android.ddmlib.testrunner.ITestRunListener.TestFailure;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import junit.framework.AssertionFailedError;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestListener;

/**
 * A class that listens to {@link junit.framework.TestListener} events and forwards them to an
 * {@link ITestInvocationListener}.
 * <p/>
 */
 public class JUnitToInvocationResultForwarder implements TestListener {

    private static final String LOG_TAG = "JUnitToInvocationResultForwarder";
    private final ITestInvocationListener mInvocationListener;

    public JUnitToInvocationResultForwarder(ITestInvocationListener invocationListener) {
        mInvocationListener = invocationListener;
    }

    /**
     * {@inheritDoc}
     */
    public void addError(Test test, Throwable t) {
        mInvocationListener.testFailed(TestFailure.ERROR, getTestId(test), getStackTrace(t));
    }

    /**
     * {@inheritDoc}
     */
    public void addFailure(Test test, AssertionFailedError t) {
        mInvocationListener.testFailed(TestFailure.FAILURE, getTestId(test), getStackTrace(t));
    }

    /**
     * {@inheritDoc}
     */
    public void endTest(Test test) {
        mInvocationListener.testEnded(getTestId(test));
    }

    /**
     * {@inheritDoc}
     */
    public void startTest(Test test) {
        mInvocationListener.testStarted(getTestId(test));
    }

    /**
     * Return the {@link TestIdentifier} equivalent for the {@link Test}.
     *
     * @param test the {@link Test} to convert
     * @return the {@link TestIdentifier}
     */
    private TestIdentifier getTestId(Test test) {
        final String className = test.getClass().getName();
        String testName = "";
        if (test instanceof TestCase) {
            testName = ((TestCase)test).getName();
        }
        return new TestIdentifier(className, testName);
    }

    /**
     * Gets the stack trace in {@link String}.
     *
     * @param throwable the {@link Throwable} to convert.
     * @return a {@link String} stack trace
     */
    private String getStackTrace(Throwable throwable) {
        // dump the print stream results to the ByteArrayOutputStream, so contents can be evaluated
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintStream bytePrintStream = new PrintStream(outputStream);
        throwable.printStackTrace(bytePrintStream);
        return outputStream.toString();
    }

}
