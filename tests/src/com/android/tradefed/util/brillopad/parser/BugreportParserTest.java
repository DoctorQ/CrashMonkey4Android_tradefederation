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
package com.android.tradefed.util.brillopad.parser;

import com.android.tradefed.util.ArrayUtil;
import com.android.tradefed.util.brillopad.item.BugreportItem;

import junit.framework.TestCase;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * Unit tests for {@link BugreportParser}
 */
public class BugreportParserTest extends TestCase {

    /**
     * Test that a bugreport can be parsed.
     */
    public void testParse() throws ParseException {
        List<String> lines = Arrays.asList(
                "========================================================",
                "== dumpstate: 2012-04-25 20:45:10",
                "========================================================",
                "------ SECTION ------",
                "",
                "------ MEMORY INFO (/proc/meminfo) ------",
                "MemTotal:         353332 kB",
                "MemFree:           65420 kB",
                "Buffers:           20800 kB",
                "Cached:            86204 kB",
                "SwapCached:            0 kB",
                "",
                "------ PROCRANK (procrank) ------",
                "  PID      Vss      Rss      Pss      Uss  cmdline",
                "  178   87136K   81684K   52829K   50012K  system_server",
                " 1313   78128K   77996K   48603K   45812K  com.google.android.apps.maps",
                " 3247   61652K   61492K   33122K   30972K  com.android.browser",
                "                          ------   ------  ------",
                "                          203624K  163604K  TOTAL",
                "RAM: 731448K total, 415804K free, 9016K buffers, 108548K cached",
                "[procrank: 1.6s elapsed]",
                "",
                "------ SYSTEM LOG (logcat -v threadtime -d *:v) ------",
                "04-25 09:55:47.799  3064  3082 E AndroidRuntime: java.lang.Exception",
                "04-25 09:55:47.799  3064  3082 E AndroidRuntime: \tat class.method1(Class.java:1)",
                "04-25 09:55:47.799  3064  3082 E AndroidRuntime: \tat class.method2(Class.java:2)",
                "04-25 09:55:47.799  3064  3082 E AndroidRuntime: \tat class.method3(Class.java:3)",
                "04-25 17:17:08.445   312   366 E ActivityManager: ANR (application not responding) in process: com.android.package",
                "04-25 17:17:08.445   312   366 E ActivityManager: Reason: keyDispatchingTimedOut",
                "04-25 17:17:08.445   312   366 E ActivityManager: Load: 0.71 / 0.83 / 0.51",
                "04-25 17:17:08.445   312   366 E ActivityManager: 33% TOTAL: 21% user + 11% kernel + 0.3% iowait",
                "04-25 18:33:27.273   115   115 I DEBUG   : *** *** *** *** *** *** *** *** *** *** *** *** *** *** *** ***",
                "04-25 18:33:27.273   115   115 I DEBUG   : Build fingerprint: 'product:build:target'",
                "04-25 18:33:27.273   115   115 I DEBUG   : pid: 3112, tid: 3112  >>> com.google.android.browser <<<",
                "04-25 18:33:27.273   115   115 I DEBUG   : signal 11 (SIGSEGV), code 1 (SEGV_MAPERR), fault addr 00000000",
                "",
                "------ SYSTEM PROPERTIES ------",
                "[dalvik.vm.dexopt-flags]: [m=y]",
                "[dalvik.vm.heapgrowthlimit]: [48m]",
                "[dalvik.vm.heapsize]: [256m]",
                "[gsm.version.ril-impl]: [android moto-ril-multimode 1.0]",
                "",
                "------ SECTION ------",
                "",
                "------ VM TRACES AT LAST ANR (/data/anr/traces.txt: 2012-04-25 17:17:08) ------",
                "",
                "",
                "----- pid 2887 at 2012-04-25 17:17:08 -----",
                "Cmd line: com.android.package",
                "",
                "DALVIK THREADS:",
                "(mutexes: tll=0 tsl=0 tscl=0 ghl=0)",
                "",
                "\"main\" prio=5 tid=1 SUSPENDED",
                "  | group=\"main\" sCount=1 dsCount=0 obj=0x00000001 self=0x00000001",
                "  | sysTid=2887 nice=0 sched=0/0 cgrp=foreground handle=0000000001",
                "  | schedstat=( 0 0 0 ) utm=5954 stm=1017 core=0",
                "  at class.method1(Class.java:1)",
                "  at class.method2(Class.java:2)",
                "  at class.method2(Class.java:2)",
                "",
                "----- end 2887 -----",
                "",
                "------ SECTION ------",
                "");

        BugreportItem bugreport = new BugreportParser().parse(lines);
        assertNotNull(bugreport);
        assertEquals(parseTime("2012-04-25 20:45:10.000"), bugreport.getTime());

        assertNotNull(bugreport.getMemInfo());
        assertEquals(5, bugreport.getMemInfo().size());

        assertNotNull(bugreport.getProcrank());
        assertEquals(3, bugreport.getProcrank().getPids().size());

        assertNotNull(bugreport.getSystemLog());
        assertEquals(parseTime("2012-04-25 09:55:47.799"), bugreport.getSystemLog().getStartTime());
        assertEquals(parseTime("2012-04-25 18:33:27.273"), bugreport.getSystemLog().getStopTime());
        assertEquals(3, bugreport.getSystemLog().getEvents().size());
        assertEquals(1, bugreport.getSystemLog().getAnrs().size());
        assertNotNull(bugreport.getSystemLog().getAnrs().get(0).getTrace());

        assertNotNull(bugreport.getSystemProps());
        assertEquals(4, bugreport.getSystemProps().size());
    }

