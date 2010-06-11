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

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;

/**
 * Prepares the test environment for the test run.
 * <p/>
 * For example, installs software, tweaks env settings for testing, launches targets etc
 * <p/>
 * TODO: this class could use a better name
 */
public interface ITargetPreparer {

    /**
     * Perform the target setup for testing.
     *
     * @param device the {@link ITestDevice} to prepare
     * @param buildInfo data about the build under test.
     * @throws TargetSetupError if fatal error occurred setting up environment
     * @throws DeviceNotAvailableException if device became unresponsive
     */
    public void setUp(ITestDevice device, IBuildInfo buildInfo) throws TargetSetupError,
            DeviceNotAvailableException;
}
