/*
 * Copyright (C) 2012 The Android Open Source Project
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
import com.android.tradefed.device.DeviceMonitorAsyncProxy.Dispatcher;
import com.android.tradefed.util.RunUtil;

import org.easymock.EasyMock;
import org.easymock.IAnswer;

import junit.framework.TestCase;

/**
 * Simple unit tests for {@link DeviceMonitorAsyncProxy}
 */
public class DeviceMonitorAsyncProxyTest extends TestCase {
    DeviceMonitorAsyncProxy mProxy = null;
    IDeviceMonitor mMockMonitor = null;
    IDevice mMockDevice = null;

    @Override
    public void setUp() {
        mMockMonitor = EasyMock.createStrictMock(IDeviceMonitor.class);
        mMockDevice = EasyMock.createMock(IDevice.class);
        mProxy = new DeviceMonitorAsyncProxy(mMockMonitor);
    }

    @Override
    public void tearDown() {
        mProxy.cancel();
    }

    public void testAllocated() {
        mMockMonitor.run();
        mMockMonitor.deviceAllocated(EasyMock.eq(mMockDevice));
        EasyMock.replay(mMockMonitor, mMockDevice);
        mProxy.run();
        mProxy.deviceAllocated(mMockDevice);

        // Wait for magic to happen
        RunUtil.getDefault().sleep(100);

        EasyMock.verify(mMockMonitor, mMockDevice);
    }

    public void testCancel() {
        mMockMonitor.run();
        mMockMonitor.deviceAllocated(EasyMock.eq(mMockDevice));
        EasyMock.replay(mMockMonitor, mMockDevice);

        mProxy.run();
        // this call should register
        mProxy.deviceAllocated(mMockDevice);
        // Wait for magic to happen (so we don't cancel before the first call is processed)
        RunUtil.getDefault().sleep(100);
        Dispatcher disp = mProxy.getDispatcher();
        assertTrue(disp.isAlive());
        mProxy.cancel();

        RunUtil.getDefault().sleep(100);
        assertFalse(disp.isAlive());  // Do this before a subsequent exception might kill the thread
        assertNull(mProxy.getDispatcher());

        // this call should be ignored
        mProxy.deviceAllocated(mMockDevice);

        // Wait for things to happen, in case the cancel() didn't work as expected
        RunUtil.getDefault().sleep(100);

        EasyMock.verify(mMockMonitor, mMockDevice);
    }

    public void testCancel_whileBlocking() {
        mProxy.run();
        // At this point, the Dispatcher will have been started
        assertNotNull(mProxy.getDispatcher());
        mProxy.cancel();
        RunUtil.getDefault().sleep(100);
        assertNull(mProxy.getDispatcher());
    }

    public void testCancel_nullDvcMon() {
        DeviceMonitorAsyncProxy proxy = new DeviceMonitorAsyncProxy(null);
        assertNull(proxy.getDispatcher());
    }

    /**
     * Make sure that a delay in processing is not reflected on the calling side
     */
    public void testDelay() {
        mMockMonitor.run();
        mMockMonitor.deviceAllocated(EasyMock.eq(mMockDevice));
        EasyMock.expectLastCall().andAnswer(new IAnswer<Object>() {
            @Override
            public Object answer() {
                RunUtil.getDefault().sleep(100);
                return null;
            }
        });
        EasyMock.replay(mMockMonitor, mMockDevice);

        mProxy.run();
        final long then = System.currentTimeMillis();
        mProxy.deviceAllocated(mMockDevice);
        final long now = System.currentTimeMillis();
        final long diff = now - then;
        final long thresh = 20;

        assertTrue(
                String.format("deviceAllocated call took %d msecs; threshold is %d!", diff, thresh),
                diff < thresh);
        System.err.format("deviceAllocated call took %d msecs", now - then);

        // Wait for magic to happen
        RunUtil.getDefault().sleep(100);

        EasyMock.verify(mMockMonitor, mMockDevice);
    }

    /**
     * Ensure that a RuntimeException thrown by a Runnable is logged with CLog before the Dispatcher
     * thread exits, so that we can see historical evidence of the exception happening.
     * <p />
     * Note that this test requires manual verification
     */
    public void testException() {
        mProxy.run();
        mProxy.getRunnableQueue().add(new Runnable() {
            @Override
            public void run() {
                throw new RuntimeException("boom.");
            }
        });

        // Wait for magic to happen
        RunUtil.getDefault().sleep(100);
    }
}

