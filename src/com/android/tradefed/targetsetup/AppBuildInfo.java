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

package com.android.tradefed.targetsetup;

import com.android.tradefed.targetsetup.BuildInfo;
import com.android.tradefed.targetsetup.IBuildInfo;

import java.io.File;

/**
 * A {@link IBuildInfo} that represents an Android application and its test package.
 */
public class AppBuildInfo extends BuildInfo {

    private File mAppPackageFile = null;
    private File mTestPackageFile = null;

    /**
     * Creates a {@link AppBuildInfo}.
     *
     * @param buildId the unique build id
     * @param testTarget the test target name
     * @param buildName the build name
     */
    public AppBuildInfo(int buildId, String testTarget, String buildName) {
        super(buildId, testTarget, buildName);
    }

    /**
     * Get the local app apk file.
     */
    public File getAppPackageFile() {
        return mAppPackageFile;
    }

    /**
     * Set the local app apk file.
     */
    public void setAppPackageFile(File appPackageFile) {
        mAppPackageFile = appPackageFile;
    }

    /**
     * Get the test package apk file.
     */
    public File getTestPackageFile() {
        return mTestPackageFile;
    }

    /**
     * Set the local tests apk file.
     */
    public void setTestPackageFile(File testPackageFile) {
        mTestPackageFile = testPackageFile;
    }

    /**
     * Removes all temporary files
     */
    public void cleanUp() {
        if (mAppPackageFile != null) {
            mAppPackageFile.delete();
            mAppPackageFile = null;
        }
        if (mTestPackageFile != null) {
            mTestPackageFile.delete();
            mTestPackageFile = null;
        }
    }

}
