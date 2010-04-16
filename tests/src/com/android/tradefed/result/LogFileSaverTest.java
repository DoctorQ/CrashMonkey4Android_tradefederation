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

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;

import junit.framework.TestCase;

/**
 * Unit tests for {@link LogFileSaver}.
 */
public class LogFileSaverTest extends TestCase {

    /**
     * Simple normal case test for {@link LogFileSaver#saveLogData(String, String, InputStream)}.
     */
    public void testSaveLogData() throws IOException {
        File logFile = null;
        BufferedReader logFileReader = null;
        try {
            // TODO: would be nice to create a mock file output to make this test not use disk I/O
            File tmpDir = createUniqueTmpDir();
            LogFileSaver saver = new LogFileSaver(tmpDir);
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

    private File createUniqueTmpDir() throws IOException {
        // create a tmp file, then delete it, and recreate a dir with same name to ensure
        // uniqueness
        File tmpDir = File.createTempFile("tmpdir", "");
        tmpDir.delete();
        tmpDir.mkdir();
        tmpDir.deleteOnExit();
        return tmpDir;
    }

}
