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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An {@link ISyslogParser} to handle ANRs.  Since ANRs are all reported by the ActivityManager,
 * they are guaranteed to be fully serialized and thus to not be interleaved.  Also for the same
 * reason, the pid and tid fields are useless.
 */
public class AnrParser implements ISyslogParser {
    public static final String SECTION_NAME = "ANR";

    /**
     * Matches: ANR (application not responding) in process: app
     * Matches: ANR in app
     * Matches: ANR in app (class/package)
     */
    private static final Pattern START = Pattern.compile(
            "ANR (?:\\(application not responding\\) )?in (?:process: )?(\\S+).*");
    private static final Pattern END = Pattern.compile(".*TOTAL: .*?\\d+% user + \\d+% kernel.*");
    // FIXME: unit test: /TOTAL: .*/ vs. /TOTAL: .*?/

    private GenericMapItem<String, String> mItem = null;
    private int mPID = -1;
    private int mTID = -1;

    /**
     * We store the stack messages separately so that we avoid creating a new stack String object
     * for each new line.  In this case, we just keep a single StringBuilder for the entire thing.
     */
    private StringBuilder mStack = new StringBuilder();

    /**
     * {@inheritDoc}
     */
    @Override
    public void parseLine(int tid, int pid, String line, ItemList itemlist) {
        Matcher m = START.matcher(line);
        if (m.matches()) {
            // start of new ANR.  Out with the old, in with the new
            CLog.v("Matched ANR start: %s", line);
            commit(itemlist);
            mItem = new GenericMapItem<String, String>(SECTION_NAME);
            mItem.put("app", m.group(1));
            mPID = pid;
            mTID = tid;
        } else if (mItem == null) {
            //CLog.w("Ignoring unexpected line from pid %d, tid %d: %s", pid, tid, line);
            return;
        } else if (pid != mPID || tid != mTID) {
            CLog.w("Expected pid %d, tid %d, but got line from pid %d, tid %d; committing...",
                mPID, mTID, pid, tid);
            commit(itemlist);
            return;
        }

        mStack.append(line);
        mStack.append("\n");

        // FIXME: is there a way to guarantee that an ANR is completed?
        m = END.matcher(line);
        if (m.matches()) {
            CLog.v("Matched ANR end: %s", line);
            commit(itemlist);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void commit(ItemList itemlist) {
        if (mItem != null) {
            mItem.put("stack", mStack.toString());
            itemlist.addItem(mItem);

            mItem = null;
            mStack = new StringBuilder();
            mPID = -1;
            mTID = -1;
        }
    }
}

