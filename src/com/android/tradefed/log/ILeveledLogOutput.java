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

import com.android.ddmlib.Log.ILogOutput;
import com.android.ddmlib.Log.LogLevel;

/**
 * Classes which implement this interface provides methods that deal with outputting log
 * messages.
 */
public interface ILeveledLogOutput extends ILogOutput {

    /**
     * Gets the minimum log level to display.
     *
     * @return the {@link String} form of the {@link LogLevel}
     */
    public String getLogLevel();

    /**
     * Closes the log and performs any cleanup before closing, as necessary.
     *
     */
    public void closeLog();

}
