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
package com.android.tradefed.result;

import com.android.tradefed.device.ITestDevice;

import junit.framework.TestCase;

import org.easymock.EasyMock;

import java.io.File;
import java.io.IOException;

/**
 * Unit tests for {@link DeviceFileReporter}
 */
public class DeviceFileReporterTest extends TestCase {
    DeviceFileReporter dfr = null;
    ITestDevice mDevice = null;
    ITestInvocationListener mListener = null;

    // Used to control what ISS is returned
    InputStreamSource mDfrIss = null;

    @Override
    public void setUp() throws Exception {
        mDevice = EasyMock.createMock(ITestDevice.class);
        mListener = EasyMock.createMock(ITestInvocationListener.class);
        dfr = new DeviceFileReporter(mDevice, mListener) {
            @Override
            InputStreamSource createIssForFile(File file) throws IOException {
                return mDfrIss;
            }
        };
    }

    public void testSimple() throws Exception {
        final String result = "/data/tombstones/tombstone_00\r\n";
        final String filename = "/data/tombstones/tombstone_00";
        dfr.addPatterns("/data/tombstones/*");

        EasyMock.expect(mDevice.executeShellCommand((String)EasyMock.anyObject()))
                .andReturn(result);
        // This gets passed verbatim to createIssForFile above
        EasyMock.expect(mDevice.pullFile(EasyMock.eq(filename))).andReturn(null);

        final String tombstone = "What do you want on your tombstone?";
        mDfrIss = new ByteArrayInputStreamSource(tombstone.getBytes());
        // FIXME: use captures here to make sure we get the string back out
        mListener.testLog(EasyMock.eq(filename), EasyMock.eq(LogDataType.UNKNOWN),
                EasyMock.eq(mDfrIss));

        replayMocks();
        dfr.run();
        verifyMocks();
    }

    /**
     * Make sure that we correctly handle the case where a file doesn't exist while matching the
     * exact name.
     * <p />
     * This verifies a fix for a bug where we would mistakenly treat the
     * "file.txt: No such file or directory" message as a filename.  This would happen when we tried
     * to match an exact filename that doesn't exist, rather than using a shell glob.
     */
    public void testNoExist() throws Exception {
        final String file = "/data/traces.txt";
        final String result = file + ": No such file or directory\r\n";
        dfr.addPatterns(file);

        EasyMock.expect(mDevice.executeShellCommand((String)EasyMock.anyObject()))
                .andReturn(result);

        replayMocks();
        dfr.run();
        // No pull attempt should happen
        verifyMocks();
    }

    public void testTwoFiles() throws Exception {
        final String result = "/data/tombstones/tombstone_00\r\n/data/tombstones/tombstone_01\r\n";
        final String filename1 = "/data/tombstones/tombstone_00";
        final String filename2 = "/data/tombstones/tombstone_01";
        dfr.addPatterns("/data/tombstones/*");

        // Search the filesystem
        EasyMock.expect(mDevice.executeShellCommand((String)EasyMock.anyObject()))
                .andReturn(result);

        // Log the first file
        // This gets passed verbatim to createIssForFile above
        EasyMock.expect(mDevice.pullFile(EasyMock.eq(filename1))).andReturn(null);
        final String tombstone = "What do you want on your tombstone?";
        mDfrIss = new ByteArrayInputStreamSource(tombstone.getBytes());
        // FIXME: use captures here to make sure we get the string back out
        mListener.testLog(EasyMock.eq(filename1), EasyMock.eq(LogDataType.UNKNOWN),
                EasyMock.eq(mDfrIss));

        // Log the second file
        // This gets passed verbatim to createIssForFile above
        EasyMock.expect(mDevice.pullFile(EasyMock.eq(filename2))).andReturn(null);
        // FIXME: use captures here to make sure we get the string back out
        mListener.testLog(EasyMock.eq(filename2), EasyMock.eq(LogDataType.UNKNOWN),
                EasyMock.eq(mDfrIss));

        replayMocks();
        dfr.run();
        verifyMocks();
    }

    private void replayMocks() {
        EasyMock.replay(mDevice, mListener);
    }

    private void verifyMocks() {
        EasyMock.verify(mDevice, mListener);
    }
}
