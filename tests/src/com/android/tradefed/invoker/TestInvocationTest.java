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
package com.android.tradefed.invoker;

import com.android.ddmlib.IDevice;
import com.android.ddmlib.Log.LogLevel;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IConfigurationReceiver;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.ILeveledLogOutput;
import com.android.tradefed.log.LogRegistry;
import com.android.tradefed.log.StubLogRegistry;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.targetsetup.IBuildInfo;
import com.android.tradefed.targetsetup.IBuildProvider;
import com.android.tradefed.targetsetup.ITargetPreparer;
import com.android.tradefed.targetsetup.TargetSetupError;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;

import org.easymock.EasyMock;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import junit.framework.Test;
import junit.framework.TestCase;

/**
 * Unit tests for {@link TestInvocation}.
 */
public class TestInvocationTest extends TestCase {

    /** The {@link TestInvocation} under test, with all dependencies mocked out */
    private TestInvocation mTestInvocation;

    // The mock objects.
    private IConfiguration mMockConfiguration;
    private ITestDevice mMockDevice;
    private ITargetPreparer mMockPreparer;
    private IBuildProvider mMockBuildRetriever;
    private IBuildInfo mMockBuildInfo;
    private ITestInvocationListener mMockTestListener;
    private ILeveledLogOutput mMockLogger;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mMockConfiguration = EasyMock.createMock(IConfiguration.class);
        mMockDevice = EasyMock.createMock(ITestDevice.class);
        mMockPreparer = EasyMock.createMock(ITargetPreparer.class);
        mMockBuildRetriever = EasyMock.createMock(IBuildProvider.class);
        mMockTestListener = EasyMock.createMock(ITestInvocationListener.class);
        mMockBuildInfo = EasyMock.createMock(IBuildInfo.class);
        mMockLogger = EasyMock.createNiceMock(ILeveledLogOutput.class);

        EasyMock.expect(mMockConfiguration.getBuildProvider()).andReturn(mMockBuildRetriever);
        EasyMock.expect(mMockConfiguration.getTargetPreparer()).andReturn(mMockPreparer);
        EasyMock.expect(mMockConfiguration.getTestInvocationListener()).andReturn(
                mMockTestListener);
        EasyMock.expect(mMockConfiguration.getLogOutput()).andReturn(
                mMockLogger);
        mMockTestListener.testRunLog(EasyMock.eq(TestInvocation.TRADEFED_LOG_NAME),
                EasyMock.eq(LogDataType.TEXT), (InputStream)EasyMock.anyObject());

