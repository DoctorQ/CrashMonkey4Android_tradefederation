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

/**
 * Holds information about the build under test.
 * <p/>
 * TODO: what other generic interfaces need to be added:
 * Some potential candidates:
 *   - getBuildDescription - user meaningful string describing build
 */
public interface IBuildInfo {

    /**
     * @return the unique identifier of build under test
     */
    public int getBuildId();

    /**
     * Returns the local file path a build file with given alias.
     *
     * @param alias unique name of build file. The set of available alias' will be implementation
     * specific.
     * @return absolute file path of build file
     */
    public String getBuildFilePath(String alias);
}
