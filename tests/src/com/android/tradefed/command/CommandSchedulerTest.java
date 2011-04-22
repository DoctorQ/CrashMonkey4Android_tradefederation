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
import com.android.tradefed.device.IDeviceManager.FreeDeviceState;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.MockDeviceManager;
import com.android.tradefed.invoker.IRescheduler;
import com.android.tradefed.invoker.ITestInvocation;

import org.easymock.EasyMock;

import java.lang.Thread.UncaughtExceptionHandler;

import junit.framework.Assert;
import junit.framework.TestCase;

/**
 * Unit tests for {@link CommandScheduler}.
 */
public class CommandSchedulerTest extends TestCase {

    private CommandScheduler mScheduler;
    private MockNotifyingInvocation mMockInvocation;
    private MockDeviceManager mMockManager;
    private IConfigurationFactory mMockConfigFactory;
    private IConfiguration mMockConfiguration;
    private CommandOptions mCommandOptions;
    private DeviceSelectionOptions mDeviceOptions;

    /**
     * A helper mock {@link ITestInvocation} that will notify listeners when invoke has been called
     * the requested number of times
     */
    private static class MockNotifyingInvocation implements ITestInvocation {
        private int mExpectedCalls = 0;
        protected int mNumCalls = 0;
        private RuntimeException mInvokeThrowable;

        MockNotifyingInvocation() {
        }

        public void setExpectedCalls(int expectedCalls) {
            mExpectedCalls = expectedCalls;
        }

        public void setInvokeException(RuntimeException e) {
            mInvokeThrowable = e;
        }

        @Override
        public void invoke(ITestDevice device, IConfiguration config, IRescheduler rescheduler)
                throws DeviceNotAvailableException {
            mNumCalls++;
            if (mNumCalls >= mExpectedCalls) {
                synchronized (this) {
                    notify();
                }
            }
            if (mInvokeThrowable != null) {
                throw mInvokeThrowable;
            }
        }

        /**
         * Block for the expected number of invocation calls
         * @throws InterruptedException
         * @throws AssertionError if expected number of invoke calls was not reached within 1 sec
         */
        public synchronized void waitForExpectedCalls() throws InterruptedException {
            wait(1000);
        }

        public void assertExpectedCalls() {
            Assert.assertEquals("invoke not called expected number of times", mExpectedCalls,
                    mNumCalls);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mMockInvocation = new MockNotifyingInvocation();
        mMockManager = new MockDeviceManager(0);
        mMockConfigFactory = EasyMock.createMock(IConfigurationFactory.class);
        mMockConfiguration = EasyMock.createMock(IConfiguration.class);
        mCommandOptions = new CommandOptions();
        mDeviceOptions = new DeviceSelectionOptions();

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
            long getCommandPollTimeMs() {
                return 20;
            }
        };
    }

    /**
     * Switch all mock objects to replay mode
     */
    private void replayMocks(Object... additionalMocks) {
        EasyMock.replay(mMockConfigFactory, mMockConfiguration);
        for (Object mock : additionalMocks) {
            EasyMock.replay(mock);
        }
    }

    /**
     * Verify all mock objects
     */
    private void verifyMocks() {
        EasyMock.verify(mMockConfigFactory, mMockConfiguration);
        mMockInvocation.assertExpectedCalls();
        mMockManager.assertDevicesFreed();
    }

    /**
     * Test {@link CommandScheduler#run()} when no configs have been added
     */
    public void testRun_empty() throws InterruptedException {
        mMockManager.setNumDevices(1);
        replayMocks();
        mScheduler.start();
        while (!mScheduler.isAlive()) {
            Thread.sleep(10);
        }
        mScheduler.shutdown();
        // expect run not to block
        mScheduler.join();
        verifyMocks();
    }

