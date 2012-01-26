/*
 * Copyright (C) 2012 The Android Open Source Project
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

/**
 * A {@link IBuildInfo} that represents a kernel build.
 */
public class KernelBuildInfo extends BuildInfo implements IKernelBuildInfo {

    private String mShortSha1 = null;
    private File mKernelFile = null;
    private long mCommitTime = 0;

    /**
     * Creates a {@link KernelBuildInfo}.
     */
    public KernelBuildInfo() {
        super();
    }

    /**
     * Creates a {@link KernelBuildInfo}.
     *
     * @param sha1 the git sha1, used as the build id
     * @param shortSha1 the git short sha1
     * @param commitTime the git commit time
     * @param testTarget the test target
     * @param buildName the build name
     */
    public KernelBuildInfo(String sha1, String shortSha1, long commitTime, String testTarget,
            String buildName) {
        super(sha1, testTarget, buildName);
        mShortSha1 = shortSha1;
        mCommitTime = commitTime;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public File getKernelFile() {
        return mKernelFile;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setKernelFile(File kernelFile) {
        mKernelFile = kernelFile;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getCommitTime() {
        return mCommitTime;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setCommitTime(long time) {
        mCommitTime = time;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getShortSha1() {
        return mShortSha1;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setShortSha1(String shortSha1) {
        mShortSha1 = shortSha1;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void cleanUp() {
        super.cleanUp();
        FileUtil.deleteFile(mKernelFile);
        mKernelFile = null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IKernelBuildInfo clone() {
        try {
            KernelBuildInfo copy = new KernelBuildInfo(getBuildId(), getShortSha1(),
                    getCommitTime(),getTestTag(), getBuildTargetName());
            copy.addAllBuildAttributes(this);

            if (mKernelFile != null) {
                // Only using createTempFile to create a unique dest filename
                File kernelFileCopy = FileUtil.createTempFile(
                        FileUtil.getBaseName(mKernelFile.getName()),
                        FileUtil.getExtension(mKernelFile.getName()));
                kernelFileCopy.delete();
                FileUtil.hardlinkFile(mKernelFile, kernelFileCopy);
                copy.setKernelFile(kernelFileCopy);
            }

            return copy;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
