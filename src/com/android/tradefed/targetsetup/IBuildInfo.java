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

import java.util.Map;

/**
 * Holds information about the build under test.
 */
public interface IBuildInfo {

    /**
     * @return the unique identifier of build under test
     */
    public int getBuildId();

    /**
     * Return a unique description of the tests being run.
     */
    public String getTestTarget();

    /**
     * Return a unique description of the type of build.
     */
    public String getBuildName();

    /**
     * Get a set of name-value pairs of attributes describing the build.
     *
     * @return a {@link Map} of build attributes. Will not be <code>null</code>, but may be empty.
     */
    public Map<String, String> getBuildAttributes();

}
