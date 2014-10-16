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

package com.android.tradefed.log;

import com.android.ddmlib.Log;
import com.android.tradefed.log.LogUtil.CLog;

import junit.framework.TestCase;

/**
 * A set of functional testcases for {@link LogUtil}
 */
public class LogUtilFuncTest extends TestCase {
    private static final String CLASS_NAME = "LogUtilFuncTest";
    private static final String STRING = "hallo!";

    public void testCLog_v() {
        Log.v(CLASS_NAME, "this is the real Log.v");
        CLog.v("this is CLog.v");
        CLog.v("this is CLog.v with a format string: %s has length %d", STRING, STRING.length());
    }

    public void testCLog_d() {
        Log.d(CLASS_NAME, "this is the real Log.d");
        CLog.d("this is CLog.d");
        CLog.d("this is CLog.d with a format string: %s has length %d", STRING, STRING.length());
    }

    public void testCLog_i() {
        Log.i(CLASS_NAME, "this is the real Log.i");
        CLog.i("this is CLog.i");
        CLog.i("this is CLog.i with a format string: %s has length %d", STRING, STRING.length());
    }

    public void testCLog_w() {
        Log.w(CLASS_NAME, "this is the real Log.w");
        CLog.w("this is CLog.w");
        CLog.w("this is CLog.w with a format string: %s has length %d", STRING, STRING.length());
    }

    public void testCLog_e() {
        Log.e(CLASS_NAME, "this is the real Log.e");
        CLog.e("this is CLog.e");
        CLog.e("this is CLog.e with a format string: %s has length %d", STRING, STRING.length());
    }

    public void testCLog_getClassName() {
        String klass = CLog.getClassName(1);
        assertTrue(CLASS_NAME.equals(klass));
    }
}

