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
import com.android.tradefed.device.IDeviceManager;
import com.android.tradefed.device.IDeviceRecovery;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.IDeviceManager.FreeDeviceState;
import com.android.tradefed.invoker.ITestInvocation;
import com.android.tradefed.log.LogRegistry;
import com.android.tradefed.log.StubLogRegistry;

import org.easymock.EasyMock;

import java.io.BufferedReader;
import java.io.File;
import java.io.StringReader;

import junit.framework.TestCase;

/**
 * Unit tests for {@link CommandFile}
 */
public class CommandFileTest extends TestCase {

    /** the {@link CommandFile} under test, with all dependencies mocked out */
    private CommandFile mCommandFile;
    private ITestInvocation mMockTestInvoker;
    private IDeviceManager mMockDeviceManager;
    private IConfiguration mMockConfiguration;
    private IConfigurationFactory mMockConfigFactory;
    private ITestDevice mMockDevice;
    private IDeviceRecovery mMockRecovery;
    private String mMockFileData = "";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mMockTestInvoker = EasyMock.createMock(ITestInvocation.class);
        mMockConfiguration = EasyMock.createMock(IConfiguration.class);
        mMockConfigFactory = EasyMock.createMock(IConfigurationFactory.class);
        mMockRecovery = EasyMock.createMock(IDeviceRecovery.class);
        mMockDevice = EasyMock.createMock(ITestDevice.class);
        mMockDeviceManager =  EasyMock.createMock(IDeviceManager.class);

        EasyMock.expect(mMockDevice.getSerialNumber()).andStubReturn("serial");

        mCommandFile = new CommandFile() {
            @Override
            protected ITestInvocation createRunInstance() {
                return mMockTestInvoker;
            }

            @Override
            protected IDeviceManager getDeviceManager() {
                return mMockDeviceManager;
            }

            @Override
            protected IConfigurationFactory getConfigFactory() {
                return mMockConfigFactory;
            }

            @Override
            BufferedReader createConfigFileReader(File file) {
               return new BufferedReader(new StringReader(mMockFileData));
            }

            @Override
            protected LogRegistry getLogRegistry() {
                return new StubLogRegistry();
            }
        };
    }

    /** Set all mock objects to replay mode */
    private void replayMocks() {
        EasyMock.replay(mMockConfigFactory, mMockConfiguration,
                mMockTestInvoker, mMockDevice, mMockDeviceManager);
    }

    /** Verify all mock objects */
    private void verifyMocks() {
        EasyMock.verify(mMockConfigFactory, mMockConfiguration,
                mMockTestInvoker, mMockDeviceManager);
    }

    /**
     * Test for a normal run with single config that proceeds without error.
     */
    public void testRun_singleConfig() throws DeviceNotAvailableException, ConfigurationException {
        // inject mock file data
        mMockFileData = "  #Comment followed by blank line\n \n--foo  config";
        String[] expectedArgs = new String[] {"--foo", "config"};
        EasyMock.expect(
                mMockConfigFactory.createConfigurationFromArgs(EasyMock.aryEq(expectedArgs)))
                .andReturn(mMockConfiguration);
        EasyMock.expect(mMockConfiguration.getDeviceRecovery()).andReturn(mMockRecovery);
        EasyMock.expect(mMockDeviceManager.allocateDevice(mMockRecovery)).andReturn(mMockDevice);
        // expect doRun is invoked with the device
        mMockTestInvoker.invoke(mMockDevice, mMockConfiguration);
        mMockDeviceManager.freeDevice(mMockDevice, FreeDeviceState.AVAILABLE);
        mMockDeviceManager.terminate();
        // switch mock objects to verify mode
        replayMocks();
        mCommandFile.setConfigFile(new File("tmp"));
        mCommandFile.run(new String[] {});
        verifyMocks();
    }

    /**
     * Test run when --file is not specified
     * <p/>
     * Expect run to not proceed, and exception silently handled
     */
    public void testRun_missingfile() {
        replayMocks();
        mCommandFile.run(new String[] {});
        verifyMocks();
    }

    /**
     * Test that scenario where configuration cannot be loaded.
     * <p/>
     * Expect exception to be captured gracefully and run to not proceed.
     */
    public void testRun_configException() throws ConfigurationException {
        mMockFileData = "foo";
        EasyMock.expect(
                mMockConfigFactory.createConfigurationFromArgs((String[])EasyMock.anyObject()))
                .andThrow(new ConfigurationException(""));
        mMockDeviceManager.terminate();
        replayMocks();
        mCommandFile.setConfigFile(new File("tmp"));
        mCommandFile.run(new String[] {});
        verifyMocks();
    }

}
