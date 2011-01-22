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

import com.android.tradefed.command.CommandFileParserTest;
import com.android.tradefed.command.CommandSchedulerTest;
import com.android.tradefed.command.CommandTest;
import com.android.tradefed.command.ConsoleTest;
import com.android.tradefed.config.ArgsOptionParserTest;
import com.android.tradefed.config.ConfigurationDefTest;
import com.android.tradefed.config.ConfigurationFactoryTest;
import com.android.tradefed.config.ConfigurationTest;
import com.android.tradefed.config.ConfigurationXmlParserTest;
import com.android.tradefed.config.OptionSetterTest;
import com.android.tradefed.device.DeviceManagerTest;
import com.android.tradefed.device.DeviceStateMonitorTest;
import com.android.tradefed.device.TestDeviceTest;
import com.android.tradefed.device.WifiHelperTest;
import com.android.tradefed.invoker.TestInvocationTest;
import com.android.tradefed.log.FileLoggerTest;
import com.android.tradefed.log.LogRegistryTest;
import com.android.tradefed.result.CollectingTestListenerTest;
import com.android.tradefed.result.InvocationToJUnitResultForwarderTest;
import com.android.tradefed.result.JUnitToInvocationResultForwarderTest;
import com.android.tradefed.result.LogFileSaverTest;
import com.android.tradefed.result.TestSummaryTest;
import com.android.tradefed.result.XmlResultReporterTest;
import com.android.tradefed.targetsetup.DeviceBuildInfoTest;
import com.android.tradefed.targetsetup.DeviceFlasherTest;
import com.android.tradefed.targetsetup.DeviceSetupTest;
import com.android.tradefed.targetsetup.FileDownloadCacheTest;
import com.android.tradefed.targetsetup.FlashingResourcesParserTest;
import com.android.tradefed.testtype.DeviceTestCaseTest;
import com.android.tradefed.testtype.GTestResultParserTest;
import com.android.tradefed.testtype.GTestTest;
import com.android.tradefed.testtype.HostTestTest;
import com.android.tradefed.testtype.InstrumentationListTestTest;
import com.android.tradefed.testtype.InstrumentationTestTest;
import com.android.tradefed.testtype.testdefs.XmlDefsParserTest;
import com.android.tradefed.testtype.testdefs.XmlDefsTestTest;
import com.android.tradefed.util.ConditionPriorityBlockingQueueTest;
import com.android.tradefed.util.FileUtilTest;
import com.android.tradefed.util.RunUtilTest;

import junit.framework.Test;
import junit.framework.TestSuite;

/**
 * A test suite for all Trade Federation unit tests.
 * <p/>
 * All tests listed here should be self-contained, and do not require any external dependencies.
 */
public class UnitTests extends TestSuite {

    public UnitTests() {
        super();
        addTestSuite(CommandFileParserTest.class);
        addTestSuite(CommandTest.class);
        addTestSuite(CommandSchedulerTest.class);
        addTestSuite(ConditionPriorityBlockingQueueTest.class);
        addTestSuite(ConsoleTest.class);
        addTestSuite(ConfigurationTest.class);
        addTestSuite(ArgsOptionParserTest.class);
        addTestSuite(OptionSetterTest.class);
        addTestSuite(ConfigurationDefTest.class);
        addTestSuite(ConfigurationFactoryTest.class);
        addTestSuite(ConfigurationXmlParserTest.class);
        addTestSuite(DeviceManagerTest.class);
        addTestSuite(DeviceStateMonitorTest.class);
        addTestSuite(TestDeviceTest.class);
        addTestSuite(TestInvocationTest.class);
        addTestSuite(WifiHelperTest.class);
        addTestSuite(FileLoggerTest.class);
        addTestSuite(LogRegistryTest.class);
        addTestSuite(CollectingTestListenerTest.class);
        addTestSuite(JUnitToInvocationResultForwarderTest.class);
        addTestSuite(LogFileSaverTest.class);
        addTestSuite(InvocationToJUnitResultForwarderTest.class);
        addTestSuite(TestSummaryTest.class);
        addTestSuite(XmlResultReporterTest.class);
        addTestSuite(DeviceBuildInfoTest.class);
        addTestSuite(DeviceFlasherTest.class);
        addTestSuite(DeviceSetupTest.class);
        addTestSuite(FlashingResourcesParserTest.class);
        addTestSuite(FileDownloadCacheTest.class);
        addTestSuite(DeviceTestCaseTest.class);
        addTestSuite(InstrumentationListTestTest.class);
        addTestSuite(InstrumentationTestTest.class);
        addTestSuite(GTestTest.class);
        addTestSuite(GTestResultParserTest.class);
        addTestSuite(HostTestTest.class);
        addTestSuite(XmlDefsParserTest.class);
        addTestSuite(XmlDefsTestTest.class);
        addTestSuite(RunUtilTest.class);
        addTestSuite(FileUtilTest.class);
    }

    public static Test suite() {
        return new UnitTests();
    }
}
