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

import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.device.IDeviceManager;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.ITestInvocation;
import com.android.tradefed.log.StdoutLogger;

import org.easymock.EasyMock;

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

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mMockTestInvoker = EasyMock.createMock(ITestInvocation.class);
        mMockDeviceManager = EasyMock.createMock(IDeviceManager.class);
        mMockConfiguration = EasyMock.createMock(IConfiguration.class);
        mMockDevice = EasyMock.createMock(ITestDevice.class);

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
            IConfiguration createConfiguration(String[] args) {
                return mMockConfiguration;
            }
        };
    }

    /**
     * Test for a normal run that proceeds without error.
     */
    public void testRun() throws DeviceNotAvailableException, ConfigurationException {
        // expect getLogOutput to be called
        EasyMock.expect(mMockConfiguration.getLogOutput()).andReturn(new StdoutLogger());
        // expect to be asked for device to connect to and return the mock device
        EasyMock.expect(mMockDeviceManager.allocateDevice()).andReturn(mMockDevice);
        // expect doRun is invoked with the device
        mMockTestInvoker.invoke(mMockDevice, mMockConfiguration);
        // switch mock objects to verify mode
        EasyMock.replay(mMockConfiguration);
        EasyMock.replay(mMockDeviceManager);
        EasyMock.replay(mMockTestInvoker);
        mCommand.run(new String[] {});
    }

    /**
     * Test that scenario where device cannot be connected.
     * <p/>
     * Expect exception to be captured gracefully and run to not proceed.
     */
    public void testRun_noDevice() throws DeviceNotAvailableException, ConfigurationException {
        // expect getLogOutput to be called
        EasyMock.expect(mMockConfiguration.getLogOutput()).andReturn(new StdoutLogger());
        // expect to be asked for device to connect to and return the mock device
        EasyMock.expect(mMockDeviceManager.allocateDevice()).andThrow(
                 new DeviceNotAvailableException("no device"));

        // switch mock objects to verify mode
        EasyMock.replay(mMockConfiguration);
        EasyMock.replay(mMockDeviceManager);
        // expect TestInvocation to NOT be called
        EasyMock.replay(mMockTestInvoker);
        mCommand.run(new String[] {});
    }

    /**
     * Test that scenario where configuration cannot be loaded.
     * <p/>
     * Expect exception to be captured gracefully and run to not proceed.
     */
    public void testRun_configException() {
        Command command = new Command() {
            @Override
            IConfiguration createConfiguration(String[] args) throws ConfigurationException {
                throw new ConfigurationException("error");
            }
        };
        // switch mock objects to verify mode
        // expect allocateDevices to not be called
        EasyMock.replay(mMockDeviceManager);
        // expect RunInstance to NOT be called
        EasyMock.replay(mMockTestInvoker);
        command.run(new String[] {});
    }
}
