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

import com.android.tradefed.util.ArrayUtil;
import com.android.tradefed.util.brillopad.item.LogcatItem;

import junit.framework.TestCase;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * Unit tests for {@link LogcatParserTest}.
 */
public class LogcatParserTest extends TestCase {

    /**
     * Test that an ANR is parsed in the log.
     */
    public void testParse_anr() throws ParseException {
        List<String> lines = Arrays.asList(
                "04-25 17:17:08.445   312   366 E ActivityManager: ANR (application not responding) in process: com.android.package",
                "04-25 17:17:08.445   312   366 E ActivityManager: Reason: keyDispatchingTimedOut",
                "04-25 17:17:08.445   312   366 E ActivityManager: Load: 0.71 / 0.83 / 0.51",
                "04-25 17:17:08.445   312   366 E ActivityManager: 33% TOTAL: 21% user + 11% kernel + 0.3% iowait");

        LogcatItem logcat = new LogcatParser("2012").parse(lines);
        assertNotNull(logcat);
        assertEquals(parseTime("2012-04-25 17:17:08.445"), logcat.getStartTime());
        assertEquals(parseTime("2012-04-25 17:17:08.445"), logcat.getStopTime());
        assertEquals(1, logcat.getEvents().size());
        assertEquals(1, logcat.getAnrs().size());
        assertEquals(312, logcat.getAnrs().get(0).getPid().intValue());
        assertEquals(366, logcat.getAnrs().get(0).getTid().intValue());
        assertEquals("", logcat.getAnrs().get(0).getLastPreamble());
        assertEquals("", logcat.getAnrs().get(0).getProcessPreamble());
        assertEquals(parseTime("2012-04-25 17:17:08.445"), logcat.getAnrs().get(0).getEventTime());
    }

    /**
     * Test that Java crashes can be parsed.
     */
    public void testParse_java_crash() throws ParseException {
        List<String> lines = Arrays.asList(
                "04-25 09:55:47.799  3064  3082 E AndroidRuntime: java.lang.Exception",
                "04-25 09:55:47.799  3064  3082 E AndroidRuntime: \tat class.method1(Class.java:1)",
                "04-25 09:55:47.799  3064  3082 E AndroidRuntime: \tat class.method2(Class.java:2)",
                "04-25 09:55:47.799  3064  3082 E AndroidRuntime: \tat class.method3(Class.java:3)");

        LogcatItem logcat = new LogcatParser("2012").parse(lines);
        assertNotNull(logcat);
        assertEquals(parseTime("2012-04-25 09:55:47.799"), logcat.getStartTime());
        assertEquals(parseTime("2012-04-25 09:55:47.799"), logcat.getStopTime());
        assertEquals(1, logcat.getEvents().size());
        assertEquals(1, logcat.getJavaCrashes().size());
        assertEquals(3064, logcat.getJavaCrashes().get(0).getPid().intValue());
        assertEquals(3082, logcat.getJavaCrashes().get(0).getTid().intValue());
        assertEquals("", logcat.getJavaCrashes().get(0).getLastPreamble());
        assertEquals("", logcat.getJavaCrashes().get(0).getProcessPreamble());
        assertEquals(parseTime("2012-04-25 09:55:47.799"),
                logcat.getJavaCrashes().get(0).getEventTime());
    }

    /**
     * Test that native crashes can be parsed.
     */
    public void testParse_native_crash() throws ParseException {
        List<String> lines = Arrays.asList(
                "04-25 18:33:27.273   115   115 I DEBUG   : *** *** *** *** *** *** *** *** *** *** *** *** *** *** *** ***",
                "04-25 18:33:27.273   115   115 I DEBUG   : Build fingerprint: 'product:build:target'",
                "04-25 18:33:27.273   115   115 I DEBUG   : pid: 3112, tid: 3112  >>> com.google.android.browser <<<",
                "04-25 18:33:27.273   115   115 I DEBUG   : signal 11 (SIGSEGV), code 1 (SEGV_MAPERR), fault addr 00000000");

        LogcatItem logcat = new LogcatParser("2012").parse(lines);
        assertNotNull(logcat);
        assertEquals(parseTime("2012-04-25 18:33:27.273"), logcat.getStartTime());
        assertEquals(parseTime("2012-04-25 18:33:27.273"), logcat.getStopTime());
        assertEquals(1, logcat.getEvents().size());
        assertEquals(1, logcat.getNativeCrashes().size());
        assertEquals(115, logcat.getNativeCrashes().get(0).getPid().intValue());
        assertEquals(115, logcat.getNativeCrashes().get(0).getTid().intValue());
        assertEquals("", logcat.getNativeCrashes().get(0).getLastPreamble());
        assertEquals("", logcat.getNativeCrashes().get(0).getProcessPreamble());
        assertEquals(parseTime("2012-04-25 18:33:27.273"),
                logcat.getNativeCrashes().get(0).getEventTime());
    }

