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

import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.Option.Importance;
import com.android.tradefed.config.OptionCopier;
import com.android.tradefed.log.LogUtil.CLog;

/**
 * Implementation of {@link ICommandOptions}.
 */
public class CommandOptions implements ICommandOptions {

    @Option(name = "help", description =
        "display the help text for the most important/critical options.",
        importance = Importance.ALWAYS)
    private boolean mHelpMode = false;

    @Option(name = "help-all", description = "display the full help text for all options.",
            importance = Importance.ALWAYS)
    private boolean mFullHelpMode = false;

    @Option(name = "dry-run",
            description = "build but don't actually run the command.  Intended as a quick check " +
                    "to ensure that a command is runnable.",
            importance = Importance.ALWAYS)
    private boolean mDryRunMode = false;

    @Option(name = "noisy-dry-run",
            description = "build but don't actually run the command.  This version prints the " +
                    "command to the console.  Intended for cmdfile debugging.",
            importance = Importance.ALWAYS)
    private boolean mNoisyDryRunMode = false;

    @Option(name = "min-loop-time", description =
            "the minimum invocation time in ms when in loop mode.")
    private long mMinLoopTime = 10 * 60 * 1000;

    @Option(name = "loop", description = "keep running continuously.")
    private boolean mLoopMode = true;

    @Option(name = "all-devices", description =
            "fork this command to run on all connected devices.")
    private boolean mAllDevices = true;

    @Option(name = "need-prepare", description = "is needed to prepare device")
    private boolean mNeedPrepare = true;
    
//    @Option(name = "need-flash", description = "is needed to fastboot device")
//    private boolean mNeedFlash = true;
    
    @Option(name = "need-tearDown", description = "is needed to clean device")
    private boolean mNeedTearDown = true;
    
    
    public boolean isNeedPrepare() {
		return mNeedPrepare;
	}

	public void setNeedPrepare(boolean needPrepare) {
		mNeedPrepare = needPrepare;
	}

	public boolean isNeedTearDown() {
		return mNeedTearDown;
	}

	public void setNeedTearDown(boolean needTearDown) {
		mNeedTearDown = needTearDown;
	}

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
    @Override
    public boolean isHelpMode() {
        return mHelpMode;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isFullHelpMode() {
        return mFullHelpMode;
    }

    /**
     * Set the dry run mode for the config.
     * <p/>
     * Exposed for testing.
     */
    void setDryRunMode(boolean dryRunMode) {
        mDryRunMode = dryRunMode;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isDryRunMode() {
        return mDryRunMode || mNoisyDryRunMode;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isNoisyDryRunMode() {
        return mNoisyDryRunMode;
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
    @Override
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
    @Override
    public long getMinLoopTime() {
        return mMinLoopTime;
    }

    @Override
    public ICommandOptions clone() {
        CommandOptions clone = new CommandOptions();
        try {
            OptionCopier.copyOptions(this, clone);
        } catch (ConfigurationException e) {
            CLog.e("failed to clone command options", e);
        }
        return clone;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean runOnAllDevices() {
        return mAllDevices;
    }
}
