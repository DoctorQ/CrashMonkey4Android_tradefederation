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

import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.util.brillopad.item.AnrItem;
import com.android.tradefed.util.brillopad.item.BugreportItem;
import com.android.tradefed.util.brillopad.item.LogcatItem;
import com.android.tradefed.util.brillopad.item.MemInfoItem;
import com.android.tradefed.util.brillopad.item.ProcrankItem;
import com.android.tradefed.util.brillopad.item.SystemPropsItem;
import com.android.tradefed.util.brillopad.item.TracesItem;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.ListIterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A {@link IParser} to parse Android bugreports.
 */
public class BugreportParser extends AbstractSectionParser {
    private static final String MEM_INFO_SECTION_REGEX = "------ MEMORY INFO .*";
    private static final String PROCRANK_SECTION_REGEX = "------ PROCRANK .*";
    private static final String SYSTEM_PROP_SECTION_REGEX = "------ SYSTEM PROPERTIES .*";
    private static final String SYSTEM_LOG_SECTION_REGEX = "------ SYSTEM LOG .*";
    private static final String ANR_TRACES_SECTION_REGEX = "------ VM TRACES AT LAST ANR .*";
    private static final String NOOP_SECTION_REGEX = "------ .*";

    /**
     * Matches: == dumpstate: 2012-04-26 12:13:14
     */
    private static final Pattern DATE = Pattern.compile(
            "^== dumpstate: (\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2})$");

    private LogcatParser mLogcatParser = new LogcatParser();
    private BugreportItem mBugreport = null;

    /**
     * Parse a bugreport from a {@link BufferedReader} into an {@link BugreportItem} object.
     *
     * @param input a {@link BufferedReader}.
     * @return The {@link BugreportItem}.
     * @see #parse(List)
     */
    public BugreportItem parse(BufferedReader input) throws IOException {
        String line;

        setup();
        while ((line = input.readLine()) != null) {
            parseLine(line);
        }
        commit();

        return mBugreport;
    }

    /**
     * Parse a bugreport from a {@link InputStreamSource} into an {@link BugreportItem} object.
     *
     * @param input a {@link InputStreamSource}.
     * @return The {@link BugreportItem}.
     * @see #parse(List)
     */
    public BugreportItem parse(InputStreamSource input) throws IOException {
        InputStream stream = input.createInputStream();
        return parse(new BufferedReader(new InputStreamReader(stream)));
    }

    /**
     * {@inheritDoc}
     *
     * @return The {@link BugreportItem}.
     */
    @Override
    public BugreportItem parse(List<String> lines) {
        setup();
        for (String line : lines) {
            parseLine(line);
        }
        commit();

        return mBugreport;
    }

    /**
     * Sets up the parser by adding the section parsers and adding an initial {@link IParser} to
     * parse the bugreport header.
     */
    protected void setup() {
        // Set the initial parser explicitly since the header isn't part of a section.
        setParser(new IParser() {
            @Override
            public BugreportItem parse(List<String> lines) {
                BugreportItem bugreport = new BugreportItem();
                for (String line : lines) {
                    Matcher m = DATE.matcher(line);
                    if (m.matches()) {
                        bugreport.setTime(parseTime(m.group(1)));
                    }
                }
                return bugreport;
            }
        });
        addSectionParser(new MemInfoParser(), MEM_INFO_SECTION_REGEX);
        addSectionParser(new ProcrankParser(), PROCRANK_SECTION_REGEX);
        addSectionParser(new SystemPropsParser(), SYSTEM_PROP_SECTION_REGEX);
        addSectionParser(new TracesParser(), ANR_TRACES_SECTION_REGEX);
        addSectionParser(mLogcatParser, SYSTEM_LOG_SECTION_REGEX);
        addSectionParser(new NoopParser(), NOOP_SECTION_REGEX);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void commit() {
        // signal EOF
        super.commit();

        if (mBugreport != null) {
            mBugreport.setMemInfo((MemInfoItem) getSection(MemInfoItem.TYPE));
            mBugreport.setProcrank((ProcrankItem) getSection(ProcrankItem.TYPE));
            mBugreport.setSystemLog((LogcatItem) getSection(LogcatItem.TYPE));
            mBugreport.setSystemProps((SystemPropsItem) getSection(SystemPropsItem.TYPE));

            TracesItem traces = (TracesItem) getSection(TracesItem.TYPE);
            if (traces != null && traces.getApp() != null && traces.getStack() != null &&
                    mBugreport.getSystemLog() != null) {
                addAnrTrace(mBugreport.getSystemLog().getAnrs(), traces.getApp(),
                        traces.getStack());

            }
        }
    }

    /**
     * Add the trace from {@link TracesItem} to the last seen {@link AnrItem} matching a given app.
     */
    private void addAnrTrace(List<AnrItem> anrs, String app, String trace) {
        ListIterator<AnrItem> li = anrs.listIterator(anrs.size());

        while (li.hasPrevious()) {
            AnrItem anr = li.previous();
            if (app.equals(anr.getApp())) {
                anr.setTrace(trace);
                return;
            }
        }
    }

    /**
     * Set the {@link BugreportItem} and the year of the {@link LogcatParser} from the bugreport
     * header.
     */
    @Override
    protected void onSwitchParser() {
        if (mBugreport == null) {
            mBugreport = (BugreportItem) getSection(BugreportItem.TYPE);
            if (mBugreport.getTime() != null) {
                mLogcatParser.setYear(new SimpleDateFormat("yyyy").format(mBugreport.getTime()));
            }
        }
    }

    /**
     * Converts a {@link String} into a {@link Date}.
     */
    private static Date parseTime(String timeStr) {
        DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        try {
            return formatter.parse(timeStr);
        } catch (ParseException e) {
            CLog.e("Could not parse time string %s", timeStr);
            return null;
        }
    }
}

