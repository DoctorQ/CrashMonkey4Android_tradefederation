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

    private int mBuildInfo = 0;
    private String mTestTarget = "stub";
    private String mBuildName = "stub";
    private UniqueMultiMap<String, String> mBuildAttributes = new UniqueMultiMap<String, String>();

    /**
     * Creates a {@link BuildInfo} using default attribute values.
     */
    public BuildInfo() {
    }

    /**
     * Creates a {@link BuildInfo}
     *
     * @param buildId the build id
     * @param testTarget the test target name
     * @param buildName the build name
     */
    public BuildInfo(int buildId, String testTarget, String buildName) {
        mBuildInfo = buildId;
        mTestTarget = testTarget;
        mBuildName = buildName;
    }

    /**
     * {@inheritDoc}
     */
    public int getBuildId() {
        return mBuildInfo;
    }

    public String getTestTarget() {
        return mTestTarget;
    }

    /**
     * {@inheritDoc}
     */
    public Map<String, String> getBuildAttributes() {
        return mBuildAttributes.getUniqueMap();
    }

    /**
     * {@inheritDoc}
     */
    public String getBuildName() {
        return mBuildName;
    }

    /**
     * {@inheritDoc}
     */
    public void addBuildAttribute(String attributeName, String attributeValue) {
        mBuildAttributes.put(attributeName, attributeValue);
    }

    protected void addAllBuildAttributes(MultiMap<String, String> attributes) {
        mBuildAttributes.putAll(attributes);
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

    @Override
    public IBuildInfo clone() {
        BuildInfo copy = new BuildInfo(mBuildInfo, mTestTarget, mBuildName);
        copy.addAllBuildAttributes(mBuildAttributes);
        return copy;
    }
}
