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

package com.android.tradefed.command;

import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IConfigurationFactory;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.DeviceSelectionOptions;
import com.android.tradefed.device.IDeviceManager;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.IDeviceManager.FreeDeviceState;
import com.android.tradefed.invoker.IRescheduler;
import com.android.tradefed.invoker.ITestInvocation;
import com.android.tradefed.log.LogRegistry;
import com.android.tradefed.log.StubLogRegistry;

import org.easymock.EasyMock;

import java.io.PrintStream;

import junit.framework.TestCase;

/**
 * Unit tests for {@link Command}
 */
public class CommandTest extends TestCase {

    /** the {@link Command} under test, with all dependencies mocked out */
    private Command mCommand;
    private ITestInvocation mMockTestInvoker;
    private IDeviceManager mMockDeviceManager;
    private IConfiguration mMockConfiguration;
    private ITestDevice mMockDevice;
    private IConfigurationFactory mMockConfigFactory;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mMockTestInvoker = EasyMock.createMock(ITestInvocation.class);
        mMockDeviceManager = EasyMock.createMock(IDeviceManager.class);
        mMockConfiguration = EasyMock.createMock(IConfiguration.class);
        mMockDevice = EasyMock.createMock(ITestDevice.class);
        mMockConfigFactory = EasyMock.createMock(IConfigurationFactory.class);

        mCommand = new Command() {
            @Override
            ITestInvocation createRunInstance() {
                return mMockTestInvoker;
            }

            @Override
            IDeviceManager getDeviceManager() {
                return mMockDeviceManager;
            }

            @Override
            IConfigurationFactory getConfigFactory() {
                return mMockConfigFactory;
            }

            @Override
            void initLogging() {
                // do nothing

            }

            @Override
            LogRegistry getLogRegistry() {
                return new StubLogRegistry();
            }
        };
    }

    /**
     * Test for a normal run that proceeds without error.
     */
    public void testRun() throws DeviceNotAvailableException, ConfigurationException {
        setCreateConfigExpectations();
        setAllocateDeviceExpectations();
        mMockDeviceManager.freeDevice(mMockDevice, FreeDeviceState.AVAILABLE);
        mMockDeviceManager.terminate();
        mMockTestInvoker.invoke(EasyMock.eq(mMockDevice), EasyMock.eq(mMockConfiguration),
                (IRescheduler)EasyMock.anyObject());
        // switch mock objects to verify mode
        replayMocks();
        mCommand.run(new String[] {});
        verifyMocks();
    }

    /**
     * Test run when a device cannot be allocated.
     * <p/>
     * Expect exception to be captured gracefully and run to not proceed.
     */
    public void testRun_noDevice() throws DeviceNotAvailableException, ConfigurationException {
        setCreateConfigExpectations();
        // expect to be asked for device to connect to and return the mock device
        EasyMock.expect(
                mMockDeviceManager.allocateDevice(EasyMock.anyLong(),
                        (DeviceSelectionOptions)EasyMock.anyObject())).andReturn(null);
        mMockDeviceManager.terminate();

        replayMocks();
        mCommand.run(new String[] {});
        verifyMocks();
    }

    /**
     * Test that scenario where configuration cannot be loaded.
     * <p/>
     * Expect exception to be captured gracefully and run to not proceed.
     */
    public void testRun_configException() throws ConfigurationException {
        String[] args = new String[] {};
        setConfigExceptionExpectations();
        setPrintHelpExpectations(args);
        replayMocks();
        mCommand.run(args);
        verifyMocks();
    }

    /**
     * Test that scenario where the invocation throws a unexpected exception
     * <p/>
     * Expect device and device manager to be freed.
     */
    public void testRun_uncaughtThrowable() throws ConfigurationException,
            DeviceNotAvailableException {
        setCreateConfigExpectations();
        setAllocateDeviceExpectations();
        mMockDeviceManager.freeDevice(mMockDevice, FreeDeviceState.AVAILABLE);
        mMockDeviceManager.terminate();
        mMockTestInvoker.invoke(EasyMock.eq(mMockDevice), EasyMock.eq(mMockConfiguration),
                (IRescheduler)EasyMock.anyObject());
        EasyMock.expectLastCall().andThrow(new RuntimeException());
        replayMocks();
        try {
            mCommand.run(new String[] {});
        } catch (RuntimeException e) {
            // expected
        }
        verifyMocks();
    }

    /**
     * Verify the general "help" option works.
     */
    public void testRun_help() throws ConfigurationException,
            DeviceNotAvailableException {
        String[] args = new String[] {"--help"};
        // expect a config exception in response to --help command
        setConfigExceptionExpectations();
        setPrintHelpExpectations(args);
        replayMocks();
        mCommand.run(args);
        verifyMocks();
    }

    /**
     * Configure EasyMock expectations for a
     * {@link IConfigurationFactory#printHelp(String[], PrintStream, Class...) call.
     */
    private void setPrintHelpExpectations(String[] args) {
        mMockConfigFactory.printHelp(EasyMock.eq(args), (PrintStream)EasyMock.anyObject(),
                EasyMock.eq(mCommand.getClass()), EasyMock.eq(DeviceSelectionOptions.class));
    }

    /**
     * Configure EasyMock expectations for a
     * {@link IDeviceManager#allocateDevice(long, DeviceSelectionOptions) call.
     */
    private void setAllocateDeviceExpectations() {
        // expect to be asked for device to connect to and return the mock device
        EasyMock.expect(mMockDeviceManager.allocateDevice(EasyMock.eq(Command.WAIT_DEVICE_TIME),
                (DeviceSelectionOptions)EasyMock.anyObject()))
                .andReturn(mMockDevice);
    }

    /**
     * Configure EasyMock expectations for a
     * {@link IConfigurationFactory#createConfigurationFromArgs(String[], Object...)} call.
     */
    private void setCreateConfigExpectations() throws ConfigurationException {
        EasyMock.expect(
                mMockConfigFactory.createConfigurationFromArgs((String[])EasyMock.anyObject(),
                        EasyMock.eq(mCommand), EasyMock.anyObject())).andReturn(mMockConfiguration);
    }

    /**
     * Configure EasyMock expectations for a failed
     * {@link IConfigurationFactory#createConfigurationFromArgs(String[], Object...)} call.
     */
    private void setConfigExceptionExpectations() throws ConfigurationException {
        EasyMock.expect(
                mMockConfigFactory.createConfigurationFromArgs((String[])EasyMock.anyObject(),
                        EasyMock.eq(mCommand), EasyMock.anyObject())).andThrow(
                                new ConfigurationException(""));
    }

    /**
     * Set the mock objects to replay mode.
     */
    private void replayMocks() {
        EasyMock.replay(mMockConfiguration, mMockDeviceManager, mMockTestInvoker,
                mMockConfigFactory);
    }

    /**
     * Set the mock objects to verify mode.
     */
    private void verifyMocks() {
        EasyMock.verify(mMockConfiguration, mMockDeviceManager, mMockTestInvoker,
                mMockConfigFactory);
    }
}
