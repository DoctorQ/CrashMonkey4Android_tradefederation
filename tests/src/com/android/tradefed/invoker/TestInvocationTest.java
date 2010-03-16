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
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.IConfigurationReceiver;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.targetsetup.IBuildInfo;
import com.android.tradefed.targetsetup.IBuildProvider;
import com.android.tradefed.targetsetup.ITargetPreparer;
import com.android.tradefed.targetsetup.TargetSetupError;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;

import org.easymock.EasyMock;

import junit.framework.Test;
import junit.framework.TestCase;

/**
 * Unit tests for {@link TestInvocation}.
 */
public class TestInvocationTest extends TestCase {

    /** The {@link TestInvocation} under test, with all dependencies mocked out */
    private TestInvocation mTestInvocation;

    // The mock objects. Holy mockery Batman, thats a lot of mocks
    private IConfiguration mMockConfiguration;
    private ITestDevice mMockDevice;
    private ITargetPreparer mMockPreparer;
    private IBuildProvider mMockBuildRetriever;
    private IBuildInfo mMockBuildInfo;
    private ITestInvocationListener mMockTestListener;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mMockConfiguration = EasyMock.createMock(IConfiguration.class);
        mMockDevice = EasyMock.createMock(ITestDevice.class);
        mMockPreparer = EasyMock.createMock(ITargetPreparer.class);
        mMockBuildRetriever = EasyMock.createMock(IBuildProvider.class);
        mMockTestListener = EasyMock.createMock(ITestInvocationListener.class);
        mMockBuildInfo = EasyMock.createMock(IBuildInfo.class);

        EasyMock.expect(mMockConfiguration.getBuildProvider()).andReturn(mMockBuildRetriever);
        EasyMock.expect(mMockConfiguration.getTargetPreparer()).andReturn(mMockPreparer);
        EasyMock.expect(mMockConfiguration.getTestInvocationListener()).andReturn(
                mMockTestListener);

        // create the BaseTestInvocation to test
        mTestInvocation = new TestInvocation();
    }

    /**
     * Test the normal case invoke scenario with a {@link IRemoteTest}.
     * <p/>
     * Verifies that all external interfaces get notified as expected.
     */
    public void testInvoke_RemoteTest() throws Exception {
        IRemoteTest test = EasyMock.createMock(IRemoteTest.class);
        setupNormalInvoke(test);
        test.run(mMockTestListener);
        EasyMock.replay(test);
        mTestInvocation.invoke(mMockDevice, mMockConfiguration);
    }

    /**
     * Test the invoke scenario where build retrieve fails.
     */
    public void testInvoke_buildFailed() throws TargetSetupError, ConfigurationException  {
        EasyMock.expect(mMockBuildRetriever.getBuild()).andThrow(
                new TargetSetupError("error"));
        Test test = EasyMock.createMock(Test.class);
        EasyMock.expect(mMockConfiguration.getTest()).andReturn(test);
        // expect no other methods to be called
        EasyMock.replay(mMockBuildRetriever);
        EasyMock.replay(mMockPreparer);
        EasyMock.replay(test);
        EasyMock.replay(mMockConfiguration);
        // TODO: add verification for sending an error/logging error ?
        mTestInvocation.invoke(mMockDevice, mMockConfiguration);
    }

    /**
     * Test the invoke scenario where config retrieve fails.
     */
    public void testInvoke_configFailed() throws ConfigurationException  {
        EasyMock.expect(mMockConfiguration.getTest()).andThrow(new ConfigurationException("fail"));
        // expect no other methods to be called
        EasyMock.replay(mMockBuildRetriever);
        EasyMock.replay(mMockPreparer);
        EasyMock.replay(mMockConfiguration);
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
         EasyMock.replay(mockDeviceConfigTest);
         setupNormalInvoke(mockDeviceConfigTest);
         mTestInvocation.invoke(mMockDevice, mMockConfiguration);
    }

    /**
     * Test the invoke scenario where test run throws {@link IllegalArgumentException}
     *
     * @throws Exception if unexpected error occurs
     */
    public void testInvoke_testFail() throws Exception {
        IRemoteTest test = EasyMock.createMock(IRemoteTest.class);
        setupNormalInvoke(test);
        test.run(mMockTestListener);
        EasyMock.expectLastCall().andThrow(new IllegalArgumentException());
        EasyMock.replay(test);
        mTestInvocation.invoke(mMockDevice, mMockConfiguration);
    }

    /**
     * Set up expected conditions for normal run up to the part where tests are run.
     *
     * @param test the {@link Test} to use.
     */
    private void setupNormalInvoke(Test test) throws TargetSetupError, ConfigurationException {
        EasyMock.expect(mMockConfiguration.getTest()).andReturn(test);
        EasyMock.expect(mMockBuildRetriever.getBuild()).andReturn(mMockBuildInfo);
        mMockPreparer.setUp(mMockDevice, mMockBuildInfo);
        EasyMock.replay(mMockConfiguration);
        EasyMock.replay(mMockBuildRetriever);
        EasyMock.replay(mMockPreparer);
    }

    /**
     * Interface for testing device config pass through.
     */
    private interface DeviceConfigTest extends IRemoteTest, IConfigurationReceiver, IDeviceTest {

    }
}
