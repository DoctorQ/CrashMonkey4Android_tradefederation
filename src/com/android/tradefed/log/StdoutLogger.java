/*
 * Copyright (C) 2010 The Android Open Source Project
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
import com.android.tradefed.config.Option;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * A {@link ILeveledLogOutput} that directs log messages to stdout.
 */
public class StdoutLogger implements ILeveledLogOutput {

    @Option(name="log-level", description="minimum log level to display")
    private String mLogLevel = LogLevel.INFO.getStringValue();

    /**
     * {@inheritDoc}
     */
    public void printAndPromptLog(LogLevel logLevel, String tag, String message) {
        printLog(logLevel, tag, message);

    }

    /**
     * {@inheritDoc}
     */
    public void printLog(LogLevel logLevel, String tag, String message) {
        // TODO: for now, jst redirect back to the standard ddms logger
        Log.printLog(logLevel, tag, message);

    }

    /**
     * {@inheritDoc}
     */
    public String getLogLevel() {
        return mLogLevel;
    }

    /**
     * {@inheritDoc}
     */
    public void closeLog() {
        // ignore
    }

    /**
     * {@inheritDoc}
     */
    public InputStream getLog() {
        // not supported - return empty stream
        return new ByteArrayInputStream(new byte[0]);
    }

}