    /**
     * Test that multiple events can be parsed.
     */
    public void testParse_multiple_events() throws ParseException {
        List<String> lines = Arrays.asList(
                "04-25 09:55:47.799  3064  3082 E AndroidRuntime: java.lang.Exception",
                "04-25 09:55:47.799  3064  3082 E AndroidRuntime: \tat class.method1(Class.java:1)",
                "04-25 09:55:47.799  3064  3082 E AndroidRuntime: \tat class.method2(Class.java:2)",
                "04-25 09:55:47.799  3064  3082 E AndroidRuntime: \tat class.method3(Class.java:3)",
                "04-25 09:55:47.799  3065  3090 E AndroidRuntime: java.lang.Exception",
                "04-25 09:55:47.799  3065  3090 E AndroidRuntime: \tat class.method1(Class.java:1)",
                "04-25 09:55:47.799  3065  3090 E AndroidRuntime: \tat class.method2(Class.java:2)",
                "04-25 09:55:47.799  3065  3090 E AndroidRuntime: \tat class.method3(Class.java:3)",
                "04-25 17:17:08.445   312   366 E ActivityManager: ANR (application not responding) in process: com.android.package",
                "04-25 17:17:08.445   312   366 E ActivityManager: Reason: keyDispatchingTimedOut",
                "04-25 17:17:08.445   312   366 E ActivityManager: Load: 0.71 / 0.83 / 0.51",
                "04-25 17:17:08.445   312   366 E ActivityManager: 33% TOTAL: 21% user + 11% kernel + 0.3% iowait",
                "04-25 17:17:08.445   312   366 E ActivityManager: ANR (application not responding) in process: com.android.package",
                "04-25 17:17:08.445   312   366 E ActivityManager: Reason: keyDispatchingTimedOut",
                "04-25 17:17:08.445   312   366 E ActivityManager: Load: 0.71 / 0.83 / 0.51",
                "04-25 17:17:08.445   312   366 E ActivityManager: 33% TOTAL: 21% user + 11% kernel + 0.3% iowait",
                "04-25 18:33:27.273   115   115 I DEBUG   : *** *** *** *** *** *** *** *** *** *** *** *** *** *** *** ***",
                "04-25 18:33:27.273   115   115 I DEBUG   : Build fingerprint: 'product:build:target'",
                "04-25 18:33:27.273   115   115 I DEBUG   : pid: 3112, tid: 3112  >>> com.google.android.browser <<<",
                "04-25 18:33:27.273   115   115 I DEBUG   : signal 11 (SIGSEGV), code 1 (SEGV_MAPERR), fault addr 00000000",
                "04-25 18:33:27.273   117   117 I DEBUG   : *** *** *** *** *** *** *** *** *** *** *** *** *** *** *** ***",
                "04-25 18:33:27.273   117   117 I DEBUG   : Build fingerprint: 'product:build:target'",
                "04-25 18:33:27.273   117   117 I DEBUG   : pid: 3112, tid: 3112  >>> com.google.android.browser <<<",
                "04-25 18:33:27.273   117   117 I DEBUG   : signal 11 (SIGSEGV), code 1 (SEGV_MAPERR), fault addr 00000000");


        LogcatItem logcat = new LogcatParser("2012").parse(lines);
        assertNotNull(logcat);
        assertEquals(parseTime("2012-04-25 09:55:47.799"), logcat.getStartTime());
        assertEquals(parseTime("2012-04-25 18:33:27.273"), logcat.getStopTime());
        assertEquals(6, logcat.getEvents().size());
        assertEquals(2, logcat.getAnrs().size());
        assertEquals(2, logcat.getJavaCrashes().size());
        assertEquals(2, logcat.getNativeCrashes().size());

        assertEquals(312, logcat.getAnrs().get(0).getPid().intValue());
        assertEquals(366, logcat.getAnrs().get(0).getTid().intValue());
        assertEquals(parseTime("2012-04-25 17:17:08.445"), logcat.getAnrs().get(0).getEventTime());

        assertEquals(312, logcat.getAnrs().get(1).getPid().intValue());
        assertEquals(366, logcat.getAnrs().get(1).getTid().intValue());
        assertEquals(parseTime("2012-04-25 17:17:08.445"), logcat.getAnrs().get(1).getEventTime());

        assertEquals(3064, logcat.getJavaCrashes().get(0).getPid().intValue());
        assertEquals(3082, logcat.getJavaCrashes().get(0).getTid().intValue());
        assertEquals(parseTime("2012-04-25 09:55:47.799"),
                logcat.getJavaCrashes().get(0).getEventTime());

        assertEquals(3065, logcat.getJavaCrashes().get(1).getPid().intValue());
        assertEquals(3090, logcat.getJavaCrashes().get(1).getTid().intValue());
        assertEquals(parseTime("2012-04-25 09:55:47.799"),
                logcat.getJavaCrashes().get(1).getEventTime());

        assertEquals(115, logcat.getNativeCrashes().get(0).getPid().intValue());
        assertEquals(115, logcat.getNativeCrashes().get(0).getTid().intValue());
        assertEquals(parseTime("2012-04-25 18:33:27.273"),
                logcat.getNativeCrashes().get(0).getEventTime());

        assertEquals(117, logcat.getNativeCrashes().get(1).getPid().intValue());
        assertEquals(117, logcat.getNativeCrashes().get(1).getTid().intValue());
        assertEquals(parseTime("2012-04-25 18:33:27.273"),
                logcat.getNativeCrashes().get(1).getEventTime());
    }

