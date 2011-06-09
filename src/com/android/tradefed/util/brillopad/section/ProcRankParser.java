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

import com.android.tradefed.util.brillopad.ItemList;
import com.android.tradefed.util.brillopad.IBlockParser;
import com.android.tradefed.util.brillopad.item.GenericMapItem;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A {@link ILineParser} to handle the Procrank section of the bugreport.  Memory values returned
 * are in units of kiloBytes
 */
public class ProcRankParser implements IBlockParser {
    public static final String SECTION_NAME = "PROCRANK";
    public static final String SECTION_REGEX = "------ PROCRANK .*";

    private int mNumFields = -1;
    private String[] mFieldNames = null;

    /** Match a memory amount, such as "12345K" */
    private static final Pattern NUMBER_PAT = Pattern.compile("(\\d+)([BKMGbkmg])?");

    /**
     * A utility function to parse a memory amount, such as "12345K", and return the number of
     * kiloBytes that the amount represents.
     */
    private static Integer parseMem(String val) {
        Integer count = null;
        Matcher m = NUMBER_PAT.matcher(val);
        if (m.matches()) {
            count = Integer.parseInt(m.group(1));
            String suffix = m.group(2);
            if (suffix == null) {
                return count;
            }
            suffix = suffix.toLowerCase();
            if ("b".equals(suffix)) {
                count /= 1024;
            } else if ("k".equals(suffix)) {
                // nothing to do
            } else if ("m".equals(suffix)) {
                count *= 1024;
            } else if ("g".equals(suffix)) {
                count *= 1024 * 1024;
            }
        }
        return count;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void parseBlock(List<String> block, ItemList itemlist) {
        GenericMapItem<String, Map<String, Integer>> output =
                new GenericMapItem<String, Map<String, Integer>>(SECTION_NAME);

        for (String line : block) {
            // Trim leading whitespace so that split() works properly
            line = line.replaceFirst("^\\s+", "");
            if (mFieldNames == null) {
                // try to parse a header
                mFieldNames = line.split("\\s+");
                mNumFields = mFieldNames.length;
                continue;
            }

            String[] fields = line.split("\\s+", mNumFields);
            String cmdline = fields[fields.length - 1];
            Map<String, Integer> valueMap = new HashMap<String, Integer>();
            for (int i = 0; i < mNumFields - 1 && i < fields.length; ++i) {
                // FIXME: it's not correct to send PID through this, but in practice it works
                valueMap.put(mFieldNames[i], parseMem(fields[i]));
            }
            output.put(cmdline, valueMap);
        }
        itemlist.addItem(output);
    }
}

