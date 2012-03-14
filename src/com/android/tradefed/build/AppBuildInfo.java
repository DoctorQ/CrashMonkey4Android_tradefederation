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

package com.android.tradefed.build;

import com.android.tradefed.util.FileUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * A {@link IBuildInfo} that represents an Android application and its test package(s).
 */
public class AppBuildInfo extends BuildInfo implements IAppBuildInfo {

    private List<File> mAppPackageFiles = new ArrayList<File>();

    /**
     * Creates a {@link AppBuildInfo}.
     *
     * @param buildId the unique build id
     * @param testTarget the test target name
     * @param buildName the build name
     */
    public AppBuildInfo(String buildId, String testTarget, String buildName) {
        super(buildId, testTarget, buildName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<File> getAppPackageFiles() {
        List<File> listCopy = new ArrayList<File>(mAppPackageFiles.size());
        listCopy.addAll(mAppPackageFiles);
        return listCopy;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addAppPackageFile(File appPackageFile) {
        mAppPackageFiles.add(appPackageFile);
    }

    /**
     * Removes all temporary files
     */
    @Override
    public void cleanUp() {
        for (File appPackageFile : mAppPackageFiles) {
            appPackageFile.delete();
        }
        mAppPackageFiles.clear();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IBuildInfo clone() {
        AppBuildInfo copy = new AppBuildInfo(getBuildId(), getTestTag(), getBuildTargetName());
        copy.addAllBuildAttributes(this);
        try {
            for (File origFile : mAppPackageFiles) {
                // Only using createTempFile to create a unique dest filename
                File copyFile = FileUtil.createTempFile(origFile.getName(),
                        FileUtil.getExtension(origFile.getName()));
                copyFile.delete();
                FileUtil.hardlinkFile(origFile, copyFile);
                copy.addAppPackageFile(copyFile);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        copy.setBuildBranch(getBuildBranch());
        copy.setBuildFlavor(getBuildFlavor());

        return copy;
    }

}
