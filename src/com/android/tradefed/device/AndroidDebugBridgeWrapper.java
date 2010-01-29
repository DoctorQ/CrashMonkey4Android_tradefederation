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

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.AndroidDebugBridge.IDeviceChangeListener;

/**
 * A wrapper that directs {@link IAndroidDebugBridge} calls to the 'real'
 * {@link AndroidDebugBridge}.
 */
class AndroidDebugBridgeWrapper implements IAndroidDebugBridge {

    private AndroidDebugBridge mAdbBridge;

    /**
     * Creates a {@link AndroidDebugBridgeWrapper}.
     * <p/>
     * {@link AndroidDebugBridge#init(boolean)} must be called before this.
     */
    AndroidDebugBridgeWrapper() {
        mAdbBridge = AndroidDebugBridge.createBridge();
    }

    /**
     * {@inheritDoc}
     */
    public IDevice[] getDevices() {
        return mAdbBridge.getDevices();
    }

    /**
     * {@inheritDoc}
     */
    public void addDeviceChangeListener(IDeviceChangeListener listener) {
        AndroidDebugBridge.addDeviceChangeListener(listener);
    }

    /**
     * {@inheritDoc}
     */
    public void removeDeviceChangeListener(IDeviceChangeListener listener) {
        AndroidDebugBridge.removeDeviceChangeListener(listener);
    }

}