    /**
     * Test that the logcat year is set correctly from the bugreport timestamp.
     */
    public void testParse_set_logcat_year() throws ParseException {
        List<String> lines = Arrays.asList(
                "========================================================",
                "== dumpstate: 1999-01-01 02:03:04",
                "========================================================",
                "------ SYSTEM LOG (logcat -v threadtime -d *:v) ------",
                "01-01 01:02:03.000     1     1 I TAG     : message",
                "01-01 01:02:04.000     1     1 I TAG     : message",
                "");

        BugreportItem bugreport = new BugreportParser().parse(lines);
        assertNotNull(bugreport);
        assertEquals(parseTime("1999-01-01 02:03:04.000"), bugreport.getTime());
        assertNotNull(bugreport.getSystemLog());
        assertEquals(parseTime("1999-01-01 01:02:03.000"), bugreport.getSystemLog().getStartTime());
        assertEquals(parseTime("1999-01-01 01:02:04.000"), bugreport.getSystemLog().getStopTime());
    }

    /**
     * Test that the trace is set correctly if there is only one ANR.
     */
    public void testSetAnrTrace_single() {
        List<String> lines = Arrays.asList(
                "========================================================",
                "== dumpstate: 2012-04-25 20:45:10",
                "========================================================",
                "------ SYSTEM LOG (logcat -v threadtime -d *:v) ------",
                "04-25 17:17:08.445   312   366 E ActivityManager: ANR (application not responding) in process: com.android.package",
                "04-25 17:17:08.445   312   366 E ActivityManager: Reason: keyDispatchingTimedOut",
                "04-25 17:17:08.445   312   366 E ActivityManager: Load: 0.71 / 0.83 / 0.51",
                "04-25 17:17:08.445   312   366 E ActivityManager: 33% TOTAL: 21% user + 11% kernel + 0.3% iowait",
                "",
                "------ VM TRACES AT LAST ANR (/data/anr/traces.txt: 2012-04-25 17:17:08) ------",
                "",
                "----- pid 2887 at 2012-04-25 17:17:08 -----",
                "Cmd line: com.android.package",
                "",
                "DALVIK THREADS:",
                "(mutexes: tll=0 tsl=0 tscl=0 ghl=0)",
                "",
                "\"main\" prio=5 tid=1 SUSPENDED",
                "  | group=\"main\" sCount=1 dsCount=0 obj=0x00000001 self=0x00000001",
                "  | sysTid=2887 nice=0 sched=0/0 cgrp=foreground handle=0000000001",
                "  | schedstat=( 0 0 0 ) utm=5954 stm=1017 core=0",
                "  at class.method1(Class.java:1)",
                "  at class.method2(Class.java:2)",
                "  at class.method2(Class.java:2)",
                "",
                "----- end 2887 -----",
                "");

        List<String> expectedStack = Arrays.asList(
                "\"main\" prio=5 tid=1 SUSPENDED",
                "  | group=\"main\" sCount=1 dsCount=0 obj=0x00000001 self=0x00000001",
                "  | sysTid=2887 nice=0 sched=0/0 cgrp=foreground handle=0000000001",
                "  | schedstat=( 0 0 0 ) utm=5954 stm=1017 core=0",
                "  at class.method1(Class.java:1)",
                "  at class.method2(Class.java:2)",
                "  at class.method2(Class.java:2)");

        BugreportItem bugreport = new BugreportParser().parse(lines);

        assertNotNull(bugreport.getSystemLog());
        assertEquals(1, bugreport.getSystemLog().getAnrs().size());
        assertEquals(ArrayUtil.join("\n", expectedStack),
                bugreport.getSystemLog().getAnrs().get(0).getTrace());
    }

