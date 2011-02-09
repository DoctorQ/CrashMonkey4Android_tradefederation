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
package com.android.tradefed.testtype;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.ResultForwarder;

import java.util.List;

/**
 * A simpler abstract implementation of a {@link IRemoteTest}, which loops through
 * ITestInvocationListeners on behalf of the subclass.
 * <p />
 * If the subclass doesn't care about the difference between one {@link ITestInvocationListener} and
 * the next, it will be functionally equivalent (and much simpler) to implement this class instead
 * of {@link IRemoteTest}.
 */
public abstract class SimpleAbstractRemoteTest implements IRemoteTest {
    /**
     * {@inheritDoc}
     */
    @Override
    public void run(List<ITestInvocationListener> listeners) throws DeviceNotAvailableException {
        ITestInvocationListener multiplexListener = new ResultForwarder(listeners);
        run(multiplexListener);
    }

    /**
     * A convenience method which subclasses should implement to take advantage of the
     * {@link ITestInvocationListener} multiplexing that this class implements.  See
     * {@see #run(List<ITestInvocationListener>)} for more details.
     */
    abstract public void run(ITestInvocationListener multiplexListener)
            throws DeviceNotAvailableException;
}