    /**
     * Test that multiple java crashes and native crashes can be parsed even when interleaved.
     */
    public void testParse_multiple_events_interleaved() throws ParseException {
        List<String> lines = Arrays.asList(
                "04-25 09:55:47.799  3064  3082 E AndroidRuntime: java.lang.Exception",
                "04-25 09:55:47.799  3065  3090 E AndroidRuntime: java.lang.Exception",
                "04-25 09:55:47.799   115   115 I DEBUG   : *** *** *** *** *** *** *** *** *** *** *** *** *** *** *** ***",
                "04-25 09:55:47.799   117   117 I DEBUG   : *** *** *** *** *** *** *** *** *** *** *** *** *** *** *** ***",
                "04-25 09:55:47.799  3064  3082 E AndroidRuntime: \tat class.method1(Class.java:1)",
                "04-25 09:55:47.799  3065  3090 E AndroidRuntime: \tat class.method1(Class.java:1)",
                "04-25 09:55:47.799   115   115 I DEBUG   : Build fingerprint: 'product:build:target'",
                "04-25 09:55:47.799   117   117 I DEBUG   : Build fingerprint: 'product:build:target'",
                "04-25 09:55:47.799  3064  3082 E AndroidRuntime: \tat class.method2(Class.java:2)",
                "04-25 09:55:47.799  3065  3090 E AndroidRuntime: \tat class.method2(Class.java:2)",
                "04-25 09:55:47.799   115   115 I DEBUG   : pid: 3112, tid: 3112  >>> com.google.android.browser <<<",
                "04-25 09:55:47.799   117   117 I DEBUG   : pid: 3112, tid: 3112  >>> com.google.android.browser <<<",
                "04-25 09:55:47.799  3064  3082 E AndroidRuntime: \tat class.method3(Class.java:3)",
                "04-25 09:55:47.799  3065  3090 E AndroidRuntime: \tat class.method3(Class.java:3)",
                "04-25 09:55:47.799   115   115 I DEBUG   : signal 11 (SIGSEGV), code 1 (SEGV_MAPERR), fault addr 00000000",
                "04-25 09:55:47.799   117   117 I DEBUG   : signal 11 (SIGSEGV), code 1 (SEGV_MAPERR), fault addr 00000000");

        LogcatItem logcat = new LogcatParser("2012").parse(lines);
        assertNotNull(logcat);
        assertEquals(parseTime("2012-04-25 09:55:47.799"), logcat.getStartTime());
        assertEquals(parseTime("2012-04-25 09:55:47.799"), logcat.getStopTime());
        assertEquals(4, logcat.getEvents().size());
        assertEquals(0, logcat.getAnrs().size());
        assertEquals(2, logcat.getJavaCrashes().size());
        assertEquals(2, logcat.getNativeCrashes().size());

        assertEquals(3064, logcat.getJavaCrashes().get(0).getPid().intValue());
        assertEquals(3082, logcat.getJavaCrashes().get(0).getTid().intValue());
        assertEquals(parseTime("2012-04-25 09:55:47.799"),
                logcat.getJavaCrashes().get(0).getEventTime());

        assertEquals(3065, logcat.getJavaCrashes().get(1).getPid().intValue());
        assertEquals(3090, logcat.getJavaCrashes().get(1).getTid().intValue());
        assertEquals(parseTime("2012-04-25 09:55:47.799"),
                logcat.getJavaCrashes().get(1).getEventTime());

        assertEquals(115, logcat.getNativeCrashes().get(0).getPid().intValue());
        assertEquals(115, logcat.getNativeCrashes().get(0).getTid().intValue());
        assertEquals(parseTime("2012-04-25 09:55:47.799"),
                logcat.getNativeCrashes().get(0).getEventTime());

        assertEquals(117, logcat.getNativeCrashes().get(1).getPid().intValue());
        assertEquals(117, logcat.getNativeCrashes().get(1).getTid().intValue());
        assertEquals(parseTime("2012-04-25 09:55:47.799"),
                logcat.getNativeCrashes().get(1).getEventTime());
    }

