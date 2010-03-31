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
package com.android.tradefed.testtype.testdefs;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.StubTestDevice;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.testtype.InstrumentationTest;
import com.android.tradefed.testtype.MockInstrumentationTest;

import org.easymock.EasyMock;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import junit.framework.TestCase;

/**
 * Unit tests for {@link XmlDefTest}.
 */
public class XmlDefsTestTest extends TestCase {

    private static final String TEST_PATH = "foopath";
    private static final String TEST_DEF_DATA = XmlDefsParserTest.TEST_DATA;
    private static final String TEST_PKG = XmlDefsParserTest.TEST_PKG;
    private ITestDevice mMockTestDevice;
    private ITestInvocationListener mMockListener;
    private XmlDefsTest mXmlTest;
    private MockInstrumentationTest mMockInstrumentationTest;

    /**
     * {@inheritDoc}
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mMockTestDevice = EasyMock.createMock(ITestDevice.class);
        EasyMock.expect(mMockTestDevice.getSerialNumber()).andReturn("foo").anyTimes();
        mMockListener = EasyMock.createMock(ITestInvocationListener.class);
        mMockInstrumentationTest = new MockInstrumentationTest();

        mXmlTest = new XmlDefsTest() {
            @Override
            InstrumentationTest createInstrumentationTest() {
                return mMockInstrumentationTest;
            }
        };
        mXmlTest.setDevice(mMockTestDevice);
    }

    /**
     * Test the run normal case. Simple verification that expected data is passed along, etc.
     */
    public void testRun() throws DeviceNotAvailableException {
        mXmlTest.addRemoteFilePath(TEST_PATH);

        // TODO: it would be nice to mock out the file objects, so this test wouldn't need to do
        // IO
        mMockTestDevice.pullFile(EasyMock.eq(TEST_PATH), (File)EasyMock.anyObject());
        EasyMock.expectLastCall().andDelegateTo(new StubTestDevice() {
            @Override
            public boolean pullFile(String remoteFilePath, File localFile)
                    throws DeviceNotAvailableException {
                // simulate the pull file by dumping data into local file
                FileOutputStream outStream;
                try {
                    outStream = new FileOutputStream(localFile);
                    outStream.write(TEST_DEF_DATA.getBytes());
                    outStream.close();
                    return true;
                } catch (IOException e) {
                    fail(e.toString());
                }
                return false;
            }
        });
        EasyMock.replay(mMockTestDevice);
        mXmlTest.run(mMockListener);
        assertEquals(mMockListener, mMockInstrumentationTest.getListener());
        assertEquals(TEST_PKG, mMockInstrumentationTest.getPackageName());
    }

    /**
     * Test that IllegalArgumentException is thrown when attempting run without setting device.
     */
    public void testRun_noDevice() throws Exception {
        mXmlTest.addRemoteFilePath(TEST_PATH);
        mXmlTest.setDevice(null);
        try {
            mXmlTest.run(mMockListener);
            fail("IllegalArgumentException not thrown");
        } catch (IllegalArgumentException e) {
            // expected
        }
        assertNull(mMockInstrumentationTest.getPackageName());
    }

    /**
     * Test that IllegalArgumentException is thrown when attempting run without setting any file
     * paths.
     */
    public void testRun_noPath() throws Exception {
        try {
            mXmlTest.run(mMockListener);
            fail("IllegalArgumentException not thrown");
        } catch (IllegalArgumentException e) {
            // expected
        }
        assertNull(mMockInstrumentationTest.getPackageName());
    }
}
