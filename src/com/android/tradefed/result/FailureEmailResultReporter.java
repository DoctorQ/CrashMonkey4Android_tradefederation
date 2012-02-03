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
package com.android.tradefed.result;

import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.Email;
import com.android.tradefed.util.IEmail;

/**
 * An {@link EmailResultReporter} that can also restrict notifications
 * to just test or invocation failures
 */
@OptionClass(alias = "failure-email")
public class FailureEmailResultReporter extends EmailResultReporter {

    @Option(name = "send-only-on-failure",
            description = "Flag for sending email only on test failure.")
    private boolean mSendOnlyOnTestFailure = false;

    @Option(name = "send-only-on-inv-failure",
            description = "Flag for sending email only on invocation failure.")
    private boolean mSendOnlyOnInvFailure = false;

    /**
     * Default constructor
     */
    public FailureEmailResultReporter() {
        this(new Email());
    }

    /**
     * Create a {@link FailureEmailResultReporter} with a custom {@link IEmail} instance to use.
     * <p/>
     * Exposed for unit testing.
     *
     * @param mailer the {@link IEmail} instance to use.
     */
    public FailureEmailResultReporter(IEmail mailer) {
        super(mailer);
    }

    /**
     * Sets the send-only-on-inv-failure flag
     */
    void setSendOnlyOnInvocationFailure(boolean send) {
        mSendOnlyOnInvFailure = send;
    }

    @Override
    protected boolean shouldSendMessage() {
        if (mSendOnlyOnTestFailure) {
            if (!hasFailedTests()) {
                CLog.v("Not sending email because there are no failures to report.");
                return false;
            }
        } else if (mSendOnlyOnInvFailure && getInvocationStatus().equals(InvocationStatus.SUCCESS)) {
            CLog.v("Not sending email because invocation succeeded.");
            return false;
        }
        return true;
    }
}
