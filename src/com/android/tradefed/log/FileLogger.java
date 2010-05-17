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
import com.android.tradefed.config.Option;

import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;

/**
 * A {@link ILeveledLogOutput} that directs log messages to a file and to stdout.
 */
public class FileLogger implements ILeveledLogOutput {

    private static final String TEMP_FILE_PREFIX = "tradefed_log_";
    private static final String TEMP_FILE_SUFFIX = ".txt";

    private File mTempLogFile = null;
    private BufferedWriter mLogWriter = null;

    // TODO: raise this to info level
    @Option(name="log-level", description="minimum log level to log")
    private String mLogLevel = LogLevel.DEBUG.getStringValue();

    // TODO: raise this to warning level
    @Option(name="log-level-display", description="minimum log level to display on stdout")
    private String mLogLevelStringDisplay = LogLevel.INFO.getStringValue();

    /**
     * Constructor for creating a temporary log file in the system's default temp directory
     * @throws ConfigurationException if unable to create log file
     */
    public FileLogger() throws ConfigurationException  {
        try {
            mTempLogFile = File.createTempFile(TEMP_FILE_PREFIX, TEMP_FILE_SUFFIX);
            // delete the temp file on exit rather than on close, so log is available in case
            // program fails before logs are collected
            mTempLogFile.deleteOnExit();
            mLogWriter = new BufferedWriter(new FileWriter(mTempLogFile));
        }
        catch (IOException e) {
            throw new ConfigurationException(String.format("Could not create output log file : %s",
                    e.getMessage()));
        }
    }

    /**
     * {@inheritDoc}
     */
    public void printAndPromptLog(LogLevel logLevel, String tag, String message) {
        printLog(logLevel, tag, message);
    }

    /**
     * {@inheritDoc}
     */
    public void printLog(LogLevel logLevel, String tag, String message) {
        String outMessage = Log.getLogFormatString(logLevel, tag, message);
        LogLevel displayLevel = LogLevel.getByString(mLogLevelStringDisplay);
        if (logLevel.getPriority() >= displayLevel.getPriority()) {
            System.out.print(outMessage);
        }
        try {
            writeToLog(outMessage);
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Writes given message to log.
     * <p/>
     * Exposed for unit testing.
     *
     * @param outMessage the entry to write to log
     * @throws IOException
     */
    void writeToLog(String outMessage) throws IOException {
        if (mLogWriter != null) {
            mLogWriter.write(outMessage);
        }
    }

    /**
     * {@inheritDoc}
     */
    public String getLogLevel() {
        return mLogLevel;
    }

    /**
     * Returns the path representation of the file being logged to by this file logger
     */
    String getFilename() throws SecurityException {
        return mTempLogFile.getAbsolutePath();
    }

    /**
     * {@inheritDoc}
     */
    public InputStream getLog() {
        if (mLogWriter == null) {
            throw new IllegalStateException();
        }
        try {
            // create a InputStream from log file
            mLogWriter.flush();
            return new FileInputStream(mTempLogFile);
        } catch (IOException e) {
            System.err.println("Failed to get log");
            e.printStackTrace();
        }
        return new ByteArrayInputStream(new byte[0]);
    }

    /**
     * {@inheritDoc}
     */
    public void closeLog() {
        try {
            doCloseLog();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Flushes stream and closes log file.
     * <p/>
     * Exposed for unit testing.
     *
     * @throws IOException
     */
    void doCloseLog() throws IOException {
        // set mLogWriter to null first before closing, to prevent "write" calls after "close"
        BufferedWriter writer = mLogWriter;
        mLogWriter = null;
        writer.flush();
        writer.close();
    }
}
