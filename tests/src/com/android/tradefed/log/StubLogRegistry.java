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

/**
 * A no-op implementation of a {@link LogRegistry}
 */
public class StubLogRegistry extends LogRegistry {

    @Override
    public void registerLogger(ILeveledLogOutput log) {
        // ignore
    }

    @Override
    public void unregisterLogger() {
        // ignore
    }

    @Override
    public void printLog(LogLevel logLevel, String tag, String message) {
        Log.printLog(logLevel, tag, message);
    }

    @Override
    public void closeLog() {
        // ignore
    }
}
