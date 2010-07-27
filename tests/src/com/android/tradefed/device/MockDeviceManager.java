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

import org.easymock.EasyMock;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * A {@link IDeviceManager} that simulates the resource allocation of {@link DeviceManager}
 * for a configurable set of devices.
 */
public class MockDeviceManager implements IDeviceManager {

    LinkedBlockingQueue<ITestDevice> mDeviceQueue = new LinkedBlockingQueue<ITestDevice>();

    public MockDeviceManager(int numDevices) {
        setNumDevices(numDevices);
    }

    public void setNumDevices(int numDevices) {
        mDeviceQueue.clear();
        for (int i=0; i < numDevices; i++) {
            ITestDevice mockDevice = EasyMock.createNiceMock(ITestDevice.class);
            EasyMock.expect(mockDevice.getSerialNumber()).andStubReturn("serial" + i);
            EasyMock.replay(mockDevice);
            mDeviceQueue.add(mockDevice);
        }
    }

    /**
     * {@inheritDoc}
     */
    public void addFastbootListener(IFastbootListener listener) {
        // ignore
    }

    /**
     * {@inheritDoc}
     */
    public ITestDevice allocateDevice() {
        try {
            return mDeviceQueue.take();
        } catch (InterruptedException e) {
            return null;
        }
    }

    /**
     * {@inheritDoc}
     */
    public ITestDevice allocateDevice(long timeout) {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     */
    public void freeDevice(ITestDevice device, FreeDeviceState state) {
        mDeviceQueue.add(device);
    }

    /**
     * {@inheritDoc}
     */
    public void removeFastbootListener(IFastbootListener listener) {
        // ignore
    }

    /**
     * {@inheritDoc}
     */
    public void terminate() {
        // ignore
    }

    /**
     * {@inheritDoc}
     */
    public Collection<String> getAllocatedDevices() {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     */
    public Collection<String> getAvailableDevices() {
        Collection<String> deviceSerials = new ArrayList<String>(mDeviceQueue.size());
        for (ITestDevice dev : mDeviceQueue) {
            deviceSerials.add(dev.getSerialNumber());
        }
        return deviceSerials;
    }

    /**
     * {@inheritDoc}
     */
    public Collection<String> getUnavailableDevices() {
        throw new UnsupportedOperationException();
    }

}
