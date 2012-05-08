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
package com.android.tradefed.util.brillopad;

import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.util.brillopad.item.GenericLogcatItem;
import com.android.tradefed.util.brillopad.item.MonkeyLogItem;
import com.android.tradefed.util.brillopad.item.MonkeyLogItem.DroppedCategory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A {@link IParser} to parse monkey logs.
 */
public class MonkeyLogParser implements IParser {
    private final static Pattern THROTTLE = Pattern.compile(
            "adb shell monkey.* --throttle (\\d+).*");
    private final static Pattern SEED_AND_TARGET_COUNT = Pattern.compile(
            ":Monkey: seed=(\\d+) count=(\\d+)");
    private final static Pattern SECURITY_EXCEPTIONS = Pattern.compile(
            "adb shell monkey.* --ignore-security-exceptions.*");

    private final static Pattern PACKAGES = Pattern.compile(":AllowPackage: (\\S+)");
    private final static Pattern CATEGORIES = Pattern.compile(":IncludeCategory: (\\S+)");

    private static final Pattern START_UPTIME = Pattern.compile(
            "# (.*) - device uptime = (\\d+\\.\\d+): Monkey command used for this test:");
    private static final Pattern STOP_UPTIME = Pattern.compile(
            "# (.*) - device uptime = (\\d+\\.\\d+): Monkey command ran for: " +
            "(\\d+):(\\d+) \\(mm:ss\\)");

    private final static Pattern INTERMEDIATE_COUNT = Pattern.compile(
            "\\s+// Sending event #(\\d+)");
    private final static Pattern FINISHED = Pattern.compile("// Monkey finished");
    private final static Pattern FINAL_COUNT = Pattern.compile("Events injected: (\\d+)");

    private final static Pattern DROPPED_KEYS = Pattern.compile(":Dropped: .*keys=(\\d+).*");
    private final static Pattern DROPPED_POINTERS = Pattern.compile(
            ":Dropped: .*pointers=(\\d+).*");
    private final static Pattern DROPPED_TRACKBALLS = Pattern.compile(
            ":Dropped: .*trackballs=(\\d+).*");
    private final static Pattern DROPPED_FLIPS = Pattern.compile(":Dropped: .*flips=(\\d+).*");
    private final static Pattern DROPPED_ROTATIONS = Pattern.compile(
            ":Dropped: .*rotations=(\\d+).*");

    private final static Pattern ANR = Pattern.compile(
            "// NOT RESPONDING: (\\S+) \\(pid (\\d+)\\)");
    private final static Pattern JAVA_CRASH = Pattern.compile(
            "// CRASH: (\\S+) \\(pid (\\d+)\\)");

    private boolean mMatchingAnr = false;
    private boolean mMatchingJavaCrash = false;
    private List<String> mCrash = null;
    private String mApp = null;
    private int mPid = 0;

    private MonkeyLogItem mMonkeyLog = new MonkeyLogItem();

    /**
     * Parse a monkey log from a {@link BufferedReader} into an {@link MonkeyLogItem} object.
     *
     * @param input a {@link BufferedReader}.
     * @return The {@link MonkeyLogItem}.
     * @see #parse(List)
     */
    public MonkeyLogItem parse(BufferedReader input) throws IOException {
        String line;
        while ((line = input.readLine()) != null) {
            parseLine(line);
        }

        return mMonkeyLog;
    }

    /**
     * Parse a monkey log from a {@link InputStreamSource} into an {@link MonkeyLogItem} object.
     *
     * @param input a {@link InputStreamSource}.
     * @return The {@link MonkeyLogItem}.
     * @see #parse(List)
     */
    public MonkeyLogItem parse(InputStreamSource input) throws IOException {
        InputStream stream = input.createInputStream();
        return parse(new BufferedReader(new InputStreamReader(stream)));
    }

    /**
     * {@inheritDoc}
     *
     * @return The {@link MonkeyLogItem}.
     */
    @Override
    public MonkeyLogItem parse(List<String> lines) {
        for (String line : lines) {
            parseLine(line);
        }

        return mMonkeyLog;
    }