        // create the BaseTestInvocation to test
        mTestInvocation = new TestInvocation() {
            @Override
            LogRegistry getLogRegistry() {
                // use a StubLogRegistry to prevent registration of mock loggers
                return new StubLogRegistry();
            }
        };
    }

    @Override
    protected void tearDown() throws Exception {
      super.tearDown();

    }


    /**
     * Test the normal case invoke scenario with a {@link IRemoteTest}.
     * <p/>
     * Verifies that all external interfaces get notified as expected.
     */
    public void testInvoke_RemoteTest() throws Exception {
        IRemoteTest test = EasyMock.createMock(IRemoteTest.class);
        mMockTestListener.invocationStarted(mMockBuildInfo);
        mMockTestListener.invocationEnded();
        test.run(mMockTestListener);
        setupNormalInvoke(test);
        mTestInvocation.invoke(mMockDevice, mMockConfiguration);
    }

    /**
     * Test the invoke scenario where build retrieve fails.
     */
    public void testInvoke_buildFailed() throws TargetSetupError, ConfigurationException,
            DeviceNotAvailableException  {
        TargetSetupError exception = new TargetSetupError("error");
        EasyMock.expect(mMockBuildRetriever.getBuild()).andThrow(exception);
        Test test = EasyMock.createMock(Test.class);
        EasyMock.expect(mMockConfiguration.getTest()).andReturn(test);
        EasyMock.expect(mMockLogger.getLogLevel()).andReturn(LogLevel.VERBOSE.getStringValue());
        mMockLogger.printLog((LogLevel)EasyMock.anyObject(),
            (String)EasyMock.anyObject(), (String)EasyMock.anyObject());
        EasyMock.expect(mMockLogger.getLog()).andReturn(new ByteArrayInputStream(new byte[0]));
        mMockLogger.closeLog();
        mMockTestListener.invocationFailed((String)EasyMock.anyObject(), EasyMock.eq(exception));
        // expect no other methods to be called
        EasyMock.replay(test, mMockTestListener, mMockConfiguration, mMockPreparer,
                mMockBuildRetriever, mMockLogger);
        // TODO: add verification for sending an error/logging error ?
        mTestInvocation.invoke(mMockDevice, mMockConfiguration);
    }

    /**
     * Test the invoke scenario where there is no build to test.
     */
    public void testInvoke_noBuild() throws TargetSetupError, ConfigurationException,
            DeviceNotAvailableException  {
        EasyMock.expect(mMockBuildRetriever.getBuild()).andReturn(null);
        // expect no other methods to be called
        EasyMock.replay(mMockTestListener, mMockConfiguration, mMockPreparer,
                mMockBuildRetriever);
        mTestInvocation.invoke(mMockDevice, mMockConfiguration);
    }

    /**
     * Test the invoke scenario where config retrieve fails.
     */
    public void testInvoke_configFailed() throws ConfigurationException,
            DeviceNotAvailableException  {
        EasyMock.expect(mMockConfiguration.getTest()).andThrow(new ConfigurationException("fail"));
        // expect no other methods to be called
        EasyMock.expect(mMockLogger.getLogLevel()).andReturn(LogLevel.VERBOSE.getStringValue());
        mMockLogger.printLog((LogLevel)EasyMock.anyObject(),
            (String)EasyMock.anyObject(), (String)EasyMock.anyObject());
        mMockLogger.closeLog();
        EasyMock.replay(mMockTestListener, mMockConfiguration, mMockPreparer,
                mMockBuildRetriever, mMockLogger);
        // TODO: add verification for sending an error/logging error ?
        mTestInvocation.invoke(mMockDevice, mMockConfiguration);
    }

    /**
     * Test the {@link TestInvocation#invoke(IDevice, IConfiguration)} scenario where the
     * test is a {@link IDeviceTest} and a {@link IConfigurationReceiver}
     */
    public void testInvoke_deviceConfigTest() throws Exception {
         DeviceConfigTest mockDeviceConfigTest = EasyMock.createMock(DeviceConfigTest.class);
         EasyMock.expect(mMockConfiguration.getTest()).andReturn(mockDeviceConfigTest);
         mockDeviceConfigTest.setDevice(mMockDevice);
         mockDeviceConfigTest.setConfiguration(mMockConfiguration);
         mockDeviceConfigTest.run(mMockTestListener);
         mMockTestListener.invocationStarted(mMockBuildInfo);
         mMockTestListener.invocationEnded();
         setupNormalInvoke(mockDeviceConfigTest);
         mTestInvocation.invoke(mMockDevice, mMockConfiguration);
    }

    /**
     * Test the invoke scenario where test run throws {@link IllegalArgumentException}
     *
     * @throws Exception if unexpected error occurs
     */
    public void testInvoke_testFail() throws Exception {
        IllegalArgumentException exception = new IllegalArgumentException();
        IRemoteTest test = EasyMock.createMock(IRemoteTest.class);
        test.run(mMockTestListener);
        EasyMock.expectLastCall().andThrow(exception);
        mMockTestListener.invocationStarted(mMockBuildInfo);
        mMockTestListener.invocationFailed((String)EasyMock.anyObject(), EasyMock.eq(exception));
        EasyMock.expect(mMockLogger.getLogLevel()).andReturn(LogLevel.VERBOSE.getStringValue());
        mMockLogger.printLog((LogLevel)EasyMock.anyObject(),
            (String)EasyMock.anyObject(), (String)EasyMock.anyObject());
        setupNormalInvoke(test);
        mTestInvocation.invoke(mMockDevice, mMockConfiguration);
    }

    /**
     * Set up expected conditions for normal run up to the part where tests are run.
     *
     * @param test the {@link Test} to use.
     */
    private void setupNormalInvoke(Test test) throws TargetSetupError, ConfigurationException,
            DeviceNotAvailableException {
        EasyMock.expect(mMockConfiguration.getTest()).andReturn(test);
        EasyMock.expect(mMockBuildRetriever.getBuild()).andReturn(mMockBuildInfo);
        EasyMock.expect(mMockLogger.getLog()).andReturn(new ByteArrayInputStream(new byte[0]));
        mMockLogger.closeLog();
        mMockPreparer.setUp(mMockDevice, mMockBuildInfo);
        EasyMock.replay(test, mMockTestListener, mMockConfiguration, mMockPreparer,
                mMockBuildRetriever, mMockLogger);
    }

    /**
     * Interface for testing device config pass through.
     */
    private interface DeviceConfigTest extends IRemoteTest, IConfigurationReceiver, IDeviceTest {

    }
}
