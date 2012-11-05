/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache
 * License, Version 2.0 (the "License");
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

package com.android.tradefed.device;

import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.util.StreamUtil;

import junit.framework.TestCase;

import org.easymock.EasyMock;

import java.io.IOException;

/**
 * Unit tests for {@link LargeOutputReceiver}
 */
public class LargeOutputReceiverTest extends TestCase {
    private ITestDevice mTestDevice;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mTestDevice = EasyMock.createMock(ITestDevice.class);
        EasyMock.expect(mTestDevice.getSerialNumber()).andReturn("serial").anyTimes();
        EasyMock.expect(mTestDevice.getBuildId()).andReturn("id").anyTimes();
    }

    /**
     * Test the log file size limiting.
     */
    public void testMaxFileSizeHelper() throws IOException {
        final String input1 = "this is the output of greater than 10 bytes.";
        final String input2 = "this is the second output of greater than 10 bytes.";
        final String input3 = "<10bytes";
        EasyMock.replay(mTestDevice);

        LargeOutputReceiver helper = new LargeOutputReceiver("command", "serial", 10);

        try {
            helper.createTmpFile();
            byte[] inputData1 = input1.getBytes();
            // add log data > maximum. This will trigger a log swap, where inputData1 will be moved
            // to the backup log file
            helper.addOutput(inputData1, 0, inputData1.length);
            // inject the second input data > maximum. This will trigger another log swap, that will
            // discard inputData. the backup log file will have inputData2, and the current log file
            // will be empty
            byte[] inputData2 = input2.getBytes();
            helper.addOutput(inputData2, 0, inputData2.length);
            // inject log data smaller than max log data - that will not trigger a log swap. The
            // backup log file should contain inputData2, and the current should contain inputData3
            byte[] inputData3 = input3.getBytes();
            helper.addOutput(inputData3, 0, inputData3.length);

            InputStreamSource iss = helper.getData();
            String actualString;
            try {
                actualString = StreamUtil.getStringFromStream(iss.createInputStream());
            } finally {
                iss.cancel();
            }
            // verify that data from both the backup log file (input2) and current log file
            // (input3) is retrieved
            assertFalse(actualString.contains(input1));
            assertTrue(actualString.contains(input2));
            assertTrue(actualString.contains(input3));
        } finally {
            helper.cancel();
            helper.delete();
        }
    }
}
