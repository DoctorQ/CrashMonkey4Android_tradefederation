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

import com.android.ddmlib.Log;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;

import java.util.HashMap;
import java.util.Map;

/**
 * No-op empty implementation of a {@link IBuildProvider}.
 * <p/>
 * Will provide an empty {@link BuildInfo} with the provided values from options.
 */
@OptionClass(alias="stub")
public class StubBuildProvider implements IBuildProvider {

    @Option(name="build-id", description="build id to supply.")
    private int mBuildId = 0;

    @Option(name="test-target", description="test target name to supply.")
    private String mTestTarget = "stub";

    @Option(name="build-name", description="build name to supply.")
    private String mBuildName = "stub";

    @Option(name="build-attribute", description="build attributes to supply.")
    private Map<String, String> mBuildAttributes = new HashMap<String,String>();

    /**
     * {@inheritDoc}
     */
    public IBuildInfo getBuild() throws BuildRetrievalError {
        Log.d("BuildProvider", "skipping build provider step");
        BuildInfo stubBuild = new BuildInfo(mBuildId, mTestTarget, mBuildName);
        for (Map.Entry<String, String> attributeEntry : mBuildAttributes.entrySet()) {
            stubBuild.addBuildAttribute(attributeEntry.getKey(), attributeEntry.getValue());
        }
        return stubBuild;
    }

    /**
     * {@inheritDoc}
     */
    public void buildNotTested(IBuildInfo info) {
        // ignore
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void cleanUp(IBuildInfo info) {
        // ignore
    }
}
