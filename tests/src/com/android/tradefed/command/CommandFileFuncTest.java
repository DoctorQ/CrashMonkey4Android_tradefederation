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
import com.android.tradefed.device.DeviceManager;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.IDeviceManager;
import com.android.tradefed.device.IDeviceRecovery;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.ITestInvocation;
import com.android.tradefed.log.LogRegistry;
import com.android.tradefed.log.StubLogRegistry;
import com.android.tradefed.util.RunUtil;

import org.easymock.EasyMock;

import java.io.BufferedReader;
import java.io.File;
import java.io.StringReader;
import java.util.Collection;
import java.util.concurrent.LinkedBlockingQueue;

import junit.framework.TestCase;

/**
 * Longer running test for {@link CommandFile}
 */
public class CommandFileFuncTest extends TestCase {

    /** the {@link CommandFile} under test, with all dependencies mocked out */
    private CommandFile mCommandFile;
    private MeasuredInvocation mMockTestInvoker;
    private IDeviceManager mMockDeviceManager;
    private IConfiguration mSlowConfig;
    private IConfiguration mFastConfig;
    private IConfigurationFactory mMockConfigFactory;
    private IDeviceRecovery mMockRecovery;
    private String mMockFileData = "";

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mSlowConfig = EasyMock.createNiceMock(IConfiguration.class);
        mFastConfig = EasyMock.createNiceMock(IConfiguration.class);
        mMockDeviceManager = new MockDeviceManager(3);
        mMockTestInvoker = new MeasuredInvocation();
        mMockConfigFactory = EasyMock.createNiceMock(IConfigurationFactory.class);

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

            @Override
            protected void setLogRegistry() {
                // do nothing
            }
        };
    }

    /**
     * Test config priority scheduling. Verifies that configs are prioritized according to their
     * total run time.
     * <p/>
     * This test continually executes two configs in loop mode. One config executes quickly (ie
     * "fast config"). The other config (ie "slow config") takes ~ 2 * fast config time to execute.
     * <p/>
     * The run is stopped after the slow config is executed 20 times. At the end of the test, it is
     * expected that "fast config" has executed roughly twice as much as the "slow config".
     */
    public void testRun_scheduling() throws ConfigurationException, DeviceNotAvailableException {
        mMockFileData = "fastConfig\nslowConfig";
        String[] fastConfigArgs = new String[] {"fastConfig"};
        String[] slowConfigArgs = new String[] {"slowConfig"};

        EasyMock.expect(
                mMockConfigFactory.createConfigurationFromArgs(EasyMock.aryEq(fastConfigArgs)))
                .andReturn(mFastConfig).anyTimes();
        EasyMock.expect(mFastConfig.getDeviceRecovery()).andStubReturn(mMockRecovery);
        EasyMock.expect(
                mMockConfigFactory.createConfigurationFromArgs(EasyMock.aryEq(slowConfigArgs)))
                .andReturn(mSlowConfig).anyTimes();
        EasyMock.expect(mSlowConfig.getDeviceRecovery()).andReturn(mMockRecovery).times(20);
        // throw an exception to stop running after 20 slow iterations.
        EasyMock.expect(mSlowConfig.getDeviceRecovery()).andThrow(new RuntimeException());

        EasyMock.replay(mFastConfig, mSlowConfig, mMockConfigFactory);
        mCommandFile.setConfigFile(new File("tmp"));
        // put configs immediately back into queue after invocation is started
        mCommandFile.setMinLoopTime(0);
        mCommandFile.setLoopMode(true);
        mCommandFile.run(new String[] {});
        System.out.println(String.format("fast times %d slow times %d",
                mMockTestInvoker.mFastCount, mMockTestInvoker.mSlowCount));
        // assert that fast config has executed roughly twice as much as slow config. Allow for
        // some variance since the execution time of each config (governed via Thread.sleep) will
        // not be 100% accurate
        assertEquals(mMockTestInvoker.mFastCount, mMockTestInvoker.mSlowCount * 2, 5);
    }

    /**
     * A {@link IDeviceManager} that simulates the resource allocation of {@link DeviceManager}
     * for a configurable set of devices.
     */
    private static class MockDeviceManager implements IDeviceManager {

        LinkedBlockingQueue<ITestDevice> mDeviceQueue = new LinkedBlockingQueue<ITestDevice>();

        MockDeviceManager(int numDevices) {
            // EasyMock.expect(mMockDevice.getSerialNumber()).andStubReturn("serial");
            for (int i=0; i < numDevices; i++) {
                ITestDevice mockDevice = EasyMock.createNiceMock(ITestDevice.class);
                EasyMock.expect(mockDevice.getSerialNumber()).andStubReturn("serial" + i);
                EasyMock.replay(mockDevice);
                mDeviceQueue.add(mockDevice);
            }
        }

        /**
         * {@inheritDoc}
         */
        public void addFastbootListener(IFastbootListener listener) {
            // ignore
        }

        /**
         * {@inheritDoc}
         */
        public ITestDevice allocateDevice(IDeviceRecovery recovery) {
            try {
                return mDeviceQueue.take();
            } catch (InterruptedException e) {
                return null;
            }
        }

        /**
         * {@inheritDoc}
         */
        public ITestDevice allocateDevice(IDeviceRecovery recovery, long timeout) {
            throw new UnsupportedOperationException();
        }

        /**
         * {@inheritDoc}
         */
        public void freeDevice(ITestDevice device, FreeDeviceState state) {
            mDeviceQueue.add(device);
        }

        /**
         * {@inheritDoc}
         */
        public void removeFastbootListener(IFastbootListener listener) {
            // ignore
        }

        /**
         * {@inheritDoc}
         */
        public void terminate() {
            // ignore
        }

        /**
         * {@inheritDoc}
         */
        public Collection<String> getAllocatedDevices() {
            return null;
        }

        /**
         * {@inheritDoc}
         */
        public Collection<String> getAvailableDevices() {
            return null;
        }

        /**
         * {@inheritDoc}
         */
        public Collection<String> getUnavailableDevices() {
            return null;
        }
    }

    private class MeasuredInvocation implements ITestInvocation {
        Integer mSlowCount = 0;
        Integer mFastCount = 0;

        public void invoke(ITestDevice device, IConfiguration config)
                throws DeviceNotAvailableException {
            if (config.equals(mSlowConfig)) {
                // sleep for 2 * fast config time
                RunUtil.getInstance().sleep(200);
                synchronized (mSlowCount) {
                    mSlowCount++;
                }
            } else if (config.equals(mFastConfig)) {
                RunUtil.getInstance().sleep(100);
                synchronized (mFastCount) {
                    mFastCount++;
                }
            } else {
                throw new IllegalArgumentException("unknown config");
            }
        }
    }
}