    /**
     * Test that the preambles are set correctly if there's only partial preambles.
     */
    public void testParse_partial_preambles() throws ParseException {
        List<String> lines = Arrays.asList(
                "04-25 09:15:47.799   123  3082 I tag: message 1",
                "04-25 09:20:47.799  3064  3082 I tag: message 2",
                "04-25 09:25:47.799   345  3082 I tag: message 3",
                "04-25 09:30:47.799  3064  3082 I tag: message 4",
                "04-25 09:35:47.799   456  3082 I tag: message 5",
                "04-25 09:40:47.799  3064  3082 I tag: message 6",
                "04-25 09:45:47.799   567  3082 I tag: message 7",
                "04-25 09:50:47.799  3064  3082 I tag: message 8",
                "04-25 09:55:47.799  3064  3082 E AndroidRuntime: java.lang.Exception",
                "04-25 09:55:47.799  3064  3082 E AndroidRuntime: \tat class.method1(Class.java:1)",
                "04-25 09:55:47.799  3064  3082 E AndroidRuntime: \tat class.method2(Class.java:2)",
                "04-25 09:55:47.799  3064  3082 E AndroidRuntime: \tat class.method3(Class.java:3)");

        List<String> expectedLastPreamble = Arrays.asList(
                "04-25 09:15:47.799   123  3082 I tag: message 1",
                "04-25 09:20:47.799  3064  3082 I tag: message 2",
                "04-25 09:25:47.799   345  3082 I tag: message 3",
                "04-25 09:30:47.799  3064  3082 I tag: message 4",
                "04-25 09:35:47.799   456  3082 I tag: message 5",
                "04-25 09:40:47.799  3064  3082 I tag: message 6",
                "04-25 09:45:47.799   567  3082 I tag: message 7",
                "04-25 09:50:47.799  3064  3082 I tag: message 8");

        List<String> expectedProcPreamble = Arrays.asList(
                "04-25 09:20:47.799  3064  3082 I tag: message 2",
                "04-25 09:30:47.799  3064  3082 I tag: message 4",
                "04-25 09:40:47.799  3064  3082 I tag: message 6",
                "04-25 09:50:47.799  3064  3082 I tag: message 8");

        LogcatItem logcat = new LogcatParser("2012").parse(lines);
        assertNotNull(logcat);
        assertEquals(parseTime("2012-04-25 09:15:47.799"), logcat.getStartTime());
        assertEquals(parseTime("2012-04-25 09:55:47.799"), logcat.getStopTime());
        assertEquals(1, logcat.getEvents().size());
        assertEquals(1, logcat.getJavaCrashes().size());
        assertEquals(3064, logcat.getJavaCrashes().get(0).getPid().intValue());
        assertEquals(3082, logcat.getJavaCrashes().get(0).getTid().intValue());
        assertEquals(ArrayUtil.join("\n", expectedLastPreamble),
                logcat.getJavaCrashes().get(0).getLastPreamble());
        assertEquals(ArrayUtil.join("\n", expectedProcPreamble),
                logcat.getJavaCrashes().get(0).getProcessPreamble());
        assertEquals(parseTime("2012-04-25 09:55:47.799"),
                logcat.getJavaCrashes().get(0).getEventTime());
    }

