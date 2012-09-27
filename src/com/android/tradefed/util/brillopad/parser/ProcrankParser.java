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

import com.android.tradefed.util.brillopad.item.ProcrankItem;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A {@link IParser} to handle the output of {@code procrank}.  Memory values returned are in units
 * of kilobytes.
 */
public class ProcrankParser implements IParser {

    /** Match a valid line, such as:
     * " 1313   78128K   77996K   48603K   45812K  com.google.android.apps.maps" */
    private static final Pattern LINE_PAT = Pattern.compile(
            "\\s*(\\d+)\\s+" + /* PID [1] */
            "(\\d+)K\\s+(\\d+)K\\s+(\\d+)K\\s+(\\d+)K\\s+" + /* Vss Rss Pss Uss [2-5] */
            "(\\S+)" /* process name [6] */);

    /** Match the end of the Procrank table, determined by three sets of "------". */
    private static final Pattern END_PAT = Pattern.compile("^\\s+-{6}\\s+-{6}\\s+-{6}");

    /**
     * {@inheritDoc}
     */
    @Override
    public ProcrankItem parse(List<String> lines) {
        ProcrankItem item = new ProcrankItem();

        for (String line : lines) {
            // If we have reached the end.
            Matcher endMatcher = END_PAT.matcher(line);
            if (endMatcher.matches()) {
                return item;
            }

            Matcher m = LINE_PAT.matcher(line);
            if (m.matches()) {
                item.addProcrankLine(Integer.parseInt(m.group(1)), m.group(6),
                        Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)),
                        Integer.parseInt(m.group(4)), Integer.parseInt(m.group(5)));
            }
        }

        return item;
    }
}

