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
package com.android.tradefed.util.brillopad;

import com.android.tradefed.testtype.DeviceTestSuite;
import com.android.tradefed.util.brillopad.item.GenericItemTest;

import junit.framework.Test;

/**
 * A test suite for the Brillopad unit tests.
 * <p/>
 * All tests listed here should be self-contained, and should not require any external dependencies.
 */
public class BrillopadTests extends DeviceTestSuite {

    public BrillopadTests() {
        super();

        addTestSuite(AbstractSectionParserTest.class);
        addTestSuite(AnrParserTest.class);
        addTestSuite(BugreportParserTest.class);
        addTestSuite(JavaCrashParserTest.class);
        addTestSuite(LogcatParserTest.class);
        addTestSuite(MemInfoParserTest.class);
        addTestSuite(NativeCrashParserTest.class);
        addTestSuite(ProcrankParserTest.class);
        addTestSuite(SystemPropsParserTest.class);

        // item
        addTestSuite(GenericItemTest.class);
    }

    public static Test suite() {
        return new BrillopadTests();
    }
}
