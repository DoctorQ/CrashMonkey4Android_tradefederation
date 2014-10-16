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

import com.android.tradefed.config.Option;
import com.android.tradefed.config.Option.Importance;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.util.Email;
import com.android.tradefed.util.IEmail;
import com.android.tradefed.util.IEmail.Message;
import com.android.tradefed.util.StreamUtil;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 将测试结果写到email中发送出去 A simple result reporter base class that sends emails for
 * test results.<br>
 * Subclasses should determine whether an email needs to be sent, and can
 * override other behavior.
 */
@OptionClass(alias = "email")
public class EmailResultReporter extends CollectingTestListener implements
		ITestSummaryListener {
	private static final String DEFAULT_SUBJECT_TAG = "Tradefed";

	@Option(name = "sender", description = "The envelope-sender address to use for the messages.", importance = Importance.IF_UNSET)
	private String mSender = null;

	@Option(name = "destination", description = "One or more destination addresses.", importance = Importance.IF_UNSET)
	private Collection<String> mDestinations = new HashSet<String>();

	@Option(name = "cc", description = "One or more cc addresses.", importance = Importance.IF_UNSET)
	private Collection<String> mCCs = new HashSet<String>();

	@Option(name = "subject-tag", description = "The tag to be added to the beginning of the email subject.")
	private String mSubjectTag = DEFAULT_SUBJECT_TAG;
	
	@Option(name = "send-message", description = "Whether or not to send mail")
	private boolean isSendMessage = true;
	
	private List<File> mAttach = new ArrayList<File>();

	private List<File> mDisposition = new ArrayList<File>();

	private List<TestSummary> mSummaries = null;

	private Throwable mInvocationThrowable = null;

	protected IEmail mMailer;

	private boolean mHtml;

	private String mCharset = null;

	/**
	 * Create a {@link EmailResultReporter}
	 */
	public EmailResultReporter() {
		this(new Email());
	}

	/**
	 * Create a {@link EmailResultReporter} with a custom {@link IEmail}
	 * instance to use.
	 * <p/>
	 * Exposed for unit testing.
	 * 
	 * @param mailer
	 *            the {@link IEmail} instance to use.
	 */
	protected EmailResultReporter(IEmail mailer) {
		mMailer = mailer;
	}

	/**
	 * Adds an email destination address.
	 * 
	 * @param dest
	 */
	public void addDestination(String dest) {
		mDestinations.add(dest);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void putSummary(List<TestSummary> summaries) {
		mSummaries = summaries;
	}

	/**
	 * Allow subclasses to get at the summaries we've received
	 */
	protected List<TestSummary> fetchSummaries() {
		return mSummaries;
	}

	/**
	 * A method, meant to be overridden, which should do whatever filtering is
	 * decided and determine whether a notification email should be sent for the
	 * test results. Presumably, would consider how many (if any) tests failed,
	 * prior failures of the same tests, etc.
	 * 
	 * @return {@code true} if a notification email should be sent,
	 *         {@code false} if not
	 */
	protected boolean shouldSendMessage() {
		return isSendMessage;
	}

	/**
	 * A method to generate the subject for email reports. Will not be called if
	 * {@link #shouldSendMessage()} returns {@code false}.
	 * 
	 * @return A {@link String} containing the subject to use for an email
	 *         report
	 */
	protected String generateEmailSubject() {
		return String.format("%s result for %s on build %s: %s", mSubjectTag,
				getBuildInfo().getTestTag(), getBuildInfo().getBuildId(),
				getInvocationStatus());
	}

	/**
	 * Returns the {@link InvocationStatus}
	 */
	protected InvocationStatus getInvocationStatus() {
		if (mInvocationThrowable == null) {
			return InvocationStatus.SUCCESS;
		} else if (mInvocationThrowable instanceof BuildError) {
			return InvocationStatus.BUILD_ERROR;
		} else {
			return InvocationStatus.FAILED;
		}
	}

	/**
	 * Returns the {@link Throwable} passed via
	 * {@link #invocationFailed(Throwable)}.
	 */
	protected Throwable getInvocationException() {
		return mInvocationThrowable;
	}

	/**
	 * A method to generate the body for email reports. Will not be called if
	 * {@link #shouldSendMessage()} returns {@code false}.
	 * 
	 * @return A {@link String} containing the body to use for an email report
	 */
	protected String generateEmailBody() {
		StringBuilder bodyBuilder = new StringBuilder();

		for (Map.Entry<String, String> buildAttr : getBuildInfo()
				.getBuildAttributes().entrySet()) {
			bodyBuilder.append(buildAttr.getKey());
			bodyBuilder.append(": ");
			bodyBuilder.append(buildAttr.getValue());
			bodyBuilder.append("\n");
		}
		bodyBuilder.append("host: ");
		try {
			bodyBuilder.append(InetAddress.getLocalHost().getHostName());
		} catch (UnknownHostException e) {
			bodyBuilder.append("unknown");
			CLog.e(e);
		}
		bodyBuilder.append("\n\n");

		if (mInvocationThrowable != null) {
			bodyBuilder.append("Invocation failed: ");
			bodyBuilder.append(StreamUtil.getStackTrace(mInvocationThrowable));
			bodyBuilder.append("\n");
		}
		bodyBuilder.append(String.format(
				"Test results:  %d passed, %d failed, %d error\n\n",
				getNumPassedTests(), getNumFailedTests(), getNumErrorTests()));
		for (TestRunResult result : getRunResults()) {
			if (!result.getRunMetrics().isEmpty()) {
				bodyBuilder.append(String.format("'%s' test run metrics: %s\n",
						result.getName(), result.getRunMetrics()));
			}
		}
		bodyBuilder.append("\n");

		if (mSummaries != null) {
			for (TestSummary summary : mSummaries) {
				bodyBuilder.append("Invocation summary report: ");
				bodyBuilder.append(summary.getSummary().getString());
				if (!summary.getKvEntries().isEmpty()) {
					bodyBuilder.append("\".\nSummary key-value dump:\n");
					bodyBuilder.append(summary.getKvEntries().toString());
				}
			}
		}
		return bodyBuilder.toString();
	}

	/**
	 * A method to set a flag indicating that the email body is in HTML rather
	 * than plain text
	 * 
	 * This method must be called before the email body is generated
	 * 
	 * @param html
	 *            true if the body is html
	 */
	protected void setHtml(boolean html) {
		mHtml = html;
	}

	protected boolean isHtml() {
		return mHtml;
	}

	protected void addAttaches(List<File> attach) {
		mAttach.addAll(attach);
	}

	protected void addAttach(File attach) {
		mAttach.add(attach);
	}

	protected List<File> getAttach() {
		return mAttach;
	}

	protected void addDispositions(List<File> dispositions) {
		mDisposition.addAll(dispositions);
	}

	protected void addDisposition(File disposition) {
		mDisposition.add(disposition);
	}

	protected List<File> getDisposition() {
		return mDisposition;
	}

	protected String getCharset() {
		return mCharset;
	}

	protected void setCharset(String charset) {
		this.mCharset = charset;
	}

	@Override
	public void invocationFailed(Throwable t) {
		mInvocationThrowable = t;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void invocationEnded(long elapsedTime) {
		super.invocationEnded(elapsedTime);
		// 是否发送信息,一般都会发送
		if (!shouldSendMessage()) {
			return;
		}

		if (mDestinations.isEmpty()) {
			CLog.e("Failed to send email because no destination addresses were set.");
			return;
		}

		Message msg = new Message();
		msg.setSender(mSender);
		msg.setSubject(generateEmailSubject());
		msg.setBody(generateEmailBody());
		msg.setHtml(isHtml());
		msg.setCharset(getCharset());

		Iterator<String> toAddress = mDestinations.iterator();
		while (toAddress.hasNext()) {
			msg.addTo(toAddress.next());
		}

		Iterator<String> ccAddress = mCCs.iterator();
		while (ccAddress.hasNext()) {
			msg.addCc(ccAddress.next());
		}

		Iterator<File> attaches = mAttach.iterator();
		while (attaches.hasNext()) {
			msg.addAttach(attaches.next());
		}

		Iterator<File> dispositions = mDisposition.iterator();
		while (dispositions.hasNext()) {
			msg.addDisposition(dispositions.next());
		}

		try {
			//发送邮件
			mMailer.send(msg);
		} catch (IllegalArgumentException e) {
			CLog.e("Failed to send email");
			CLog.e(e);
		} catch (IOException e) {
			CLog.e("Failed to send email");
			CLog.e(e);
		}
	}
}
