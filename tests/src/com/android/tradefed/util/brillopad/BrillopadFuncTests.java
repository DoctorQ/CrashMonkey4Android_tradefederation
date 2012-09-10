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
import com.android.tradefed.util.brillopad.parser.BugreportParserFuncTest;
import com.android.tradefed.util.brillopad.parser.LogcatParserFuncTest;
import com.android.tradefed.util.brillopad.parser.MonkeyLogParserFuncTest;

import junit.framework.Test;

/**
 * A test suite for the Brillopad functional tests.
 * <p>
 * In order to run this suite, there must be a bugreport, logcat, and monkey log in
 * {@code /tmp/bugreport.txt}, {@code /tmp/logcat.txt}, and {@code /tmp/monkey_log.txt}.
 * </p>
 */
public class BrillopadFuncTests extends DeviceTestSuite {

    public BrillopadFuncTests() {
        super();

        addTestSuite(BugreportParserFuncTest.class);
        addTestSuite(LogcatParserFuncTest.class);
        addTestSuite(MonkeyLogParserFuncTest.class);
    }

    public static Test suite() {
        return new BrillopadFuncTests();
    }
}