    /**
     * Test that the trace is set correctly if there are multiple ANRs.
     */
    public void testSetAnrTrace_multiple() {
        List<String> lines = Arrays.asList(
                "========================================================",
                "== dumpstate: 2012-04-25 20:45:10",
                "========================================================",
                "------ SYSTEM LOG (logcat -v threadtime -d *:v) ------",
                "04-25 17:17:08.445   312   366 E ActivityManager: ANR (application not responding) in process: com.android.package",
                "04-25 17:17:08.445   312   366 E ActivityManager: Reason: keyDispatchingTimedOut",
                "04-25 17:17:08.445   312   366 E ActivityManager: Load: 0.71 / 0.83 / 0.51",
                "04-25 17:17:08.445   312   366 E ActivityManager: 33% TOTAL: 21% user + 11% kernel + 0.3% iowait",
                "04-25 17:18:08.445   312   366 E ActivityManager: ANR (application not responding) in process: com.android.package",
                "04-25 17:18:08.445   312   366 E ActivityManager: Reason: keyDispatchingTimedOut",
                "04-25 17:18:08.445   312   366 E ActivityManager: Load: 0.71 / 0.83 / 0.51",
                "04-25 17:18:08.445   312   366 E ActivityManager: 33% TOTAL: 21% user + 11% kernel + 0.3% iowait",
                "04-25 17:19:08.445   312   366 E ActivityManager: ANR (application not responding) in process: com.android.different.pacakge",
                "04-25 17:19:08.445   312   366 E ActivityManager: Reason: keyDispatchingTimedOut",
                "04-25 17:19:08.445   312   366 E ActivityManager: Load: 0.71 / 0.83 / 0.51",
                "04-25 17:19:08.445   312   366 E ActivityManager: 33% TOTAL: 21% user + 11% kernel + 0.3% iowait",
                "",
                "------ VM TRACES AT LAST ANR (/data/anr/traces.txt: 2012-04-25 17:18:08) ------",
                "",
                "----- pid 2887 at 2012-04-25 17:17:08 -----",
                "Cmd line: com.android.package",
                "",
                "DALVIK THREADS:",
                "(mutexes: tll=0 tsl=0 tscl=0 ghl=0)",
                "",
                "\"main\" prio=5 tid=1 SUSPENDED",
                "  | group=\"main\" sCount=1 dsCount=0 obj=0x00000001 self=0x00000001",
                "  | sysTid=2887 nice=0 sched=0/0 cgrp=foreground handle=0000000001",
                "  | schedstat=( 0 0 0 ) utm=5954 stm=1017 core=0",
                "  at class.method1(Class.java:1)",
                "  at class.method2(Class.java:2)",
                "  at class.method2(Class.java:2)",
                "",
                "----- end 2887 -----",
                "");

        List<String> expectedStack = Arrays.asList(
                "\"main\" prio=5 tid=1 SUSPENDED",
                "  | group=\"main\" sCount=1 dsCount=0 obj=0x00000001 self=0x00000001",
                "  | sysTid=2887 nice=0 sched=0/0 cgrp=foreground handle=0000000001",
                "  | schedstat=( 0 0 0 ) utm=5954 stm=1017 core=0",
                "  at class.method1(Class.java:1)",
                "  at class.method2(Class.java:2)",
                "  at class.method2(Class.java:2)");

        BugreportItem bugreport = new BugreportParser().parse(lines);

        assertNotNull(bugreport.getSystemLog());
        assertEquals(3, bugreport.getSystemLog().getAnrs().size());
        assertNull(bugreport.getSystemLog().getAnrs().get(0).getTrace());
        assertEquals(ArrayUtil.join("\n", expectedStack),
                bugreport.getSystemLog().getAnrs().get(1).getTrace());
        assertNull(bugreport.getSystemLog().getAnrs().get(2).getTrace());
    }

