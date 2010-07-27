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

import com.android.tradefed.command.CommandScheduler.CommandOptions;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IConfigurationFactory;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.IDeviceManager;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.MockDeviceManager;
import com.android.tradefed.device.IDeviceManager.FreeDeviceState;
import com.android.tradefed.invoker.ITestInvocation;

import org.easymock.EasyMock;

import junit.framework.TestCase;

/**
 * Unit tests for {@link CommandScheduler}.
 */
public class CommandSchedulerTest extends TestCase {

    private CommandScheduler mScheduler;
    private MockInvocation mMockInvocation;
    private MockDeviceManager mMockManager;
    private IConfigurationFactory mMockConfigFactory;
    private IConfiguration mMockConfiguration;
    private CommandOptions mCommandOptions;

    /**
     * {@inheritDoc}
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mMockInvocation = new MockInvocation();
        mMockManager = new MockDeviceManager(0);
        mMockConfigFactory = EasyMock.createMock(IConfigurationFactory.class);
        mMockConfiguration = EasyMock.createMock(IConfiguration.class);
        mCommandOptions = new CommandOptions();

        mScheduler = new CommandScheduler() {
            @Override
            ITestInvocation createRunInstance() {
                return mMockInvocation;
            }

            @Override
            IDeviceManager getDeviceManager() {
                return mMockManager;
            }

            @Override
            IConfigurationFactory getConfigFactory() {
                return mMockConfigFactory;
            }

            @Override
            CommandOptions createCommandOptions() {
                return mCommandOptions;
            }

        };
    }

    /**
     * Switch all mock objects to replay mode
     */
    private void replayMocks() {
        EasyMock.replay(mMockConfigFactory);
    }

    /**
     * Verify all mock objects
     */
    private void verifyMocks() {
        EasyMock.verify(mMockConfigFactory);
    }

    /**
     * Test {@link CommandScheduler#run()} when no configs have been added
     */
    public void testRun_empty() throws InterruptedException {
        mMockManager.setNumDevices(1);
        replayMocks();
        mScheduler.start();
        // wait for thread to start
        while (!mScheduler.isAlive()) {
            Thread.sleep(10);
        }
        mScheduler.shutdown();
        // expect run not to block
        mScheduler.join();
        assertEquals("allocated device was not returned to queue", 1,
                mMockManager.getAvailableDevices().size());
        verifyMocks();
        assertEquals(0, mMockInvocation.invokeCount());
    }

    /**
     * Test {@link CommandScheduler#addConfig(String[])} when passed invalid arguments.
     */
    public void testAddConfig_invalidConfig() throws ConfigurationException {
        String[] args = new String[] {};
        EasyMock.expect(mMockConfigFactory.createConfigurationFromArgs(EasyMock.eq(args),
                (CommandOptions)EasyMock.anyObject())).andThrow(new ConfigurationException(""));
        mMockConfigFactory.printHelp(EasyMock.eq(args), EasyMock.eq(System.out),
                EasyMock.eq(CommandOptions.class));
        replayMocks();
        mScheduler.addConfig(args);
        verifyMocks();
        assertEquals(0, mMockInvocation.invokeCount());
    }

    /**
     * Test {@link CommandScheduler#addConfig(String[])} when help mode is specified
     */
    public void testAddConfig_configHelp() throws ConfigurationException {
        String[] args = new String[] {};
        mCommandOptions.setHelpMode(true);
        EasyMock.expect(mMockConfigFactory.createConfigurationFromArgs(EasyMock.eq(args),
                (CommandOptions)EasyMock.anyObject())).andReturn(mMockConfiguration);
        mMockConfigFactory.printHelp(EasyMock.eq(args), EasyMock.eq(System.out),
                EasyMock.eq(CommandOptions.class));
        replayMocks();
        mScheduler.addConfig(args);
        verifyMocks();
        assertEquals(0, mMockInvocation.invokeCount());
    }


