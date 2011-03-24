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

import java.io.File;

/**
 *  A {@link IBuildInfo} that represents an extracted Android SDK and ADT build.
 */
public interface ISdkBuildInfo extends IBuildInfo {

    /**
     * Returns the directory that contains the extracted SDK build.
     */
    public File getSdkDir();

    /**
     * Returns the directory that contains the extracted ADT build.
     */
    public File getAdtDir();

    /**
     * Sets the directory that contains the extracted ADT build.
     */
    public void setAdtDir(File adtDir);

    /**
     * Sets the directory that contains the extracted SDK build.
     *
     * @param sdkDir the path to the sdk.
     */
    public void setSdkDir(File sdkDir);

    /**
     * Sets the directory that contains the extracted SDK build.
     *
     * @param sdkDir the path to the sdk
     * @param deleteParent if <code>true</code>, delete the parent directory of sdkDir on
     *            {@link #cleanUp()}. If <code>false</code>, only sdkDir will be deleted.
     */
    public void setSdkDir(File sdkDir, boolean deleteParent);

}
