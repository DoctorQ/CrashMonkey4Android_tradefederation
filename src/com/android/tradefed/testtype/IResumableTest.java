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
package com.android.tradefed.testtype;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.result.ITestInvocationListener;

import java.util.List;

/**
 * An {@link IRemoteTest} that supports resuming a previous aborted test run from where it left off.
 */
public interface IResumableTest extends IRemoteTest {

    /**
     * Resume the previous test run from where it left off.
     *
     * @param listeners the list of {@link ITestInvocationListener} of test results
     * @throws DeviceNotAvailableException
     */
    public void resume(List<ITestInvocationListener> listeners) throws DeviceNotAvailableException;

    /**
     * Resume the previous test run from where it left off.
     *
     * @param listener the {@link ITestInvocationListener} of test results
     * @throws DeviceNotAvailableException
     */
    public void resume(ITestInvocationListener listener) throws DeviceNotAvailableException;

}
