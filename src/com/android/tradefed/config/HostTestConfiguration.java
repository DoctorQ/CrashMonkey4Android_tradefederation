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
package com.android.tradefed.config;

import com.android.tradefed.device.WaitDeviceRecovery;
import com.android.tradefed.log.StdoutLogger;
import com.android.tradefed.result.TextResultReporter;
import com.android.tradefed.targetsetup.StubBuildProvider;
import com.android.tradefed.targetsetup.StubTargetPreparer;
import com.android.tradefed.testtype.HostTest;

/**
 * A {@link IConfiguration} for simply running a host based test.
 * <p/>
 * Intended to be used from command line. Uses a stdout logger and result reporter.
 */
class HostTestConfiguration extends AbstractConfiguration {

    /**
     * Creates a {@link HostTestConfiguration}, and all its associated delegate objects.
     *
     * @throws ConfigurationException
     */
    HostTestConfiguration() throws ConfigurationException {
        super();
        addObject(BUILD_PROVIDER_NAME, new StubBuildProvider());
        addObject(DEVICE_RECOVERY_NAME, new WaitDeviceRecovery());
        addObject(TARGET_PREPARER_NAME, new StubTargetPreparer());
        addObject(TEST_NAME, new HostTest());
        addObject(LOGGER_NAME, new StdoutLogger());
        addObject(RESULT_REPORTER_NAME, new TextResultReporter());
    }
}
