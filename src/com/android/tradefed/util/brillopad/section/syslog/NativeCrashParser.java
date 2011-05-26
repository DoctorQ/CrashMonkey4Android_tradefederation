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
 * An {@link ISyslogParser} to handle Native crashes.  We parse line-by-line since messages from
 * different crashes may be interleaved -- the only guarantee we have is that a single line of
 * logcat does not contain portions of some other line.
 */
public class NativeCrashParser implements ISyslogParser {
    public static final String SECTION_NAME = "NATIVE CRASH";

    /** Matches: *** *** *** *** *** *** *** *** *** *** *** *** *** *** *** *** */
    private static final Pattern START = Pattern.compile("^(?:\\*\\*\\* ){15}\\*\\*\\*$");
    /** Matches: Build fingerprint: 'fingerprint' */
    private static final Pattern FINGERPRINT = Pattern.compile("^Build fingerprint: '(.*)'$");
    /** Matches: pid: 957, tid: 963  >>> com.android.camera <<< */
    private static final Pattern APP = Pattern.compile("^pid: \\d+, tid: \\d+  >>> (\\S+) <<<$");

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

        if (!mKeys.contains(key)) {
            // potentially new entry.  Ignore unless we see a start line.
            Matcher m = START.matcher(line);
            if (m.matches()) {
                mKeys.add(key);
                mMaps.put(key, new GenericMapItem<String, String>(SECTION_NAME));
                mStacks.put(key, new StringBuilder());
            } else {
                CLog.w("Ignoring unexpected line from pid %d, tid %d: %s", pid, tid, line);
                return;
            }
        }

        // by virtue of getting this far, the entries in mMaps and mStacks should exist
        GenericMapItem<String, String> item = mMaps.get(key);
        Matcher m = FINGERPRINT.matcher(line);
        if (m.matches()) {
            item.put("fingerprint", m.group(1));
        }
        m = APP.matcher(line);
        if (m.matches()) {
            item.put("app", m.group(1));
        }

        StringBuilder stack = mStacks.get(key);
        stack.append(line);
        stack.append("\n");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void commit(ItemList itemlist) {
        for (int key : mKeys) {
            GenericMapItem<String, String> item = new GenericMapItem<String, String>(SECTION_NAME);
            item.put("stack", mStacks.get(key).toString());
            itemlist.addItem(item);
        }
    }
}

