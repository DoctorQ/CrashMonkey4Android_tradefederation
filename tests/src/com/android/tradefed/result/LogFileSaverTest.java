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
package com.android.tradefed.result;

import com.android.tradefed.targetsetup.BuildInfo;
import com.android.tradefed.targetsetup.IBuildInfo;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.StreamUtil;

import org.easymock.EasyMock;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import junit.framework.TestCase;

/**
 * Unit tests for {@link LogFileSaver}.
 */
public class LogFileSaverTest extends TestCase {

    private File mRootDir;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mRootDir = FileUtil.createTempDir("tmpdir");
    }

    @Override
    protected void tearDown() throws Exception {
        FileUtil.recursiveDelete(mRootDir);
        super.tearDown();
    }

    /**
     * Test that a unique directory is created
     */
    public void testGetFileDir() throws IOException {
        final int buildId = 88888;
        IBuildInfo mockBuild = EasyMock.createMock(IBuildInfo.class);
        EasyMock.expect(mockBuild.getBuildId()).andReturn(buildId).anyTimes();
        EasyMock.replay(mockBuild);
        ILogFileSaver saver = new LogFileSaver(mockBuild, mRootDir);
        File generatedDir = saver.getFileDir();
        File buildDir = generatedDir.getParentFile();
        // ensure a directory with name == build number is parent of generated directory
        assertEquals(Integer.toString(buildId), buildDir.getName());
        // ensure parent directory is rootDir
        assertEquals(0, mRootDir.compareTo(buildDir.getParentFile()));

        // now create a new log saver,
        ILogFileSaver newsaver = new LogFileSaver(mockBuild, mRootDir);
        File newgeneratedDir = newsaver.getFileDir();
        // ensure a new dir is created
        assertTrue(generatedDir.compareTo(newgeneratedDir) != 0);
        // verify buildDir is reused
        File newbuildDir = newgeneratedDir.getParentFile();
        assertEquals(0, buildDir.compareTo(newbuildDir));
    }

    /**
     * Simple normal case test for
     * {@link LogFileSaver#saveLogData(String, LogDataType, InputStream)}.
     */
    public void testSaveLogData() throws IOException {
        File logFile = null;
        BufferedReader logFileReader = null;
        try {
            // TODO: would be nice to create a mock file output to make this test not use disk I/O
            ILogFileSaver saver = new LogFileSaver(new BuildInfo(), mRootDir);
            final String testData = "Here's some test data, blah";
            ByteArrayInputStream mockInput = new ByteArrayInputStream(testData.getBytes());
            logFile = saver.saveLogData("testSaveLogData", LogDataType.TEXT, mockInput);

            // Verify test data was written to file
            logFileReader = new BufferedReader(new FileReader(logFile));
            String actualLogString = logFileReader.readLine().trim();
            assertTrue(actualLogString.equals(testData));
        } finally {
            if (logFileReader != null) {
                logFileReader.close();
            }
            if (logFile != null) {
                logFile.delete();
            }
        }
    }

    /**
     * Simple normal case test for
     * {@link LogFileSaver#saveAndZipLogData}.
     */
    public void testSaveAndZipLogData() throws IOException {
        File logFile = null;
        ZipInputStream zipFileStream = null;
        try {
            // TODO: would be nice to create a mock file output to make this test not use disk I/O
            ILogFileSaver saver = new LogFileSaver(new BuildInfo(), mRootDir);
            final String testData = "Here's some test data, blah";
            ByteArrayInputStream mockInput = new ByteArrayInputStream(testData.getBytes());
            logFile = saver.saveAndZipLogData("testSaveLogData", LogDataType.TEXT, mockInput);

            assertTrue(logFile.getName().endsWith(LogDataType.ZIP.getFileExt()));
            // Verify test data was written to file
            ZipFile zipFile = new ZipFile(logFile);

            String actualLogString = StreamUtil.getStringFromStream(zipFile.getInputStream(
                    new ZipEntry("testSaveLogData.txt")));
            assertTrue(actualLogString.equals(testData));
        } finally {
            if (zipFileStream != null) {
                zipFileStream.close();
            }
            if (logFile != null) {
                logFile.delete();
            }
        }
    }
}
