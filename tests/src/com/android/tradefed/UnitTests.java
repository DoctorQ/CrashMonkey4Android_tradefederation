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

import com.android.tradefed.build.BuildInfoTest;
import com.android.tradefed.build.DeviceBuildInfoTest;
import com.android.tradefed.build.FileDownloadCacheTest;
import com.android.tradefed.build.KernelBuildInfoTest;
import com.android.tradefed.build.KernelDeviceBuildInfoTest;
import com.android.tradefed.build.OtaZipfileBuildProviderTest;
import com.android.tradefed.build.SdkBuildInfoTest;
import com.android.tradefed.command.CommandFileParserTest;
import com.android.tradefed.command.CommandSchedulerTest;
import com.android.tradefed.command.ConsoleTest;
import com.android.tradefed.command.RemoteManagerTest;
import com.android.tradefed.config.ArgsOptionParserTest;
import com.android.tradefed.config.ConfigurationDefTest;
import com.android.tradefed.config.ConfigurationFactoryTest;
import com.android.tradefed.config.ConfigurationTest;
import com.android.tradefed.config.ConfigurationXmlParserTest;
import com.android.tradefed.config.OptionCopierTest;
import com.android.tradefed.config.OptionSetterTest;
import com.android.tradefed.device.DeviceManagerTest;
import com.android.tradefed.device.DeviceSelectionOptionsTest;
import com.android.tradefed.device.DeviceStateMonitorTest;
import com.android.tradefed.device.LargeOutputReceiverTest;
import com.android.tradefed.device.ReconnectingRecoveryTest;
import com.android.tradefed.device.TestDeviceTest;
import com.android.tradefed.device.WaitDeviceRecoveryTest;
import com.android.tradefed.device.WifiHelperTest;
import com.android.tradefed.invoker.TestInvocationTest;
import com.android.tradefed.log.FileLoggerTest;
import com.android.tradefed.log.LogRegistryTest;
import com.android.tradefed.result.CollectingTestListenerTest;
import com.android.tradefed.result.EmailResultReporterTest;
import com.android.tradefed.result.FailureEmailResultReporterTest;
import com.android.tradefed.result.InvocationFailureEmailResultReporterTest;
import com.android.tradefed.result.InvocationToJUnitResultForwarderTest;
import com.android.tradefed.result.JUnitToInvocationResultForwarderTest;
import com.android.tradefed.result.LogFileSaverTest;
import com.android.tradefed.result.SnapshotInputStreamSourceTest;
import com.android.tradefed.result.TestFailureEmailResultReporterTest;
import com.android.tradefed.result.TestSummaryTest;
import com.android.tradefed.result.XmlResultReporterTest;
import com.android.tradefed.targetprep.DefaultTestsZipInstallerTest;
import com.android.tradefed.targetprep.DeviceFlashPreparerTest;
import com.android.tradefed.targetprep.DeviceSetupTest;
import com.android.tradefed.targetprep.FastbootDeviceFlasherTest;
import com.android.tradefed.targetprep.FlashingResourcesParserTest;
import com.android.tradefed.targetprep.KernelFlashPreparerTest;
import com.android.tradefed.targetprep.SdkAvdPreparerTest;
import com.android.tradefed.targetprep.SystemUpdaterDeviceFlasherTest;
import com.android.tradefed.testtype.DeviceTestCaseTest;
import com.android.tradefed.testtype.DeviceTestSuite;
import com.android.tradefed.testtype.GTestResultParserTest;
import com.android.tradefed.testtype.GTestTest;
import com.android.tradefed.testtype.HostTestTest;
import com.android.tradefed.testtype.InstrumentationListTestTest;
import com.android.tradefed.testtype.InstrumentationTestTest;
import com.android.tradefed.testtype.NativeBenchmarkTestParserTest;
import com.android.tradefed.testtype.NativeStressTestParserTest;
import com.android.tradefed.testtype.NativeStressTestTest;
import com.android.tradefed.testtype.testdefs.XmlDefsParserTest;
import com.android.tradefed.testtype.testdefs.XmlDefsTestTest;
import com.android.tradefed.util.ArrayUtilTest;
import com.android.tradefed.util.ByteArrayListTest;
import com.android.tradefed.util.ConditionPriorityBlockingQueueTest;
import com.android.tradefed.util.EmailTest;
import com.android.tradefed.util.FileUtilTest;
import com.android.tradefed.util.MultiMapTest;
import com.android.tradefed.util.QuotationAwareTokenizerTest;
import com.android.tradefed.util.RegexTrieTest;
import com.android.tradefed.util.RunUtilTest;
import com.android.tradefed.util.brillopad.BrillopadTests;
import com.android.tradefed.util.xml.AndroidManifestWriterTest;

