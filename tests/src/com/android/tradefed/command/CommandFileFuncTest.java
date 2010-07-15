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
import com.android.tradefed.invoker.ITestInvocation;
import com.android.tradefed.log.LogRegistry;
import com.android.tradefed.log.StubLogRegistry;

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
        // set up the mock device manager with more devices than configs to make comparison
        // accurate
        mMockDeviceManager = new MockDeviceManager(mMockDevice, 3);

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
                mMockTestInvoker, mMockDevice);
    }

    /** Verify all mock objects */
    private void verifyMocks() {
        EasyMock.verify(mMockConfigFactory, mMockConfiguration,
                mMockTestInvoker);
    }


    /**
     * Test config priority scheduling. Verifies that configs are prioritized according to their
     * total run time.
     * <p/>
     * This test continually executes two configs in loop mode. One config executes quickly (ie
     * "fast config"). The other config (ie "slow config") takes ~ 2 * fast config time to execute.
     * <p/>
     * The run is stopped after the slow config is executed 10 times. At the end of the test, it is
     * expected that "fast config" has executed roughly twice as much as the "slow config".
     */
    public void testRun_scheduling() throws ConfigurationException, DeviceNotAvailableException {
        mMockFileData = "fastConfig\nslowConfig";
        String[] fastConfigArgs = new String[] {"fastConfig"};
        String[] slowConfigArgs = new String[] {"slowConfig"};
        // used to store the number of times each config has run.
        final int[] runCounts = new int[2];
        final int FAST_CONFIG_INDEX = 0;
        final int SLOW_CONFIG_INDEX = 1;
        final IConfiguration slowConfig = EasyMock.createNiceMock(IConfiguration.class);
        EasyMock.expect(
                mMockConfigFactory.createConfigurationFromArgs(EasyMock.aryEq(fastConfigArgs)))
                .andReturn(mMockConfiguration).anyTimes();
        EasyMock.expect(mMockConfiguration.getDeviceRecovery()).andStubReturn(mMockRecovery);
        EasyMock.expect(
                mMockConfigFactory.createConfigurationFromArgs(EasyMock.aryEq(slowConfigArgs)))
                .andReturn(slowConfig).anyTimes();
        EasyMock.expect(slowConfig.getDeviceRecovery()).andReturn(mMockRecovery).times(10);
        EasyMock.expect(slowConfig.getDeviceRecovery()).andThrow(new RuntimeException());

        mMockTestInvoker.invoke(mMockDevice, mMockConfiguration);
        EasyMock.expectLastCall().andDelegateTo(new ITestInvocation() {
            public void invoke(ITestDevice device, IConfiguration config)
                    throws DeviceNotAvailableException {
                // sleep for small amount to simulate runtime
                try {
                    synchronized (runCounts) {
                        runCounts[FAST_CONFIG_INDEX]++;
                    }
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                }
            }
        }).anyTimes();

        mMockTestInvoker.invoke(mMockDevice, slowConfig);
        EasyMock.expectLastCall().andDelegateTo(new ITestInvocation() {
            public void invoke(ITestDevice device, IConfiguration config)
                    throws DeviceNotAvailableException {
                // sleep for small amount to simulate runtime
                try {
                    synchronized (runCounts) {
                        runCounts[SLOW_CONFIG_INDEX]++;
                    }
                    // sleep time should be more than two times fast configs time
                    Thread.sleep(210);
                } catch (InterruptedException e) {
                }
            }
        }).anyTimes();

        replayMocks();
        EasyMock.replay(slowConfig);
        mCommandFile.setConfigFile(new File("tmp"));
        // put configs immediately back into queue after invocation is started
        mCommandFile.setMinLoopTime(0);
        mCommandFile.setLoopMode(true);
        mCommandFile.run(new String[] {});
        System.out.println(String.format("fast times %d slow times %d",
                runCounts[FAST_CONFIG_INDEX], runCounts[SLOW_CONFIG_INDEX]));
        // assert that fast config has executed roughly twice as much as slow config. Allow for
        // some variance since the execution time of each config (governed via Thread.sleep) will
        // not be 100% accurate
        assertEquals(runCounts[FAST_CONFIG_INDEX], runCounts[SLOW_CONFIG_INDEX] * 2, 2);
        verifyMocks();
    }

    /**
     * A {@link IDeviceManager} that simulates the resource allocation of {@link DeviceManager}
     * for a configurable set of devices.
     */
    private static class MockDeviceManager implements IDeviceManager {

        LinkedBlockingQueue<ITestDevice> mDeviceQueue = new LinkedBlockingQueue<ITestDevice>();

        MockDeviceManager(ITestDevice mockDevice, int numDevices) {
            for (int i=0; i < numDevices; i++) {
                mDeviceQueue.add(mockDevice);
            }
        }

        /**
         * {@inheritDoc}
         */
        public void addFastbootListener(Object listener) {
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
        public void removeFastbootListener(Object listener) {
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
}
