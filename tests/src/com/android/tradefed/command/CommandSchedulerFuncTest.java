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
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IConfigurationFactory;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.IDeviceManager;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.MockDeviceManager;
import com.android.tradefed.invoker.ITestInvocation;
import com.android.tradefed.util.RunUtil;

import org.easymock.EasyMock;

import junit.framework.TestCase;

/**
 * Longer running test for {@link CommandScheduler}
 */
public class CommandSchedulerFuncTest extends TestCase {

    /** the {@link CommandScheduler} under test, with all dependencies mocked out */
    private CommandScheduler mCommandScheduler;
    private MeasuredInvocation mMockTestInvoker;
    private IDeviceManager mMockDeviceManager;
    private IConfiguration mSlowConfig;
    private IConfiguration mFastConfig;
    private IConfigurationFactory mMockConfigFactory;
    private CommandOptions mCommandOptions;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mSlowConfig = EasyMock.createNiceMock(IConfiguration.class);
        mFastConfig = EasyMock.createNiceMock(IConfiguration.class);
        mMockDeviceManager = new MockDeviceManager(3);
        mMockTestInvoker = new MeasuredInvocation();
        mMockConfigFactory = EasyMock.createMock(IConfigurationFactory.class);
        mCommandOptions = new CommandOptions();
        mCommandOptions.setLoopMode(true);
        mCommandOptions.setMinLoopTime(0);

        mCommandScheduler = new CommandScheduler() {
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
            CommandOptions createCommandOptions() {
                return mCommandOptions;
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
    public void testRun_scheduling() throws Exception {
        String[] fastConfigArgs = new String[] {"fastConfig"};
        String[] slowConfigArgs = new String[] {"slowConfig"};

        EasyMock.expect(
                mMockConfigFactory.createConfigurationFromArgs(EasyMock.aryEq(fastConfigArgs),
                        (CommandOptions)EasyMock.anyObject()))
                .andReturn(mFastConfig).anyTimes();
        EasyMock.expect(
                mMockConfigFactory.createConfigurationFromArgs(EasyMock.aryEq(slowConfigArgs),
                        (CommandOptions)EasyMock.anyObject()))
                .andReturn(mSlowConfig).anyTimes();

        EasyMock.replay(mFastConfig, mSlowConfig, mMockConfigFactory);

        mCommandScheduler.addConfig(fastConfigArgs);
        mCommandScheduler.addConfig(slowConfigArgs);
        mCommandScheduler.start();

        synchronized (mMockTestInvoker) {
            mMockTestInvoker.wait();
        }
        mCommandScheduler.shutdown();
        mCommandScheduler.join();

        System.out.println(String.format("fast times %d slow times %d",
                mMockTestInvoker.mFastCount, mMockTestInvoker.mSlowCount));
        // assert that fast config has executed roughly twice as much as slow config. Allow for
        // some variance since the execution time of each config (governed via Thread.sleep) will
        // not be 100% accurate
        assertEquals(mMockTestInvoker.mFastCount, mMockTestInvoker.mSlowCount * 2, 5);
    }

    private class MeasuredInvocation implements ITestInvocation {
        Integer mSlowCount = 0;
        Integer mFastCount = 0;
        Integer mSlowCountLimit = 20;

        public void invoke(ITestDevice device, IConfiguration config)
                throws DeviceNotAvailableException {
            if (config.equals(mSlowConfig)) {
                // sleep for 2 * fast config time
                RunUtil.getInstance().sleep(200);
                synchronized (mSlowCount) {
                    mSlowCount++;
                }
                if (mSlowCount >= mSlowCountLimit) {
                    synchronized (this) {
                        notify();
                    }
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
