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

package com.android.tradefed.util;

import com.android.ddmlib.Log;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

/**
 * A helper class to send an email.  Note that this class is NOT PLATFORM INDEPENDENT.  It will
 * likely fail on Windows, and possibly on Mac OS X as well.  It will fail on any machine where
 * The binary pointed at by the {@code mailer} constant doesn't exist.
 */
public class Email {
    private static final String LOG_TAG = "Email";
    private static final String mailer = "/usr/bin/mailx";

    /**
     * This is a sad excuse for an email message.
     */
    public static class Message {
        private Collection<String> mToAddrs = null;
        private Collection<String> mCcAddrs = null;
        private Collection<String> mBccAddrs = null;
        private String mSubject = null;
        private String mBody = null;

        public Message() {}

        /**
         * Convenience constructor: create a simple message
         *
         * @param to Single destination address
         * @param subject Subject
         * @param body Message body
         */
        public Message(String to, String subject, String body) {
            addTo(to);
            setSubject(subject);
            setBody(body);
        }

        public void addTo(String address) {
            if (mToAddrs == null) {
                mToAddrs = new ArrayList<String>();
            }
            mToAddrs.add(address);
        }
        public void addCc(String address) {
            if (mCcAddrs == null) {
                mCcAddrs = new ArrayList<String>();
            }
            mCcAddrs.add(address);
        }
        public void addBcc(String address) {
            if (mBccAddrs == null) {
                mBccAddrs = new ArrayList<String>();
            }
            mBccAddrs.add(address);
        }
        public void setSubject(String subject) {
            mSubject = subject;
        }
        public void setBody(String body) {
            mBody = body;
        }


        public Collection<String> getTo() {
            return mToAddrs;
        }
        public Collection<String> getCc() {
            return mCcAddrs;
        }
        public Collection<String> getBcc() {
            return mBccAddrs;
        }
        public String getSubject() {
            return mSubject;
        }
        public String getBody() {
            return mBody;
        }
    }

    private static String join(Collection<String> list, String sep) {
        StringBuilder builder = new StringBuilder();
        Iterator<String> iter = list.iterator();
        while (iter.hasNext()) {
            String element = iter.next();
            builder.append(element);
            if(iter.hasNext()) {
                builder.append(sep);
            }
        }
        return builder.toString();
    }

    /**
     * A helper method to use ProcessBuilder to create a new process.  This can't use
     * {@link com.android.tradefed.util.IRunUtil} because that class doesn't provide a way to pass
     * data to the stdin of the spawned process, which is the usage paradigm for most commandline
     * mailers such as mailx and sendmail.
     * <p/>
     * Exposed for mocking
     *
     * @param cmd The {@link String[]} to pass to the {@link ProcessBuilder} constructor
     * @return The {@link Process} returned from from {@link ProcessBuilder#start()}
     * @throws IOException if sending email failed in a synchronously-detectable way
     */
    Process run(String[] cmd) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        return pb.start();
    }

    /**
     * A method to send a {@link Message}.  Verifies that the to, subject, and body fields of the
     * {@link Message} are not null, but does no verification beyond the null checks.
     *
     * Note that any SMTP-level errors are undetectable at this stage.  Because of the asynchronous
     * nature of email, they will generally be reported to the envelope-sender of the message.  In
     * that case, the envelope-sender will typically receive an email from MAILER-DAEMON with the
     * details of the error.
     *
     * @param msg The {@link Message} to try to send
     * @throws IllegalArgumentException if any of the to, subject, or body fields of {@code msg} is
     *         null
     * @throws IOException if sending email failed in a synchronously-detectable way
     */
    public void send(Message msg) throws IllegalArgumentException, IOException {
        // Sanity checks
        if (msg.getTo() == null || msg.getSubject() == null || msg.getBody() == null) {
            throw new IllegalArgumentException("Message has no destination or no subject");
        }

        ArrayList<String> cmd = new ArrayList<String>();
        // mailx -b bcc -c cc -s subj to-addr to-addr
        cmd.add(mailer);
        if (msg.getBcc() != null) {
            cmd.add("-b");
            cmd.add(join(msg.getBcc(), ","));
        }
        if (msg.getCc() != null) {
            cmd.add("-c");
            cmd.add(join(msg.getCc(), ","));
        }
        cmd.add("-s");
        cmd.add(msg.getSubject());

        cmd.addAll(msg.getTo());

        Log.i(LOG_TAG, String.format("About to send email with command: %s", cmd));
        String[] strArray = new String[cmd.size()];
        Process mailerProc = run(cmd.toArray(strArray));
        BufferedOutputStream mailerStdin = new BufferedOutputStream(mailerProc.getOutputStream());
        /* There is no such thing as a "character" in the land of the shell; there are only bytes.
         * Here, we convert the body from a Java string (consisting of characters) to a byte array
         * encoding each character with UTF-8.  Each character will be represented as between one
         * and four bytes apiece.
         */
        mailerStdin.write(msg.getBody().getBytes("UTF-8"));
        mailerStdin.flush();
    }
}

