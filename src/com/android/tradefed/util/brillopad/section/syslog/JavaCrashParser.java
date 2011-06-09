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
package com.android.tradefed.util.brillopad.section.syslog;

import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.brillopad.ItemList;
import com.android.tradefed.util.brillopad.item.GenericMapItem;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An {@link ISyslogParser} to handle Java crashes.  We parse line-by-line since messages from
 * different crashes may be interleaved -- the only guarantee we have is that a single line of
 * logcat does not contain portions of some other line.
 */
public class JavaCrashParser implements ISyslogParser {
    public static final String SECTION_NAME = "JAVA CRASH";

    /**
     * Matches: java.lang.Exception
     * Matches: java.lang.Exception: reason
     */
    private static final Pattern EXCEPTION = Pattern.compile("^([^\\s:]+)(?:: (.*))$");

    private Set<Integer> mKeys = new LinkedHashSet<Integer>();
    private Map<Integer, GenericMapItem<String, String>> mMaps =
            new HashMap<Integer, GenericMapItem<String, String>>();
    /**
     * We store the stack messages separately so that we avoid creating a new stack String object
     * for each new line.  In this case, we just keep a single StringBuilder for the entire thing.
     */
    private Map<Integer, StringBuilder> mStacks = new HashMap<Integer, StringBuilder>();

    /**
     * Hash the PID and TID to create an identifier that "should" be unique for a given logcat.
     * In practice, we do use it as a unique identifier.
     */
    private static int encodePidTid(Integer pid, Integer tid) {
        // Assume an unreachable max pid of 65536 == 16 bits
        return (pid << 16) | tid;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void parseLine(int tid, int pid, String line, ItemList itemlist) {
        int key = encodePidTid(pid, tid);
        //CLog.w("Got tid %d, pid %d, encoded key %d, line %s", tid, pid, key, line);

        mKeys.add(key);

        Matcher m = EXCEPTION.matcher(line);
        if (m.matches()) {
            GenericMapItem<String, String> item = new GenericMapItem<String, String>(SECTION_NAME);
            item.put("exception", m.group(1));
            String reason = m.group(2);
            if (reason != null) {
                item.put("reason", m.group(2));
            }
            mMaps.put(key, item);
        }

        StringBuilder stack;
        if (mStacks.containsKey(key)) {
            stack = mStacks.get(key);
        } else {
            stack = new StringBuilder();
            mStacks.put(key, stack);
        }
        stack.append(line);
        stack.append("\n");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void commit(ItemList itemlist) {
        for (int key : mKeys) {
            GenericMapItem<String, String> item = mMaps.get(key);
            if (item == null) {
                item = new GenericMapItem<String, String>(SECTION_NAME);
            }

            if (mStacks.containsKey(key)) {
                item.put("stack", mStacks.get(key).toString());
            }
            itemlist.addItem(item);
        }
    }
}

