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
import com.android.tradefed.util.brillopad.ItemList;
import com.android.tradefed.util.brillopad.ILineParser;

import java.util.List;

/**
 * A shim class that implements IBlockParser by calling ILineParser methods
 */
public abstract class AbstractBlockParser implements IBlockParser, ILineParser {

    /**
     * {@inheritDoc}
     */
    @Override
    public void parseBlock(List<String> input, ItemList itemlist) {
        for (String line : input) {
            if (line == null) {
                CLog.w("Encountered unexpected null line; skipping...");
                continue;
            }
            parseLine(line, itemlist);
        }

        // signal EOF
        commit(itemlist);
    }

    /**
     * Parses a single {@link String}
     *
     * @param line
     */
    abstract public void parseLine(String line, ItemList itemlist);
}
