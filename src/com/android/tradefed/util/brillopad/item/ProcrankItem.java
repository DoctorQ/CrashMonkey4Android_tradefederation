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
package com.android.tradefed.util.brillopad.item;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;


/**
 * An {@link IItem} used to procrank info.
 */
public class ProcrankItem implements IItem {
    public static final String TYPE = "PROCRANK";

    private class ProcrankValue {
        public String mProcessName = null;
        public int mVss;
        public int mRss;
        public int mPss;
        public int mUss;

        public ProcrankValue(String processName, int vss, int rss, int pss, int uss) {
            mProcessName = processName;
            mVss = vss;
            mRss = rss;
            mPss = pss;
            mUss = uss;
        }
    }

    private Map<Integer, ProcrankValue> mProcrankLines = new HashMap<Integer, ProcrankValue>();

    /**
     * Add a line from the procrank output to the {@link ProcrankItem}.
     *
     * @param pid The PID from the output
     * @param processName The process name from the cmdline column
     * @param vss The VSS in KB
     * @param rss The RSS in KB
     * @param pss The PSS in KB
     * @param uss The USS in KB
     */
    public void addProcrankLine(int pid, String processName, int vss, int rss, int pss, int uss) {
        mProcrankLines.put(pid, new ProcrankValue(processName, vss, rss, pss, uss));
    }

    /**
     * Get a set of PIDs seen in the procrank output.
     */
    public Set<Integer> getPids() {
        return mProcrankLines.keySet();
    }

    /**
     * Get the process name for a given PID.
     */
    public String getProcessName(int pid) {
        if (!mProcrankLines.containsKey(pid)) {
            return null;
        }

        return mProcrankLines.get(pid).mProcessName;
    }

    /**
     * Get the VSS for a given PID.
     */
    public Integer getVss(int pid) {
        if (!mProcrankLines.containsKey(pid)) {
            return null;
        }

        return mProcrankLines.get(pid).mVss;
    }

    /**
     * Get the RSS for a given PID.
     */
    public Integer getRss(int pid) {
        if (!mProcrankLines.containsKey(pid)) {
            return null;
        }

        return mProcrankLines.get(pid).mRss;
    }

    /**
     * Get the PSS for a given PID.
     */
    public Integer getPss(int pid) {
        if (!mProcrankLines.containsKey(pid)) {
            return null;
        }

        return mProcrankLines.get(pid).mPss;
    }

    /**
     * Get the USS for a given PID.
     */
    public Integer getUss(int pid) {
        if (!mProcrankLines.containsKey(pid)) {
            return null;
        }

        return mProcrankLines.get(pid).mUss;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getType() {
        return TYPE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IItem merge(IItem other) throws ConflictingItemException {
        throw new ConflictingItemException("Procrank items cannot be merged");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isConsistent(IItem other) {
        return false;
    }
}