    /**
     * Test {@link CommandScheduler#run()} when one config has been added
     */
    public void testRun_oneConfig() throws Exception {
        String[] args = new String[] {};
        mMockManager.setNumDevices(2);
        EasyMock.expect(mMockConfigFactory.createConfigurationFromArgs(EasyMock.eq(args),
                (CommandOptions)EasyMock.anyObject())).andReturn(mMockConfiguration).times(2);
        replayMocks();
        mScheduler.addConfig(args);
        mScheduler.start();
        // wait for thread to start
        while (mMockInvocation.invokeCount() == 0) {
            Thread.sleep(10);
        }
        mScheduler.shutdown();
        mScheduler.join();
        assertEquals("allocated device was not returned to queue", 2,
                mMockManager.getAvailableDevices().size());
        verifyMocks();
    }

    /**
     * Test{@link CommandScheduler#run()} when config is matched to a specific device serial number
     * <p/>
     * Adds two configs to run, and verify they both run on one device
     */
    public void testRun_configSerial() throws Exception {
        String[] args = new String[] {};
        mMockManager.setNumDevices(2);
        EasyMock.expect(mMockConfigFactory.createConfigurationFromArgs(EasyMock.eq(args),
                (CommandOptions)EasyMock.anyObject())).andReturn(mMockConfiguration).times(4);
        // allocate and free a device to get its serial
        ITestDevice dev = mMockManager.allocateDevice();
        mCommandOptions.addSerial(dev.getSerialNumber());
        mMockInvocation.setExpectedSerial(dev.getSerialNumber());
        replayMocks();
        mScheduler.addConfig(args);
        mScheduler.addConfig(args);
        mMockManager.freeDevice(dev, FreeDeviceState.AVAILABLE);

        mScheduler.start();
        // wait for invocations to complete
        while (mMockInvocation.invokeCount() < 2) {
            Thread.sleep(10);
        }
        mScheduler.shutdown();
        mScheduler.join();
        assertEquals("allocated device was not returned to queue", 2,
                mMockManager.getAvailableDevices().size());
        assertTrue(mMockInvocation.matchedExpectedDevice());
        verifyMocks();
    }

    /**
     * Test{@link CommandScheduler#run()} when config is matched to a not specific device serial
     * number.
     * <p/>
     * Adds two configs to run, and verify they both run on the other device
     */
    public void testRun_configNotSerial() throws Exception {
        String[] args = new String[] {};
        mMockManager.setNumDevices(2);
        EasyMock.expect(mMockConfigFactory.createConfigurationFromArgs(EasyMock.eq(args),
                (CommandOptions)EasyMock.anyObject())).andReturn(mMockConfiguration).times(4);
        // allocate and free a device to get its serial
        ITestDevice dev = mMockManager.allocateDevice();
        mCommandOptions.addNotSerial(dev.getSerialNumber());
        ITestDevice expectedDevice = mMockManager.allocateDevice();
        mMockInvocation.setExpectedSerial(expectedDevice.getSerialNumber());
        replayMocks();
        mScheduler.addConfig(args);
        mScheduler.addConfig(args);
        mMockManager.freeDevice(dev, FreeDeviceState.AVAILABLE);
        mMockManager.freeDevice(expectedDevice, FreeDeviceState.AVAILABLE);
        mScheduler.start();
        // wait for thread to start
        while (mMockInvocation.invokeCount() < 2) {
            Thread.sleep(10);
        }
        mScheduler.shutdown();
        mScheduler.join();
        assertEquals("allocated device was not returned to queue", 2,
                mMockManager.getAvailableDevices().size());
        assertTrue(mMockInvocation.matchedExpectedDevice());
        verifyMocks();
    }

    private static class MockInvocation implements ITestInvocation {

        private int mInvokeCount = 0;
        private String mExpectedSerial = null;
        private boolean mMatched = true;

        public MockInvocation() {

        }

        public void setExpectedSerial(String expectedDeviceSerial) {
            mExpectedSerial  = expectedDeviceSerial;
        }

        /**
         * {@inheritDoc}
         */
        public void invoke(ITestDevice device, IConfiguration config)
                throws DeviceNotAvailableException {
            mInvokeCount++;
            if (mExpectedSerial != null && !device.getSerialNumber().equals(mExpectedSerial)) {
                mMatched = false;
            }
        }

        public int invokeCount() {
            return mInvokeCount;
        }

        public boolean matchedExpectedDevice() {
            return mMatched ;
        }
    }
}
