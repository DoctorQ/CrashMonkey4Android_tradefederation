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
import com.android.tradefed.util.brillopad.IBlockParser;
import com.android.tradefed.util.brillopad.ILineParser;
import com.android.tradefed.util.brillopad.ItemList;
import com.android.tradefed.util.brillopad.item.GenericMapItem;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A {@link ILineParser} to handle the System Properties section of the bugreport
 */
public class SystemPropParser implements IBlockParser {
    public static final String SECTION_NAME = "SYSTEM PROPERTIES";

    /** Match a single property line, such as "[gsm.sim.operator.numeric]: []" */
    private static final Pattern PROP_LINE = Pattern.compile("^\\[(.*)\\]: \\[(.*)\\]$");

    /**
     * {@inheritDoc}
     */
    @Override
    public void parseBlock(List<String> block, ItemList itemlist) {
        GenericMapItem<String, String> output =
                new GenericMapItem<String, String>(SECTION_NAME);

        for (String line : block) {
            Matcher m = PROP_LINE.matcher(line);
            if (m.matches()) {
                output.put(m.group(1), m.group(2));
            } else {
                CLog.w("Failed to parse line '%s'", line);
            }
        }
        itemlist.addItem(output);
    }
}

