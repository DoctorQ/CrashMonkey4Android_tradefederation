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

package com.android.tradefed.utils;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * A BroadcastReceiver that starts a service to dismiss the keyguard.
 *
 * adb shell am broadcast -a com.android.tradefed.actor.DISMISS_KEYGUARD
 */
public class DismissKeyguardIntentReceiver extends BroadcastReceiver {

    public static final String
            DISMISS_KEYGUARD_INTENT = "com.android.tradefed.utils.DISMISS_KEYGUARD";
    public static final String TAG = "DismissKeyguardUtil";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (DISMISS_KEYGUARD_INTENT.equals(intent.getAction())) {
            Log.v(TAG, "Starting Service");
            Intent pushIntent = new Intent(context, DismissKeyguardService.class);
            context.startService(pushIntent);
        }
    }
}
