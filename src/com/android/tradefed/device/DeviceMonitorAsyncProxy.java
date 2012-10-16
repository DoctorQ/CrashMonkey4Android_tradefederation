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

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * A thin shim that makes {@link IDeviceMonitor} callbacks asynchronous, in order to prevent
 * potential deadlocks.
 */
public class DeviceMonitorAsyncProxy implements IDeviceMonitor {

    /**
     * A simple thread which runs pending commands asynchronously.  This avoids deadlocks by
     * decoupling any lock-taking semantics from any locks held by callers into this
     * {@link DeviceMonitorAsyncProxy} instance.
     */
    static class Dispatcher extends Thread {
        private final BlockingQueue<Runnable> mRunnableQueue;
        private boolean mCanceled = false;

        Dispatcher(BlockingQueue<Runnable> q) {
            mRunnableQueue = q;
            this.setDaemon(true);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void run() {
            Runnable runner = null;
            while (!mCanceled) {
                try {
                    runner = mRunnableQueue.take();
                    runner.run();
                } catch (InterruptedException e) {
                    // ignore.  This just gives us an opportunity to re-evaluate mCanceled
                }
            }
        }

        public void cancel() {
            mCanceled = true;
            //this.interrupt();
            // Add a no-op Runnable to the queue so to force re-evaluation of mCanceled on the work
            // loop
            mRunnableQueue.add(new Runnable() {
                @Override
                public void run() {}
            });
        }
    }

    private final BlockingQueue<Runnable> mRunnableQueue = new LinkedBlockingQueue<Runnable>();
    private Dispatcher mDispatcher;
    private IDeviceMonitor mChildMonitor;

    public DeviceMonitorAsyncProxy(IDeviceMonitor monitor) {
        mChildMonitor = monitor;

        if (mChildMonitor == null) {
            // disabled
            mDispatcher = null;
            return;
        } else {
            // enabled; spin up the dispatcher thread
            mDispatcher = new Dispatcher(mRunnableQueue);
            mDispatcher.start();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setDeviceLister(DeviceLister lister) {
        if (mChildMonitor != null) {
            mChildMonitor.setDeviceLister(lister);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void updateFullDeviceState() {
        if ((mChildMonitor == null) || (mDispatcher == null)) return;

        final Runnable task = new Runnable() {
            @Override
            public void run() {
                mChildMonitor.updateFullDeviceState();
            }
        };
        mRunnableQueue.add(task);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deviceAllocated(final IDevice device) {
        if ((mChildMonitor == null) || (mDispatcher == null)) return;

        final Runnable task = new Runnable() {
            @Override
            public void run() {
                mChildMonitor.deviceAllocated(device);
            }
        };
        mRunnableQueue.add(task);
    }

    /**
     * Force-terminate this Proxy.  Once terminated, all calls will become no-ops.
     * Exposed for unit tests
     */
    public void cancel() {
        if ((mChildMonitor == null) || (mDispatcher == null)) return;
        mDispatcher.cancel();
        mDispatcher = null;
        mChildMonitor = null;
    }

    Dispatcher getDispatcher() {
        return mDispatcher;
    }
}

