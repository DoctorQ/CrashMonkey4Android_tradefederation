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
package com.android.tradefed.log;

import com.android.ddmlib.Log;
import com.android.ddmlib.Log.LogLevel;
import com.android.tradefed.config.ConfigurationException;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

import junit.framework.TestCase;

/**
 * Unit tests for {@link FileLogger}.
 */
public class FileLoggerTest extends TestCase {

    private static String LOG_TAG = "FileLoggerTest";

    /**
     * Test that we are able to create a logger.
     *
     * @throws ConfigurationException if unable to create log file
     * @throws SecurityException if unable to delete the log file on cleanup
     */
    public void testCreateLogger() throws ConfigurationException, SecurityException {
        FileLogger logger = new FileLogger();
        String tempFile = logger.getFilename();
        File logFile = new File(tempFile);
        assertTrue(logFile.exists());
        logger.closeLog();
        assertFalse(logFile.exists());
    }

    /**
     * Test logging to a logger.
     *
     * @throws ConfigurationException if unable to create log file
     * @throws IOException if unable to read from the file
     * @throws SecurityException if unable to delete the log file on cleanup
     */
    public void testLogToLogger() throws ConfigurationException, IOException, SecurityException {
        String Text1 = "The quick brown fox jumps over the lazy doggie.";
        String Text2 = "Betty Botter bought some butter, 'But,' she said, 'this butter's bitter.'";
        String Text3 = "Wolf zombies quickly spot the jinxed grave.";
        BufferedReader logFileReader = null;
        File logFile = null;

        try {
            FileLogger logger = new FileLogger();
            // Write 3 lines of text to the log...
            logger.printLog(LogLevel.INFO, LOG_TAG, Text1);
            String expectedText1 = Log.getLogFormatString(LogLevel.INFO, LOG_TAG, Text1).trim();
            logger.printLog(LogLevel.VERBOSE, LOG_TAG, Text2);
            String expectedText2 = Log.getLogFormatString(LogLevel.VERBOSE, LOG_TAG, Text2).trim();
            logger.printLog(LogLevel.ASSERT, LOG_TAG, Text3);
            String expectedText3 = Log.getLogFormatString(LogLevel.ASSERT, LOG_TAG, Text3).trim();

            // Verify the 3 lines we logged
            logFileReader = new BufferedReader(new InputStreamReader(
                    logger.getLog().createInputStream()));

            String actualLogString = logFileReader.readLine().trim();
            assertTrue(actualLogString.equals(expectedText1));

            actualLogString = logFileReader.readLine().trim();
            assertTrue(actualLogString.equals(expectedText2));

            actualLogString = logFileReader.readLine().trim();
            assertTrue(actualLogString.equals(expectedText3));
            logger.closeLog();
        }
        finally {
            if (logFileReader != null) {
                logFileReader.close();
            }
            if (logFile != null) {
                logFile.delete();
            }
        }
    }

    /**
     * Test that an {@link IllegalStateException} is thrown if {@link FileLogger#getLog()} is
     * called after {@link FileLogger#closeLog()}.
     */
    public void testGetLog_afterClose() throws ConfigurationException {
        FileLogger logger = new FileLogger();
        logger.closeLog();
        try {
            logger.getLog();
            fail("IllegalStateException not thrown");
        } catch (IllegalStateException e) {
            // expected
        }
    }

    /**
     * Test that no unexpected Exceptions occur if
     * {@link FileLogger#printLog(LogLevel, String, String)} is called after
     * {@link FileLogger#closeLog()}
     */
    public void testCloseLog() throws ConfigurationException, IOException {
        FileLogger logger = new FileLogger();
        // test the package-private methods to capture any exceptions that occur
        logger.doCloseLog();
        logger.writeToLog("test2");
    }
}
