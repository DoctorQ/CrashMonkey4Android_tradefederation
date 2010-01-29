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
 * Responsible for providing info regarding the build under test.
 */
public interface IBuildProvider {

    /**
     * Retrieve the data for build under test.
     *
     * @return the {@link IBuildInfo} for build under test or <code>null</code> if no build is
     * available for testing
     * @throws TargetSetupError if build info failed to be retrieved due to an unexpected error
     */
    public IBuildInfo getBuild() throws TargetSetupError;
}
