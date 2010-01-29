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
package com.android.tradefed;

import com.android.tradefed.command.CommandTest;
import com.android.tradefed.config.AbstractConfigurationTest;
import com.android.tradefed.config.ArgsOptionParserTest;
import com.android.tradefed.device.DeviceManagerTest;
import com.android.tradefed.device.TestDeviceTest;
import com.android.tradefed.invoker.TestInvocationTest;
import com.android.tradefed.result.TestResultForwarderTest;
import com.android.tradefed.testtype.InstrumentationTestTest;

import junit.framework.Test;
import junit.framework.TestSuite;

/**
 * A test suite for all Trade Federation unit tests.
 * <p/>
 * All tests listed here should be self-contained, and do not require any external dependencies.
 */
public class UnitTests extends TestSuite {

    public static Test suite() {
        TestSuite suite = new TestSuite();
        // TODO: instead of explicitly listing each class, consider dynamically discovering classes
        suite.addTestSuite(CommandTest.class);
        suite.addTestSuite(AbstractConfigurationTest.class);
        suite.addTestSuite(ArgsOptionParserTest.class);
        suite.addTestSuite(DeviceManagerTest.class);
        suite.addTestSuite(TestDeviceTest.class);
        suite.addTestSuite(TestInvocationTest.class);
        suite.addTestSuite(TestResultForwarderTest.class);
        suite.addTestSuite(InstrumentationTestTest.class);
        return suite;
    }
}
