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

import java.util.ArrayList;
import java.util.List;

import junit.framework.TestResult;

/**
 * Abstract implementation of a {@link IRemoteTest}.
 * <p/>
 * Provides implementation for {@link IRemoteTest#run(ITestInvocationListener)}.
 */
public abstract class AbstractRemoteTest implements IRemoteTest {

    /**
     * {@inheritDoc}
     */
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        List<ITestInvocationListener> listeners = new ArrayList<ITestInvocationListener>(1);
        listeners.add(listener);
        run(listeners);
    }

    /**
     * {@inheritDoc}
     */
    public int countTestCases() {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     */
    public void run(TestResult result) {
        throw new UnsupportedOperationException();
    }

}
