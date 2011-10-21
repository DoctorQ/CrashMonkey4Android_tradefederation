/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.tradefed.targetprep;

import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.util.IRunUtil;

import junit.framework.TestCase;

import org.easymock.EasyMock;

public class DeviceBatteryLevelCheckerTest extends TestCase {
    private DeviceBatteryLevelChecker mChecker = null;
    ITestDevice mFakeDevice = null;

    private static final String BATTERY_TEMPLATE = "Current Battery Service state:\n" +
            "  AC powered: false\n" +
            "  USB powered: true\n" +
            "  status: 2\n" +
            "  health: 2\n" +
            "  present: true\n" +
            "  level: %d\n" +
            "  scale: 100\n" +
            "  voltage:3400\n" +
            "  temperature: 250\n" +
            "  technology: Li-ion\n";

    private String battLevelString(int level) {
        return String.format(BATTERY_TEMPLATE, level);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mChecker = new DeviceBatteryLevelChecker() {
            @Override
            IRunUtil getRunUtil() {
                return EasyMock.createNiceMock(IRunUtil.class);
            }
        };
        mFakeDevice = EasyMock.createStrictMock(ITestDevice.class);
        EasyMock.expect(mFakeDevice.getSerialNumber()).andStubReturn("SERIAL");
    }

    public void testNull() throws Exception {
        EasyMock.expect(mFakeDevice.executeShellCommand("dumpsys battery"))
                .andReturn(null);
        EasyMock.replay(mFakeDevice);

        mChecker.setUp(mFakeDevice, null);
        // expect this to return immediately without throwing an exception.  Should log a warning.
        EasyMock.verify(mFakeDevice);
    }

    public void testNormal() throws Exception {
        EasyMock.expect(mFakeDevice.executeShellCommand("dumpsys battery"))
                .andReturn(battLevelString(45));
        EasyMock.replay(mFakeDevice);

        mChecker.setUp(mFakeDevice, null);
        EasyMock.verify(mFakeDevice);
    }

    public void testLow() throws Exception {
        EasyMock.expect(mFakeDevice.executeShellCommand("dumpsys battery"))
                .andReturn(battLevelString(5));
        EasyMock.expect(mFakeDevice.executeShellCommand("dumpsys battery"))
                .andReturn(battLevelString(20));
        EasyMock.expect(mFakeDevice.executeShellCommand("dumpsys battery"))
                .andReturn(battLevelString(50));
        EasyMock.expect(mFakeDevice.executeShellCommand("dumpsys battery"))
                .andReturn(battLevelString(90));
        EasyMock.replay(mFakeDevice);

        mChecker.setUp(mFakeDevice, null);
        EasyMock.verify(mFakeDevice);
    }
}