    /**
     * Test {@link CommandScheduler#addCommand(String[])} when passed invalid arguments.
     */
    public void testAddConfig_invalidConfig() throws ConfigurationException {
        String[] args = new String[] {"arg"};
        EasyMock.expect(
                mMockConfigFactory.createConfigurationFromArgs(EasyMock.aryEq(args))).andThrow(
                new ConfigurationException(""));
        mMockConfigFactory.printHelpForConfig(EasyMock.aryEq(args), EasyMock.eq(System.out));
        replayMocks();
        mScheduler.addCommand(args);
        verifyMocks();
    }

    /**
     * Test {@link CommandScheduler#addCommand(String[])} when help mode is specified
     */
    public void testAddConfig_configHelp() throws ConfigurationException {
        String[] args = new String[] {};
        mCommandOptions.setHelpMode(true);
        setCreateConfigExpectations(args, 1);
        mCommandOptions.setHelpMode(true);
        // expect
        mMockConfigFactory.printHelpForConfig(EasyMock.aryEq(args), EasyMock.eq(System.out));
        replayMocks();
        mScheduler.addCommand(args);
        verifyMocks();
    }

    /**
     * Test {@link CommandScheduler#run()} when one config has been added
     */
    public void testRun_oneConfig() throws Exception {
        String[] args = new String[] {};
        mMockManager.setNumDevices(2);
        setCreateConfigExpectations(args, 1);
        mMockInvocation.setExpectedCalls(1);
        replayMocks();
        mScheduler.addCommand(args);
        mScheduler.start();
        mMockInvocation.waitForExpectedCalls();
        mScheduler.shutdown();
        mScheduler.join();
        verifyMocks();
    }

    /**
     * Test {@link CommandScheduler#run()} when one config has been added in a loop
     */
    public void testRun_oneConfigLoop() throws Exception {
        String[] args = new String[] {};
        // track if exception occurs on scheduler thread
        UncaughtExceptionHandler defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        try {
            ExceptionTracker tracker = new ExceptionTracker();
            Thread.setDefaultUncaughtExceptionHandler(tracker);
            mMockManager.setNumDevices(1);
            // config should only be created three times
            setCreateConfigExpectations(args, 3);
            mCommandOptions.setLoopMode(true);
            mCommandOptions.setMinLoopTime(0);
            // wait for invocation to be executed twice
            mMockInvocation.setExpectedCalls(2);
            replayMocks();
            mScheduler.addCommand(args);
            mScheduler.start();
            mMockInvocation.waitForExpectedCalls();
            mScheduler.shutdown();
            mScheduler.join();
            verifyMocks();
            assertNull("exception occurred on background thread!", tracker.mThrowable);
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(defaultHandler);
        }
    }

    class ExceptionTracker implements UncaughtExceptionHandler {

        private Throwable mThrowable = null;

        /**
         * {@inheritDoc}
         */
        @Override
        public void uncaughtException(Thread t, Throwable e) {
            e.printStackTrace();
            mThrowable  = e;
        }
    }

    /**
     * Verify that scheduler goes into shutdown mode when a {@link FatalHostError} is thrown.
     */
    public void testRun_fatalError() throws Exception {
        mMockInvocation.setExpectedCalls(1);
        mMockInvocation.setInvokeException(new FatalHostError("error"));
        String[] args = new String[] {};
        mMockManager.setNumDevices(2);
        setCreateConfigExpectations(args, 1);
        replayMocks();
        mScheduler.addCommand(args);
        mScheduler.start();
        mMockInvocation.waitForExpectedCalls();
        // no need to call shutdown explicitly - scheduler should shutdown by itself
        mScheduler.join();
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
        setCreateConfigExpectations(args, 2);
        // allocate and free a device to get its serial
        ITestDevice dev = mMockManager.allocateDevice();
        mDeviceOptions.addSerial(dev.getSerialNumber());
        mMockInvocation.setExpectedCalls(1);
        replayMocks();
        mScheduler.addCommand(args);
        mScheduler.addCommand(args);
        mMockManager.freeDevice(dev, FreeDeviceState.AVAILABLE);

        mScheduler.start();
        mMockInvocation.waitForExpectedCalls();
        mScheduler.shutdown();
        mScheduler.join();
        verifyMocks();
    }

