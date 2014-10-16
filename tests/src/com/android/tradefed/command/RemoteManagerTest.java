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
package com.android.tradefed.command;

import com.android.tradefed.device.IDeviceManager;
import com.android.tradefed.device.IDeviceManager.FreeDeviceState;
import com.android.tradefed.device.ITestDevice;

import junit.framework.TestCase;

import org.easymock.EasyMock;

/**
 * Unit tests for {@link RemoteManager}.
 */
public class RemoteManagerTest extends TestCase {

    private IDeviceManager mMockDeviceManager;
    private RemoteManager mRemoteMgr;
    private RemoteClient mRemoteClient;
    private ICommandScheduler mMockScheduler;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mMockDeviceManager = EasyMock.createMock(IDeviceManager.class);
        mMockScheduler = EasyMock.createMock(ICommandScheduler.class);
        mRemoteMgr = new RemoteManager(mMockDeviceManager, mMockScheduler);
    }

    @Override
    protected void tearDown() throws Exception {
        if (mRemoteClient != null) {
            mRemoteClient.close();
        }
        if (mRemoteMgr != null) {
            mRemoteMgr.cancel();
        }
        super.tearDown();
    }

    /**
     * An integration test for client-manager interaction, that will filter, then unfilter a device.
     */
    public void testFilterUnfilter() throws Exception {
        ITestDevice device = EasyMock.createMock(ITestDevice.class);
        EasyMock.expect(mMockDeviceManager.forceAllocateDevice("serial")).andReturn(device);
        mMockDeviceManager.freeDevice(EasyMock.eq(device),
                EasyMock.eq(FreeDeviceState.AVAILABLE));

        EasyMock.replay(mMockDeviceManager, device);
        mRemoteMgr.start();
        int port = mRemoteMgr.getPort();
        assertTrue(port != -1);
        mRemoteClient = RemoteClient.connect(port);
        assertTrue(mRemoteClient.sendFilterDevice("serial"));
        assertTrue(mRemoteClient.sendUnfilterDevice("serial"));
        EasyMock.verify(mMockDeviceManager);
    }
}
