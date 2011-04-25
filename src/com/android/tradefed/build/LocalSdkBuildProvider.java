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
package com.android.tradefed.build;

import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.config.Option.Importance;

import java.io.File;

/**
 * A {@link IBuildProvider} that constructs a {@link ISdkBuildInfo} based on a provided local path
 */
@OptionClass(alias = "local-sdk")
public class LocalSdkBuildProvider implements IBuildProvider {

    @Option(name = "sdk-build-path", description =
            "the local filesystem path to a sdk build to test.", importance = Importance.IF_UNSET)
    private File mLocalSdkPath = null;

    @Option(name = "adt-build-path", description =
            "the local filesystem path to a adt build to test.", importance = Importance.IF_UNSET)
    private File mLocalAdtPath = null;

    /**
     * {@inheritDoc}
     */
    @Override
    public IBuildInfo getBuild() throws BuildRetrievalError {
        ISdkBuildInfo sdkBuild = new SdkBuildInfo(0, "local-sdk", "sdk");
        if (mLocalSdkPath == null) {
            throw new IllegalArgumentException("missing --sdk-build-path option");
        }
        // allow a null adt-build-path
        sdkBuild.setSdkDir(mLocalSdkPath);
        sdkBuild.setAdtDir(mLocalAdtPath);
        return sdkBuild;
    }

    /**
     * {@inheritDoc}
     */
    @Override
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