    /**
     * Test{@link CommandScheduler#run()} when config is matched to a exclude specific device serial
     * number.
     * <p/>
     * Adds two configs to run, and verify they both run on the other device
     */
    public void testRun_configExcludeSerial() throws Exception {
        String[] args = new String[] {};
        mMockManager.setNumDevices(2);
        setCreateConfigExpectations(args, 2);
        // allocate and free a device to get its serial
        ITestDevice dev = mMockManager.allocateDevice();
        mDeviceOptions.addExcludeSerial(dev.getSerialNumber());
        ITestDevice expectedDevice = mMockManager.allocateDevice();
        mMockInvocation.setExpectedCalls(1);
        replayMocks();
        mScheduler.addCommand(args);
        mScheduler.addCommand(args);
        mMockManager.freeDevice(dev, FreeDeviceState.AVAILABLE);
        mMockManager.freeDevice(expectedDevice, FreeDeviceState.AVAILABLE);
        mScheduler.start();
        mMockInvocation.waitForExpectedCalls();
        mScheduler.shutdown();
        mScheduler.join();
        verifyMocks();
    }

    /**
     * Test {@link CommandScheduler#run()} when one config has been rescheduled
     */
    public void testRun_rescheduled() throws Exception {
        String[] args = new String[] {};
        mMockManager.setNumDevices(2);
        setCreateConfigExpectations(args, 1);
        final IConfiguration rescheduledConfig = EasyMock.createMock(IConfiguration.class);
        EasyMock.expect(rescheduledConfig.getCommandOptions()).andStubReturn(mCommandOptions);
        EasyMock.expect(rescheduledConfig.getDeviceSelectionOptions()).andStubReturn(
                mDeviceOptions);

        // customize the MockNotifyingInvocation to simulate a reschedule
        final MockNotifyingInvocation reschedulingInvocation = new MockNotifyingInvocation() {
            @Override
            public void invoke(ITestDevice device, IConfiguration config, IRescheduler rescheduler)
                    throws DeviceNotAvailableException {
                super.invoke(device, config, rescheduler);
                if (mNumCalls == 1) {
                    rescheduler.scheduleConfig(rescheduledConfig);
                    throw new DeviceNotAvailableException("not avail");
                }
            }
        };
        reschedulingInvocation.setExpectedCalls(2);

        CommandScheduler scheduler = new CommandScheduler() {
            @Override
            ITestInvocation createRunInstance() {
                return reschedulingInvocation;
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
            long getCommandPollTimeMs() {
                return 20;
            }
        };

        replayMocks(rescheduledConfig);
        scheduler.addCommand(args);
        scheduler.start();
        reschedulingInvocation.waitForExpectedCalls();
        scheduler.shutdown();
        scheduler.join();

        EasyMock.verify(mMockConfigFactory, mMockConfiguration);
        reschedulingInvocation.assertExpectedCalls();
    }

    /**
     * Test {@link CommandScheduler#shutdown()} when no devices are available.
     */
    public void testShutdown() throws Exception {
        mMockManager.setNumDevices(0);
        mScheduler.start();
        while (!mScheduler.isAlive()) {
            Thread.sleep(10);
        }
        // hack - sleep a bit more to ensure allocateDevices is called
        Thread.sleep(50);
        mScheduler.shutdown();
        mScheduler.join();
        // test will hang if not successful
    }

    /**
     * Set EasyMock expectations for a create configuration call.
     */
    private void setCreateConfigExpectations(String[] args, int times)
            throws ConfigurationException {
        EasyMock.expect(
                mMockConfigFactory.createConfigurationFromArgs(EasyMock.eq(args)))
                .andReturn(mMockConfiguration)
                .times(times);
        EasyMock.expect(mMockConfiguration.getCommandOptions()).andStubReturn(mCommandOptions);
        EasyMock.expect(mMockConfiguration.getDeviceSelectionOptions()).andStubReturn(
                mDeviceOptions);
    }
}