    /**
     * Parse a line of input.
     */
    private void parseLine(String line) {
        if (mMatchingAnr || mMatchingJavaCrash) {
            if (mMatchingJavaCrash) {
                line = line.replace("// ", "");
            }
            if ("".equals(line)) {
                GenericLogcatItem crash;
                if (mMatchingAnr) {
                    crash = new AnrParser().parse(mCrash);
                } else {
                    crash = new JavaCrashParser().parse(mCrash);
                }
                crash.setPid(mPid);
                crash.setApp(mApp);
                mMonkeyLog.setCrash(crash);

                mMatchingAnr = false;
                mMatchingJavaCrash = false;
                mCrash = null;
                mApp = null;
                mPid = 0;
            } else {
                mCrash.add(line);
            }
            return;
        }

        Matcher m = THROTTLE.matcher(line);
        if (m.matches()) {
            mMonkeyLog.setThrottle(Integer.parseInt(m.group(1)));
        }
        m = SEED_AND_TARGET_COUNT.matcher(line);
        if (m.matches()) {
            mMonkeyLog.setSeed(Integer.parseInt(m.group(1)));
            mMonkeyLog.setTargetCount(Integer.parseInt(m.group(2)));
        }
        m = SECURITY_EXCEPTIONS.matcher(line);
        if (m.matches()) {
            mMonkeyLog.setIgnoreSecurityExceptions(true);
        }
        m = PACKAGES.matcher(line);
        if (m.matches()) {
            mMonkeyLog.addPackage(m.group(1));
        }
        m = CATEGORIES.matcher(line);
        if (m.matches()) {
            mMonkeyLog.addCategory(m.group(1));
        }
        m = START_UPTIME.matcher(line);
        if (m.matches()) {
            mMonkeyLog.setStartTime(parseTime(m.group(1)));
            mMonkeyLog.setStartUptimeDuration((long) (Double.parseDouble(m.group(2)) * 1000));
        }
        m = STOP_UPTIME.matcher(line);
        if (m.matches()) {
            mMonkeyLog.setStopTime(parseTime(m.group(1)));
            mMonkeyLog.setStopUptimeDuration((long) (Double.parseDouble(m.group(2)) * 1000));
            mMonkeyLog.setTotalDuration(60 * 1000 * Integer.parseInt(m.group(3)) +
                    1000 *Integer.parseInt(m.group(4)));
        }
        m = INTERMEDIATE_COUNT.matcher(line);
        if (m.matches()) {
            mMonkeyLog.setIntermediateCount(Integer.parseInt(m.group(1)));
        }
        m = FINAL_COUNT.matcher(line);
        if (m.matches()) {
            mMonkeyLog.setFinalCount(Integer.parseInt(m.group(1)));
        }
        m = FINISHED.matcher(line);
        if (m.matches()) {
            mMonkeyLog.setIsFinished(true);
        }
        m = DROPPED_KEYS.matcher(line);
        if (m.matches()) {
            mMonkeyLog.setDroppedCount(DroppedCategory.KEYS, Integer.parseInt(m.group(1)));
        }
        m = DROPPED_POINTERS.matcher(line);
        if (m.matches()) {
            mMonkeyLog.setDroppedCount(DroppedCategory.POINTERS, Integer.parseInt(m.group(1)));
        }
        m = DROPPED_TRACKBALLS.matcher(line);
        if (m.matches()) {
            mMonkeyLog.setDroppedCount(DroppedCategory.TRACKBALLS, Integer.parseInt(m.group(1)));
        }
        m = DROPPED_FLIPS.matcher(line);
        if (m.matches()) {
            mMonkeyLog.setDroppedCount(DroppedCategory.FLIPS, Integer.parseInt(m.group(1)));
        }
        m = DROPPED_ROTATIONS.matcher(line);
        if (m.matches()) {
            mMonkeyLog.setDroppedCount(DroppedCategory.ROTATIONS, Integer.parseInt(m.group(1)));
        }
        m = ANR.matcher(line);
        if (m.matches()) {
            mApp = m.group(1);
            mPid = Integer.parseInt(m.group(2));
            mCrash = new LinkedList<String>();
            mMatchingAnr = true;
        }
        m = JAVA_CRASH.matcher(line);
        if (m.matches()) {
            mApp = m.group(1);
            mPid = Integer.parseInt(m.group(2));
            mCrash = new LinkedList<String>();
            mMatchingJavaCrash = true;
        }
    }

    /**
     * Parse the timestamp and return a date.
     *
     * @param timeStr The timestamp in the format {@code E, MM/dd/yyyy hh:mm:ss a} or
     * {@code EEE MMM dd HH:mm:ss zzz yyyy}.
     * @return The {@link Date}.
     */
    private Date parseTime(String timeStr) {
        try {
            return new SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy").parse(timeStr);
        } catch (ParseException e) {
            CLog.v("Could not parse date %s with format EEE MMM dd HH:mm:ss zzz yyyy", timeStr);
        }

        try {
            return new SimpleDateFormat("E, MM/dd/yyyy hh:mm:ss a").parse(timeStr);
        } catch (ParseException e) {
            CLog.v("Could not parse date %s with format E, MM/dd/yyyy hh:mm:ss a", timeStr);
        }

        CLog.e("Could not parse date %s", timeStr);
        return null;
    }

}