import junit.framework.Test;

/**
 * A test suite for all Trade Federation unit tests.
 * <p/>
 * All tests listed here should be self-contained, and should not require any external dependencies.
 */
public class UnitTests extends DeviceTestSuite {

    public UnitTests() {
        super();
        // build
        addTestSuite(BuildInfoTest.class);
        addTestSuite(DeviceBuildInfoTest.class);
        addTestSuite(FileDownloadCacheTest.class);
        addTestSuite(KernelBuildInfoTest.class);
        addTestSuite(KernelDeviceBuildInfoTest.class);
        addTestSuite(OtaZipfileBuildProviderTest.class);
        addTestSuite(SdkBuildInfoTest.class);

        // command
        addTestSuite(CommandFileParserTest.class);
        addTestSuite(CommandSchedulerTest.class);
        addTestSuite(ConsoleTest.class);
        addTestSuite(RemoteManagerTest.class);

        // config
        addTestSuite(ArgsOptionParserTest.class);
        addTestSuite(ConfigurationDefTest.class);
        addTestSuite(ConfigurationFactoryTest.class);
        addTestSuite(ConfigurationTest.class);
        addTestSuite(ConfigurationXmlParserTest.class);
        addTestSuite(OptionCopierTest.class);
        addTestSuite(OptionSetterTest.class);

        // device
        addTestSuite(DeviceManagerTest.class);
        addTestSuite(DeviceSelectionOptionsTest.class);
        addTestSuite(DeviceStateMonitorTest.class);
        addTestSuite(LargeOutputReceiverTest.class);
        addTestSuite(ReconnectingRecoveryTest.class);
        addTestSuite(TestDeviceTest.class);
        addTestSuite(WaitDeviceRecoveryTest.class);
        addTestSuite(WifiHelperTest.class);

        // invoker
        addTestSuite(TestInvocationTest.class);

        // log
        addTestSuite(FileLoggerTest.class);
        addTestSuite(LogRegistryTest.class);

        // result
        addTestSuite(CollectingTestListenerTest.class);
        addTestSuite(EmailResultReporterTest.class);
        addTestSuite(FailureEmailResultReporterTest.class);
        addTestSuite(InvocationFailureEmailResultReporterTest.class);
        addTestSuite(InvocationToJUnitResultForwarderTest.class);
        addTestSuite(JUnitToInvocationResultForwarderTest.class);
        addTestSuite(LogFileSaverTest.class);
        addTestSuite(SnapshotInputStreamSourceTest.class);
        addTestSuite(TestSummaryTest.class);
        addTestSuite(TestFailureEmailResultReporterTest.class);
        addTestSuite(XmlResultReporterTest.class);

        // targetprep
        addTestSuite(DefaultTestsZipInstallerTest.class);
        addTestSuite(DeviceFlashPreparerTest.class);
        addTestSuite(DeviceSetupTest.class);
        addTestSuite(FastbootDeviceFlasherTest.class);
        addTestSuite(FlashingResourcesParserTest.class);
        addTestSuite(KernelFlashPreparerTest.class);
        addTestSuite(SdkAvdPreparerTest.class);
        addTestSuite(SystemUpdaterDeviceFlasherTest.class);

        // testtype
        addTestSuite(DeviceTestCaseTest.class);
        addTestSuite(GTestResultParserTest.class);
        addTestSuite(GTestTest.class);
        addTestSuite(HostTestTest.class);
        addTestSuite(InstrumentationListTestTest.class);
        addTestSuite(InstrumentationTestTest.class);
        addTestSuite(NativeBenchmarkTestParserTest.class);
        addTestSuite(NativeStressTestParserTest.class);
        addTestSuite(NativeStressTestTest.class);

        // testtype/testdefs
        addTestSuite(XmlDefsParserTest.class);
        addTestSuite(XmlDefsTestTest.class);

        // util
        addTestSuite(ArrayUtilTest.class);
        addTestSuite(ByteArrayListTest.class);
        addTestSuite(ConditionPriorityBlockingQueueTest.class);
        addTestSuite(EmailTest.class);
        addTestSuite(FileUtilTest.class);
        addTestSuite(MultiMapTest.class);
        addTestSuite(QuotationAwareTokenizerTest.class);
        addTestSuite(RegexTrieTest.class);
        addTestSuite(RunUtilTest.class);

        // util subdirs
        addTestSuite(AndroidManifestWriterTest.class);
        addTest(BrillopadTests.suite());
    }

    public static Test suite() {
        return new UnitTests();
    }
}
