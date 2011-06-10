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
package com.android.tradefed.util.brillopad.section;

import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.brillopad.ItemList;
import com.android.tradefed.util.brillopad.IBlockParser;
import com.android.tradefed.util.brillopad.section.syslog.AnrParser;
import com.android.tradefed.util.brillopad.section.syslog.ISyslogParser;
import com.android.tradefed.util.brillopad.section.syslog.JavaCrashParser;
import com.android.tradefed.util.brillopad.section.syslog.NativeCrashParser;

import java.util.List;
import java.util.ListIterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An {@link IBlockParser} to handle the System Log section of the bugreport
 */
public class SystemLogParser implements IBlockParser {
    public static final String SECTION_NAME = "SYSTEM LOG";
    public static final String SECTION_REGEX = "------ SYSTEM LOG .*";

    private ISyslogParser mJava = new JavaCrashParser();
    private ISyslogParser mNative = new NativeCrashParser();
    private ISyslogParser mAnr = new AnrParser();

    /**
     * Match a single line of `logcat -v threadtime`, such as:
     * 05-26 11:02:36.886  5689  5689 D AndroidRuntime: CheckJNI is OFF
     */
    private static final Pattern THREADTIME_LINE = Pattern.compile(
            "^(\\d{2})-(\\d{2}) (\\d{2}:\\d{2}:\\d{2}.\\d{3})\\s+" +  /* timestamp [1-3] */
                "(\\d+)\\s+(\\d+)\\s+([A-Z])\\s+" +  /* pid/tid and log level [4-6] */
                "(.+?)\\s*: (.*)$" /* tag and message [7-8]*/);

    /**
     * Match a single line of `logcat -v time`, such as:
     * 06-04 02:32:14.002 D/dalvikvm(  236): GC_CONCURRENT freed 580K, 51% free [...]
     */
    private static final Pattern TIME_LINE = Pattern.compile(
            "^(\\d{2})-(\\d{2}) (\\d{2}:\\d{2}:\\d{2}.\\d{3})\\s+" +  /* timestamp [1-3] */
                "(\\w)/(.+?)\\(\\s*(\\d+)\\): (.*)$");  /* level, tag, pid, msg [4-7] */

    /**
     * {@inheritDoc}
     */
    @Override
    public void parseBlock(List<String> block, ItemList itemlist) {
        ListIterator<String> iter = block.listIterator();

        while (iter.hasNext()) {
            String line = iter.next();
            int pid = 0;
            int tid = 0;
            String level = null;
            String tag = null;
            String msg = null;

            // FIXME: do something with timestamps
            Matcher m = THREADTIME_LINE.matcher(line);
            Matcher tm = TIME_LINE.matcher(line);
            if (m.matches()) {
                pid = Integer.parseInt(m.group(4));
                tid = Integer.parseInt(m.group(5));
                level = m.group(6);
                tag = m.group(7);
                msg = m.group(8);
            } else if (tm.matches()) {
                level = tm.group(4);
                tag = tm.group(5);
                pid = Integer.parseInt(tm.group(6));
                msg = tm.group(7);
            } else {
                CLog.w("Failed to parse line '%s'", line);
                continue;
            }

            if ("I".equals(level) && "DEBUG".equals(tag)) {
                // Native crash
                mNative.parseLine(tid, pid, msg, itemlist);
            } else if ("E".equals(level) && "AndroidRuntime".equals(tag)) {
                // Java crash
                mJava.parseLine(tid, pid, msg, itemlist);
            } else if ("ActivityManager".equals(tag)) {
                // Message from ActivityManager; potentially an ANR
                mAnr.parseLine(tid, pid, msg, itemlist);
            }
        }

        mJava.commit(itemlist);
        mNative.commit(itemlist);
        mAnr.commit(itemlist);
    }
}
