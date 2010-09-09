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

import com.android.ddmlib.Log;
import com.android.ddmlib.testrunner.ITestRunListener;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.config.Option;
import com.android.tradefed.result.CollectingTestListener;
import com.android.tradefed.result.ITestSummaryListener;
import com.android.tradefed.result.TestSummary;
import com.android.tradefed.util.Email;
import com.android.tradefed.util.Email.Message;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Iterator;

/**
 * A simple result reporter that sends emails for test results.
 */
public class EmailResultReporter extends CollectingTestListener implements ITestSummaryListener {
    private static final String LOG_TAG = "EmailResultReporter";

    @Option(name="sender", description="The envelope-sender address to use for the messages")
    private String mSender = null;

    @Option(name="destination", description="One or more destination addresses")
    private Collection<String> mDestinations = new HashSet<String>();

    private List<TestSummary> mSummaries = null;
    private long mElapsedTime = -1;

    /**
     * {@inheritDoc}
     */
    public void putSummary(List<TestSummary> summaries) {
        mSummaries = summaries;
    }

    /**
     * A method, meant to be overridden, which should do whatever filtering is decided and determine
     * whether a notification email should be sent for the test results.  Presumably, would consider
     * how many (if any) tests failed, prior failures of the same tests, etc.
     *
     * @return {@code true} if a notification email should be sent, {@code false} if not
     */
    protected boolean shouldSendMessage() {
        return true;
    }

    /**
     * A method to generate the subject for email reports.  Will not be called if
     * {@link shouldSendMessage()} returns {@code false}.
     *
     * @return A {@link String} containing the subject to use for an email report
     */
    protected String generateEmailSubject() {
        return String.format("Tradefed: %d passed, %d failed, %d error", getNumPassedTests(),
                getNumFailedTests(), getNumErrorTests());
    }

    /**
     * A method to generate the body for email reports.  Will not be called if
     * {@link shouldSendMessage()} returns {@code false}.
     *
     * @return A {@link String} containing the body to use for an email report
     */
    protected String generateEmailBody() {
        StringBuilder bodyBuilder = new StringBuilder();
        ListIterator<TestSummary> iter = mSummaries.listIterator();
        while (iter.hasNext()) {
            // FIXME: make this actually useful
            TestSummary summary = iter.next();
            bodyBuilder.append("Source ");
            bodyBuilder.append(summary.getSource());
            bodyBuilder.append(" provided summary \"");
            bodyBuilder.append(summary.getSummary().getString());
            bodyBuilder.append("\".\nIts key-value dump was:\n");
            bodyBuilder.append(summary.getKvEntries().toString());
            bodyBuilder.append("\n\n");
        }
        return bodyBuilder.toString();
    }

    /**
     * {@inheritDoc}
     */
    public void invocationEnded(long elapsedTime) {
        mElapsedTime = elapsedTime;

        if (mDestinations.isEmpty()) {
            Log.e(LOG_TAG, "Failed to send email because no destination addresses were set.");
            return;
        }

        Email mailer = new Email();
        mailer.setSender(mSender);

        Message msg = new Message();
        msg.setSubject(generateEmailSubject());
        msg.setBody(generateEmailBody());
        Iterator<String> toAddress = mDestinations.iterator();
        while (toAddress.hasNext()) {
            msg.addTo(toAddress.next());
        }

        try {
            mailer.send(msg);
        } catch (Throwable e) {
            System.err.println("Caught a throwable: " + e.toString());
        }
    }
}