    /**
     * Test that the preambles are set correctly if there's only full preambles.
     */
    public void testParse_preambles() throws ParseException {
        List<String> lines = Arrays.asList(
                "04-25 09:43:47.799  3064  3082 I tag: message 1",
                "04-25 09:44:47.799   123  3082 I tag: message 2",
                "04-25 09:45:47.799  3064  3082 I tag: message 3",
                "04-25 09:46:47.799   234  3082 I tag: message 4",
                "04-25 09:47:47.799  3064  3082 I tag: message 5",
                "04-25 09:48:47.799   345  3082 I tag: message 6",
                "04-25 09:49:47.799  3064  3082 I tag: message 7",
                "04-25 09:50:47.799   456  3082 I tag: message 8",
                "04-25 09:55:47.799  3064  3082 E AndroidRuntime: java.lang.Exception",
                "04-25 09:55:47.799  3064  3082 E AndroidRuntime: \tat class.method1(Class.java:1)",
                "04-25 09:55:47.799  3064  3082 E AndroidRuntime: \tat class.method2(Class.java:2)",
                "04-25 09:55:47.799  3064  3082 E AndroidRuntime: \tat class.method3(Class.java:3)");

        List<String> expectedLastPreamble = Arrays.asList(
                "04-25 09:48:47.799   345  3082 I tag: message 6",
                "04-25 09:49:47.799  3064  3082 I tag: message 7",
                "04-25 09:50:47.799   456  3082 I tag: message 8");

        List<String> expectedProcPreamble = Arrays.asList(
                "04-25 09:45:47.799  3064  3082 I tag: message 3",
                "04-25 09:47:47.799  3064  3082 I tag: message 5",
                "04-25 09:49:47.799  3064  3082 I tag: message 7");

        LogcatItem logcat = new LogcatParser("2012") {
            @Override
            int getLastPreambleSize() {
                return 3;
            }

            @Override
            int getProcPreambleSize() {
                return 3;
            }
        }.parse(lines);

        assertNotNull(logcat);
        assertEquals(parseTime("2012-04-25 09:43:47.799"), logcat.getStartTime());
        assertEquals(parseTime("2012-04-25 09:55:47.799"), logcat.getStopTime());
        assertEquals(1, logcat.getEvents().size());
        assertEquals(1, logcat.getJavaCrashes().size());
        assertEquals(3064, logcat.getJavaCrashes().get(0).getPid().intValue());
        assertEquals(3082, logcat.getJavaCrashes().get(0).getTid().intValue());
        assertEquals(ArrayUtil.join("\n", expectedLastPreamble),
                logcat.getJavaCrashes().get(0).getLastPreamble());
        assertEquals(ArrayUtil.join("\n", expectedProcPreamble),
                logcat.getJavaCrashes().get(0).getProcessPreamble());
        assertEquals(parseTime("2012-04-25 09:55:47.799"),
                logcat.getJavaCrashes().get(0).getEventTime());
    }

    /**
     * Test that the time logcat format can be parsed.
     */
    public void testParse_time() throws ParseException {
        List<String> lines = Arrays.asList(
                "04-25 09:55:47.799  E/AndroidRuntime(3064): java.lang.Exception",
                "04-25 09:55:47.799  E/AndroidRuntime(3064): \tat class.method1(Class.java:1)",
                "04-25 09:55:47.799  E/AndroidRuntime(3064): \tat class.method2(Class.java:2)",
                "04-25 09:55:47.799  E/AndroidRuntime(3064): \tat class.method3(Class.java:3)");

        LogcatItem logcat = new LogcatParser("2012").parse(lines);
        assertNotNull(logcat);
        assertEquals(parseTime("2012-04-25 09:55:47.799"), logcat.getStartTime());
        assertEquals(parseTime("2012-04-25 09:55:47.799"), logcat.getStopTime());
        assertEquals(1, logcat.getEvents().size());
        assertEquals(1, logcat.getJavaCrashes().size());
        assertEquals(3064, logcat.getJavaCrashes().get(0).getPid().intValue());
        assertNull(logcat.getJavaCrashes().get(0).getTid());
        assertEquals(parseTime("2012-04-25 09:55:47.799"),
                logcat.getJavaCrashes().get(0).getEventTime());
    }

    private Date parseTime(String timeStr) throws ParseException {
        DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        return formatter.parse(timeStr);
    }
}
