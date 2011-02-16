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
package com.android.tradefed.command;

import com.android.tradefed.config.Option;

/**
 * Container for execution options for commands.
 */
public class CommandOptions implements ICommandOptions {

    @Option(name="help", description="display the help text")
    private boolean mHelpMode = false;

    @Option(name="min-loop-time", description=
        "the minimum invocation time in ms when in loop mode. Default is 1 minute.")
    private long mMinLoopTime = 60 * 1000;

    @Option(name="loop", description="keep running continuously")
    private boolean mLoopMode = true;

    /**
     * Set the help mode for the config.
     * <p/>
     * Exposed for testing.
     */
    void setHelpMode(boolean helpMode) {
        mHelpMode = helpMode;
    }

    /**
     * {@inheritDoc}
     */
    public boolean isHelpMode() {
        return mHelpMode;
    }

    /**
     * Set the loop mode for the config.
     */
    @Override
    public void setLoopMode(boolean loopMode) {
        mLoopMode = loopMode;
    }

    /**
     * {@inheritDoc}
     */
    public boolean isLoopMode() {
        return mLoopMode;
    }

    /**
     * Set the min loop time for the config.
     * <p/>
     * Exposed for testing.
     */
    void setMinLoopTime(long loopTime) {
        mMinLoopTime = loopTime;
    }

    /**
     * {@inheritDoc}
     */
    public long getMinLoopTime() {
        return mMinLoopTime;
    }
}

