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

package com.android.tradefed.log;

import com.android.ddmlib.Log;
import com.android.ddmlib.Log.LogLevel;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * A logging utility class.  Useful for code that needs to override static methods from {@link Log}
 */
public class LogUtil {

    /** A String description of all the potential {@link LogLevel} values */
    // TODO: unfortunately, this value can not be calculated dynamically since if it was,
    // the String could not be used in {@link Option#annotation} expressions
    static final String LOG_LEVEL_LIST = "verbose, debug, info, warn, error, assert";

    /**
     * Make uninstantiable
     */
    private LogUtil() {}

    /**
     * Sent when a log message needs to be printed.  This implementation prints the message to
     * stdout in all cases.
     *
     * @param logLevel The {@link LogLevel} enum representing the priority of the message.
     * @param tag The tag associated with the message.
     * @param message The message to display.
     */
    public static void printLog(LogLevel logLevel, String tag, String message) {
        System.out.print(LogUtil.getLogFormatString(logLevel, tag, message));
    }

    /**
     * Creates a format string that is similar to the "threadtime" log format on the device.  This
     * is specifically useful because it includes the day and month (to differentiate times for
     * long-running TF instances), and also uses 24-hour time to disambiguate morning from evening.
     * <p/>
     * {@see Log#getLogFormatString()}
     */
    public static String getLogFormatString(LogLevel logLevel, String tag, String message) {
        SimpleDateFormat formatter = new SimpleDateFormat("MM-dd kk:mm:ss");
        return String.format("%s %c/%s: %s\n", formatter.format(new Date()),
                logLevel.getPriorityLetter(), tag, message);
    }
}