    /**
     * Test that the trace is set correctly if there is not traces file.
     */
    public void testSetAnrTrace_no_traces() {
        List<String> lines = Arrays.asList(
                "========================================================",
                "== dumpstate: 2012-04-25 20:45:10",
                "========================================================",
                "------ SYSTEM LOG (logcat -v threadtime -d *:v) ------",
                "04-25 17:17:08.445   312   366 E ActivityManager: ANR (application not responding) in process: com.android.package",
                "04-25 17:17:08.445   312   366 E ActivityManager: Reason: keyDispatchingTimedOut",
                "04-25 17:17:08.445   312   366 E ActivityManager: Load: 0.71 / 0.83 / 0.51",
                "04-25 17:17:08.445   312   366 E ActivityManager: 33% TOTAL: 21% user + 11% kernel + 0.3% iowait",
                "",
                "*** NO ANR VM TRACES FILE (/data/anr/traces.txt): No such file or directory",
                "");

        BugreportItem bugreport = new BugreportParser().parse(lines);

        assertNotNull(bugreport.getSystemLog());
        assertEquals(1, bugreport.getSystemLog().getAnrs().size());
        assertNull(bugreport.getSystemLog().getAnrs().get(0).getTrace());
    }

    /**
     * Test that app names from logcat events are populated by matching the logcat PIDs with the
     * PIDs from the logcat.
     */
    public void testSetAppsFromProcrank() {
        List<String> lines = Arrays.asList(
                "========================================================",
                "== dumpstate: 2012-04-25 20:45:10",
                "========================================================",
                "------ PROCRANK (procrank) ------",
                "  PID      Vss      Rss      Pss      Uss  cmdline",
                " 3064   87136K   81684K   52829K   50012K  com.android.package",
                "                          ------   ------  ------",
                "                          203624K  163604K  TOTAL",
                "RAM: 731448K total, 415804K free, 9016K buffers, 108548K cached",
                "[procrank: 1.6s elapsed]",
                "------ SYSTEM LOG (logcat -v threadtime -d *:v) ------",
                "04-25 09:55:47.799  3064  3082 E AndroidRuntime: java.lang.Exception",
                "04-25 09:55:47.799  3064  3082 E AndroidRuntime: \tat class.method1(Class.java:1)",
                "04-25 09:55:47.799  3064  3082 E AndroidRuntime: \tat class.method2(Class.java:2)",
                "04-25 09:55:47.799  3064  3082 E AndroidRuntime: \tat class.method3(Class.java:3)",
                "04-25 09:55:47.799  3065  3083 E AndroidRuntime: java.lang.Exception",
                "04-25 09:55:47.799  3065  3083 E AndroidRuntime: \tat class.method1(Class.java:1)",
                "04-25 09:55:47.799  3065  3083 E AndroidRuntime: \tat class.method2(Class.java:2)",
                "04-25 09:55:47.799  3065  3083 E AndroidRuntime: \tat class.method3(Class.java:3)");

        BugreportItem bugreport = new BugreportParser().parse(lines);
        assertNotNull(bugreport.getSystemLog());
        assertEquals(2, bugreport.getSystemLog().getJavaCrashes().size());
        assertEquals("com.android.package",
                bugreport.getSystemLog().getJavaCrashes().get(0).getApp());
        assertNull(bugreport.getSystemLog().getJavaCrashes().get(1).getApp());
    }

    private Date parseTime(String timeStr) throws ParseException {
        DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        return formatter.parse(timeStr);
    }

    /**
     * Some Android devices refer to SYSTEM LOG as MAIN LOG. Check that parser recognizes this
     * alternate syntax.
     */
    public void testSystemLogAsMainLog() {
        List<String> lines = Arrays.asList(
                "------ MAIN LOG (logcat -b main -b system -v threadtime -d *:v) ------",
                "--------- beginning of /dev/log/system",
                "12-11 19:48:07.945  1484  1508 D BatteryService: update start");
        BugreportItem bugreport = new BugreportParser().parse(lines);
        assertNotNull(bugreport.getSystemLog());
    }
}

