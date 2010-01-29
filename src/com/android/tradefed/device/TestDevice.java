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

package com.android.tradefed.device;

import com.android.ddmlib.IDevice;
import com.android.ddmlib.IShellOutputReceiver;
import com.android.ddmlib.MultiLineReceiver;

import java.io.IOException;

/**
 * Default implementation of a {@link ITestDevice}
 */
class TestDevice implements ITestDevice {

    private IDevice mIDevice;

    /**
     * Creates a {@link TestDevice}.
     *
     * @param device the associated {@link IDevice}
     */
    TestDevice(IDevice device) {
        mIDevice = device;
    }

    /**
     * {@inheritDoc}
     */
    public void executeShellCommand(String command, IShellOutputReceiver receiver)
            throws DeviceNotAvailableException {
        try {
            mIDevice.executeShellCommand(command, receiver);
        } catch (IOException e) {
            // TODO: add retry mechanism
            throw new DeviceNotAvailableException(String.format("failed to execute %s", command),
                    e);
        }
    }

    /**
     * {@inheritDoc}
     */
    public String executeShellCommand(String command) throws DeviceNotAvailableException {
        CollectingOutputReceiver receiver = new CollectingOutputReceiver();
        executeShellCommand(command, receiver);
        return receiver.getOutput();
    }

    /**
     * {@inheritDoc}
     */
    public IDevice getIDevice() {
        return mIDevice;
    }

    /**
     * A {@link IShellOutputReceiver} which collects the whole shell output into one
     * {@link String}.
     */
    private static class CollectingOutputReceiver extends MultiLineReceiver {

        private StringBuffer mOutputBuffer = new StringBuffer();

        public String getOutput() {
            return mOutputBuffer.toString();
        }

        @Override
        public void processNewLines(String[] lines) {
            for (String line : lines) {
                mOutputBuffer.append(line);
                mOutputBuffer.append("\r\n");
            }
        }

        public boolean isCancelled() {
            return false;
        }
    }
}
