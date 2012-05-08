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
package com.android.tradefed.util.brillopad;

import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.ArrayUtil;
import com.android.tradefed.util.brillopad.item.GenericLogcatItem;
import com.android.tradefed.util.brillopad.item.LogcatItem;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An {@link IParser} to handle logcat.  The parser can handle the time and threadtime logcat
 * formats.
 * <p>
 * Since the timestamps in the logcat do not have a year, the year can be set manually when the
 * parser is created or through {@link #setYear(String)}.  If a year is not set, the current year
 * will be used.
 * </p>
 */
public class LogcatParser implements IParser {

    /**
     * Match a single line of `logcat -v threadtime`, such as:
     * 05-26 11:02:36.886  5689  5689 D AndroidRuntime: CheckJNI is OFF
     */
    private static final Pattern THREADTIME_LINE = Pattern.compile(
            "^(\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}.\\d{3})\\s+" +  /* timestamp [1] */
                "(\\d+)\\s+(\\d+)\\s+([A-Z])\\s+" +  /* pid/tid and log level [2-4] */
                "(.+?)\\s*: (.*)$" /* tag and message [5-6]*/);

    /**
     * Match a single line of `logcat -v time`, such as:
     * 06-04 02:32:14.002 D/dalvikvm(  236): GC_CONCURRENT freed 580K, 51% free [...]
     */
    private static final Pattern TIME_LINE = Pattern.compile(
            "^(\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}.\\d{3})\\s+" +  /* timestamp [1] */
                "(\\w)/(.+?)\\(\\s*(\\d+)\\): (.*)$");  /* level, tag, pid, msg [2-5] */

    /**
     * Class for storing logcat meta data for a particular grouped list of lines.
     */
    private class LogcatData {
        public Integer mPid = null;
        public Integer mTid = null;
        public Date mTime = null;
        public String mLevel = null;
        public String mTag = null;
        public String mLastPreamble = null;
        public String mProcPreamble = null;
        public List<String> mLines = new LinkedList<String>();

        public LogcatData(Integer pid, Integer tid, Date time, String level, String tag,
                String lastPreamble, String procPreamble) {
            mPid = pid;
            mTid = tid;
            mTime = time;
            mLevel = level;
            mTag = tag;
            mLastPreamble = lastPreamble;
            mProcPreamble = procPreamble;
        }
    }

    private static final int MAX_BUFF_SIZE = 500;
    private static final int MAX_LAST_PREAMBLE_SIZE = 15;
    private static final int MAX_PROC_PREAMBLE_SIZE = 15;

    private LinkedList<String> mRingBuffer = new LinkedList<String>();
    private String mYear = null;

    /**
     * Constructor for {@link LogcatParser}.
     */
    public LogcatParser() {
    }

    /**
     * Constructor for {@link LogcatParser}.
     *
     * @param year The year as a string.
     */
    public LogcatParser(String year) {
        setYear(year);
    }

    /**
     * Sets the year for {@link LogcatParser}.
     *
     * @param year The year as a string.
     */
    public void setYear(String year) {
        mYear = year;
    }

    /**
     * {@inheritDoc}
     *
     * @return The {@link LogcatItem}.
     */
    public LogcatItem parse(List<String> lines) {
        LogcatItem logcat = new LogcatItem();

        Map<String, LogcatData> dataMap = new HashMap<String, LogcatData>();
        List<LogcatData> dataList = new LinkedList<LogcatData>();

        Date startTime = null;
        Date stopTime = null;

        for (String line : lines) {
            Integer pid = null;
            Integer tid = null;
            Date time = null;
            String level = null;
            String tag = null;
            String msg = null;

            Matcher m = THREADTIME_LINE.matcher(line);
            Matcher tm = TIME_LINE.matcher(line);
            if (m.matches()) {
                time = parseTime(m.group(1));
                pid = Integer.parseInt(m.group(2));
                tid = Integer.parseInt(m.group(3));
                level = m.group(4);
                tag = m.group(5);
                msg = m.group(6);
            } else if (tm.matches()) {
                time = parseTime(tm.group(1));
                level = tm.group(2);
                tag = tm.group(3);
                pid = Integer.parseInt(tm.group(4));
                msg = tm.group(5);
            } else {
                CLog.w("Failed to parse line '%s'", line);
                continue;
            }

            if (startTime == null) {
                startTime = time;
            }
            stopTime = time;

            // ANRs are split when START matches a line.  The newest entry is kept in the dataMap
            // for quick lookup while all entries are added to the list.
            if ("E".equals(level) && "ActivityManager".equals(tag)) {
                String key = encodeLine(pid, tid, level, tag);
                LogcatData data;
                if (!dataMap.containsKey(key) || AnrParser.START.matcher(msg).matches()) {
                    data = new LogcatData(pid, tid, time, level, tag, getLastPreamble(),
                            getProcPreamble(pid));
                    dataMap.put(key, data);
                    dataList.add(data);
                } else {
                    data = dataMap.get(key);
                }
                data.mLines.add(msg);
            }

            // PID and TID are enough to separate Java and native crashes.
            if (("E".equals(level) && "AndroidRuntime".equals(tag)) ||
                    ("I".equals(level) && "DEBUG".equals(tag))) {
                String key = encodeLine(pid, tid, level, tag);
                LogcatData data;
                if (!dataMap.containsKey(key)) {
                    data = new LogcatData(pid, tid, time, level, tag, getLastPreamble(),
                            getProcPreamble(pid));
                    dataMap.put(key, data);
                    dataList.add(data);
                } else {
                    data = dataMap.get(key);
                }
                data.mLines.add(msg);
            }

            // After parsing the line, add it the the buffer for the preambles.
            mRingBuffer.add(line);
            if (mRingBuffer.size() > MAX_BUFF_SIZE) {
                mRingBuffer.removeFirst();
            }
        }

        for (LogcatData data : dataList) {
            GenericLogcatItem item = null;
            if ("E".equals(data.mLevel) && "ActivityManager".equals(data.mTag)) {
                CLog.v("Parsing ANR: %s", data.mLines);
                item = new AnrParser().parse(data.mLines);
            } else if ("E".equals(data.mLevel) && "AndroidRuntime".equals(data.mTag)) {
                CLog.v("Parsing Java crash: %s", data.mLines);
                item = new JavaCrashParser().parse(data.mLines);
            } else if ("I".equals(data.mLevel) && "DEBUG".equals(data.mTag)) {
                CLog.v("Parsing native crash: %s", data.mLines);
                item = new NativeCrashParser().parse(data.mLines);
            }
            if (item != null) {
                item.setEventTime(data.mTime);
                item.setPid(data.mPid);
                item.setTid(data.mTid);
                item.setLastPreamble(data.mLastPreamble);
                item.setProcessPreamble(data.mProcPreamble);
                logcat.addEvent(item);
            }
        }

        logcat.setStartTime(startTime);
        logcat.setStopTime(stopTime);
        return logcat;
    }

    /**
     * Create an identifier that "should" be unique for a given logcat. In practice, we do use it as
     * a unique identifier.
     */
    private static String encodeLine(Integer pid, Integer tid, String level, String tag) {
        if (tid == null) {
            return String.format("%d|%s|%s", pid, level, tag);
        }
        return String.format("%d|%d|%s|%s", pid, tid, level, tag);
    }

    /**
     * Parse the timestamp and return a {@link Date}.  If year is not set, the current year will be
     * used.
     *
     * @param timeStr The timestamp in the format {@code MM-dd HH:mm:ss.SSS}.
     * @return The {@link Date}.
     */
    private Date parseTime(String timeStr) {
        // If year is null, just use the current year.
        if (mYear == null) {
            DateFormat yearFormatter = new SimpleDateFormat("yyyy");
            mYear = yearFormatter.format(new Date());
        }

        DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        try {
            return formatter.parse(String.format("%s-%s", mYear, timeStr));
        } catch (ParseException e) {
            CLog.e("Could not parse time string %s", timeStr);
            return null;
        }
    }

    /**
     * Get the last {@value #MAX_LAST_PREAMBLE_SIZE} lines of logcat.
     */
    private String getLastPreamble() {
        final int size = mRingBuffer.size();
        List<String> preamble;
        if (size > getLastPreambleSize()) {
            preamble = mRingBuffer.subList(size - getLastPreambleSize(), size);
        } else {
            preamble = mRingBuffer;
        }
        return ArrayUtil.join("\n", preamble).trim();
    }

    /**
     * Get the last {@value #MAX_PROC_PREAMBLE_SIZE} lines of logcat which match the given pid.
     */
    private String getProcPreamble(int pid) {
        LinkedList<String> preamble = new LinkedList<String>();

        ListIterator<String> li = mRingBuffer.listIterator(mRingBuffer.size());
        while (li.hasPrevious()) {
            String line = li.previous();

            Matcher m = THREADTIME_LINE.matcher(line);
            Matcher tm = TIME_LINE.matcher(line);
            if ((m.matches() && pid == Integer.parseInt(m.group(2))) ||
                    (tm.matches() && pid == Integer.parseInt(tm.group(4)))) {
                preamble.addFirst(line);
            }

            if (preamble.size() == getProcPreambleSize()) {
                return ArrayUtil.join("\n", preamble).trim();
            }
        }
        return ArrayUtil.join("\n", preamble).trim();
    }

    /**
     * Get the number of lines in the last preamble. Exposed for unit testing.
     */
    int getLastPreambleSize() {
        return MAX_LAST_PREAMBLE_SIZE;
    }

    /**
     * Get the number of lines in the process preamble. Exposed for unit testing.
     */
    int getProcPreambleSize() {
        return MAX_PROC_PREAMBLE_SIZE;
    }
}
