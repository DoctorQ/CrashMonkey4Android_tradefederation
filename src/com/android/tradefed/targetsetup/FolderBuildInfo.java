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

import com.android.tradefed.util.FileUtil;

import java.io.File;

/**
 * Concrete implementation of a {@link IFolderBuildInfo}.
 */
public class FolderBuildInfo extends BuildInfo implements IFolderBuildInfo {

    private File mRootDir;

    /**
     * @see {@link BuildInfo#BuildInfo(int, String, String)}
     */
    public FolderBuildInfo(int buildId, String testTarget, String buildName) {
        super(buildId, testTarget, buildName);
    }

    /**
     * {@inheritDoc}
     */
    public File getRootDir() {
        return mRootDir;
    }

    /**
     * {@inheritDoc}
     */
    public void setRootDir(File rootDir) {
        mRootDir = rootDir;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void cleanUp() {
        if (mRootDir != null) {
            FileUtil.recursiveDelete(mRootDir);
        }
        mRootDir = null;
    }
}
