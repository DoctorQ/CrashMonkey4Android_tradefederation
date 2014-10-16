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

package com.android.tradefed.targetprep;

import com.android.ddmlib.IDevice;
import com.android.ddmlib.Log;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;

import java.io.File;
import java.util.Collection;
import java.util.LinkedList;

/**
 * A {@link ITargetPreparer} that attempts to push any number of files from any host path to any
 * device path.
 * <p />
 * Should be performed *after* a new build is flashed, and *after* DeviceSetup is run (if enabled)
 */
@OptionClass(alias = "push-file")
public class PushFilePreparer implements ITargetPreparer {
    private static final String LOG_TAG = "PushFilePreparer";

    @Option(name="push", description=
            "A push-spec, formatted as '/path/to/srcfile.txt->/path/to/destfile.txt' or " +
            "'/path/to/srcfile.txt->/path/to/destdir/'. May be repeated.")
    private Collection<String> mPushSpecs = new LinkedList<String>();

    @Option(name="post-push", description=
            "A command to run on the device (with `adb shell (yourcommand)`) after all pushes " +
            "have been attempted.  Will not be run if a push fails with abort-on-push-failure " +
            "enabled.  May be repeated.")
    private Collection<String> mPostPushCommands = new LinkedList<String>();

    @Option(name="abort-on-push-failure", description=
            "If false, continue if pushes fail.  If true, abort the Invocation on any failure.")
    private boolean mAbortOnFailure = true;

    @Option(name="trigger-media-scan", description=
            "After pushing files, trigger a media scan of external storage on device.")
    private boolean mTriggerMediaScan = false;

    /**
     * Set abort on failure.  Exposed for testing.
     */
    void setAbortOnFailure(boolean value) {
        mAbortOnFailure = value;
    }

    /**
     * Set pushspecs.  Exposed for testing.
     */
    void setPushSpecs(Collection<String> pushspecs) {
        mPushSpecs = pushspecs;
    }

    /**
     * Set post-push commands.  Exposed for testing.
     */
    void setPostPushCommands(Collection<String> commands) {
        mPostPushCommands = commands;
    }

    /**
     * Helper method to only throw if mAbortOnFailure is enabled.  Callers should behave as if this
     * method may return.
     */
    private void fail(String message) throws TargetSetupError {
        if (mAbortOnFailure) {
            throw new TargetSetupError(message);
        } else {
            // Log the error and return
            Log.w(LOG_TAG, message);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setUp(ITestDevice device, IBuildInfo buildInfo) throws TargetSetupError, BuildError,
            DeviceNotAvailableException {
        for (String pushspec : mPushSpecs) {
            String[] pair = pushspec.split("->");
            if (pair.length != 2) {
                fail(String.format("Invalid pushspec: '%s'"));
                continue;
            }
            Log.d(LOG_TAG, String.format("Trying to push local '%s' to remote '%s'", pair[0],
                    pair[1]));

            File src = new File(pair[0]);
            if (!src.exists()) {
                src = buildInfo.getFile(pair[0]);
                if (src == null || !src.exists()) {
                    fail(String.format("Local source file '%s' does not exist", pair[0]));
                    continue;
                }
            }
            if (src.isDirectory()) {
                if (!device.pushDir(src, pair[1])) {
                    fail(String.format("Failed to push local '%s' to remote '%s'", pair[0],
                            pair[1]));
                    continue;
                }
            } else {
                if (!device.pushFile(src, pair[1])) {
                    fail(String.format("Failed to push local '%s' to remote '%s'", pair[0],
                            pair[1]));
                    continue;
                }
            }
        }

        for (String command : mPostPushCommands) {
            device.executeShellCommand(command);
        }

        if (mTriggerMediaScan) {
            // send a MEDIA_MOUNTED broadcast
            device.executeShellCommand(String.format(
                    "am broadcast -a android.intent.action.MEDIA_MOUNTED -d file://%s",
                    device.getMountPoint(IDevice.MNT_EXTERNAL_STORAGE)));
        }
    }
}
