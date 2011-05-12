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

import com.android.tradefed.util.MultiMap;
import com.android.tradefed.util.UniqueMultiMap;

import java.util.Map;

/**
 * Generic implementation of a {@link IBuildInfo}.
 */
public class BuildInfo implements IBuildInfo {

    private String mBuildInfo = "0";
    private String mTestTag = "stub";
    private String mBuildTargetName = "stub";
    private UniqueMultiMap<String, String> mBuildAttributes = new UniqueMultiMap<String, String>();
    private String mBuildFlavor = null;
    private String mBuildBranch = null;

    /**
     * Creates a {@link BuildInfo} using default attribute values.
     */
    public BuildInfo() {
    }

    /**
     * Creates a {@link BuildInfo}
     *
     * @param buildId the build id
     * @param testTag the test tag name
     * @param buildTargetName the build target name
     */
    public BuildInfo(String buildId, String testTag, String buildTargetName) {
        mBuildInfo = buildId;
        mTestTag = testTag;
        mBuildTargetName = buildTargetName;
    }

    /**
     * Creates a {@link BuildInfo}, populated with attributes given in another build.
     *
     * @param buildToCopy
     */
    BuildInfo(BuildInfo buildToCopy) {
        this(buildToCopy.getBuildId(), buildToCopy.getTestTag(), buildToCopy.getBuildTargetName());
        addAllBuildAttributes(buildToCopy);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getBuildId() {
        return mBuildInfo;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getTestTag() {
        return mTestTag;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, String> getBuildAttributes() {
        return mBuildAttributes.getUniqueMap();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getBuildTargetName() {
        return mBuildTargetName;
    }

    /**
     * {@inheritDoc}
     */
    public void addBuildAttribute(String attributeName, String attributeValue) {
        mBuildAttributes.put(attributeName, attributeValue);
    }

    /**
     * Helper method to copy build attributes, branch, and flavor from other build.
     */
    protected void addAllBuildAttributes(BuildInfo build) {
        mBuildAttributes.putAll(build.getAttributesMultiMap());
        setBuildFlavor(build.getBuildFlavor());
        setBuildBranch(build.getBuildBranch());
    }

    protected MultiMap<String, String> getAttributesMultiMap() {
        return mBuildAttributes;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void cleanUp() {
        // ignore
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IBuildInfo clone() {
        BuildInfo copy = new BuildInfo(mBuildInfo, mTestTag, mBuildTargetName);
        copy.addAllBuildAttributes(this);
        return copy;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getBuildFlavor() {
        return mBuildFlavor ;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setBuildFlavor(String buildFlavor) {
        mBuildFlavor = buildFlavor;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getBuildBranch() {
        return mBuildBranch;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setBuildBranch(String branch) {
        mBuildBranch = branch;
    }
}
