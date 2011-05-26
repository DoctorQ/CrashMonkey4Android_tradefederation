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

import com.android.tradefed.util.brillopad.ItemList;

import java.util.List;

/**
 * Attempt to parse a line of the logcat
 */
public interface ISyslogParser {
    // FIXME: ILogcatLineParser is a better name
    /**
     * Parse of line of logcat
     */
    public void parseLine(int tid, int pid, String msg, ItemList itemlist);

    /**
     * A signal that input is done for this parser
     */
    public void commit(ItemList itemlist);
}

