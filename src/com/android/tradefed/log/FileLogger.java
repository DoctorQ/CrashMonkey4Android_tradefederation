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

import com.android.ddmlib.Log.LogLevel;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.config.Option.Importance;
import com.android.tradefed.result.ByteArrayInputStreamSource;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.SnapshotInputStreamSource;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.StreamUtil;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.HashSet;

/**
 * A {@link ILeveledLogOutput} that directs log messages to a file and to stdout.
 */
@OptionClass(alias = "file")
public class FileLogger implements ILeveledLogOutput {
    private static final String TEMP_FILE_PREFIX = "tradefed_log_";
    private static final String TEMP_FILE_SUFFIX = ".txt";

    private File mTempLogFile = null;
    private BufferedWriter mLogWriter = null;

    @Option(name = "log-level", description = "the minimum log level to log. Must be one of "
            + LogUtil.LOG_LEVEL_LIST + ".")
    private String mLogLevel = LogLevel.DEBUG.getStringValue();

    @Option(name = "log-level-display", description =
        "the minimum log level to display on stdout. Must be one of " + LogUtil.LOG_LEVEL_LIST +
        ".", importance = Importance.ALWAYS)
    private String mLogLevelStringDisplay = LogLevel.ERROR.getStringValue();

    @Option(name = "log-tag-display", description = "Always display given tags logs on stdout")
    private Collection<String> mLogTagsDisplay = new HashSet<String>();

    // temp: track where this log was closed
    private StackTraceElement[] mCloseStackFrames = null;

    /**
     * Sets the log level filtering for stdout.
     *
     * @param logLevel the {@link LogLevel#getStringValue()} log level to display
     */
    void setLogLevelDisplay(String logLevel) {
        mLogLevelStringDisplay = logLevel;
    }

    /**
     * Gets the log level filtering for stdout.
     *
     * @return the {@link String} form of the {@link LogLevel}
     */
    String getLogLevelDisplay() {
        return mLogLevelStringDisplay;
    }

    /**
     * Adds tags to the log-tag-display list
     *
     * @param tags collection of tags to add
     */
    void addLogTagsDisplay(Collection<String> tags) {
        mLogTagsDisplay.addAll(tags);
    }

    public FileLogger() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void init() throws IOException {
        try {
            mTempLogFile = FileUtil.createTempFile(TEMP_FILE_PREFIX, TEMP_FILE_SUFFIX);
            mLogWriter = new BufferedWriter(new FileWriter(mTempLogFile));
        }
        catch (IOException e) {
            if (mTempLogFile != null) {
                mTempLogFile.delete();
            }
            throw e;
        }
    }

    /**
     * Creates a new {@link FileLogger} with the same log level settings as the current object.
     * <p/>
     * Does not copy underlying log file content (ie the clone's log data will be written to a new
     * file.)
     */
    @Override
    public ILeveledLogOutput clone()  {
        FileLogger logger = new FileLogger();
        logger.setLogLevelDisplay(mLogLevelStringDisplay);
        logger.setLogLevel(mLogLevel);
        logger.addLogTagsDisplay(mLogTagsDisplay);
        return logger;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void printAndPromptLog(LogLevel logLevel, String tag, String message) {
        internalPrintLog(logLevel, tag, message, true /* force print to stdout */);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void printLog(LogLevel logLevel, String tag, String message) {
        internalPrintLog(logLevel, tag, message, false /* don't force stdout */);
    }

    /**
     * A version of printLog(...) which can be forced to print to stdout, even if the log level
     * isn't above the urgency threshold.
     */
    private void internalPrintLog(LogLevel logLevel, String tag, String message,
            boolean forceStdout) {
        String outMessage = LogUtil.getLogFormatString(logLevel, tag, message);
        LogLevel displayLevel = LogLevel.getByString(mLogLevelStringDisplay);
        if (forceStdout
                || logLevel.getPriority() >= displayLevel.getPriority()
                || mLogTagsDisplay.contains(tag)) {
            System.out.print(outMessage);
        }
        try {
            writeToLog(outMessage);
        } catch (IOException e) {
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
    @Override
    public String getLogLevel() {
        return mLogLevel;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setLogLevel(String logLevel) {
        mLogLevel = logLevel;
    }

    /**
     * Returns the path representation of the file being logged to by this file logger
     */
    String getFilename() throws SecurityException {
        if (mTempLogFile == null) {
            throw new IllegalStateException(
                    "logger has already been closed or has not been initialized");
        }
        return mTempLogFile.getAbsolutePath();
    }

    /**
     * {@inheritDoc}
     */
    public InputStreamSource getLog() {
        if (mLogWriter == null) {
            // TODO: change this back to throw new IllegalStateException(
            System.err.println(String.format(
                    "logger has already been closed or has not been initialized, Thread %s",
                    Thread.currentThread().getName()));
            System.err.println("Current stack:");
            printStackTrace(Thread.currentThread().getStackTrace());
            System.err.println("\nLog closed at:");
            printStackTrace(mCloseStackFrames);
        } else {
            try {
                // create a InputStream from log file
                mLogWriter.flush();
                return new SnapshotInputStreamSource(new FileInputStream(mTempLogFile));

            } catch (IOException e) {
                System.err.println("Failed to get log");
                e.printStackTrace();
            }
        }
        return new ByteArrayInputStreamSource(new byte[0]);
    }

    private void printStackTrace(StackTraceElement[] trace) {
        if (trace == null) {
            System.err.println("no stack");
            return;
        }
        for (StackTraceElement element : trace) {
            System.err.println("\tat " + element);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
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
        try {
            if (mLogWriter != null) {
                // TODO: temp: track where this log was closed
                mCloseStackFrames = Thread.currentThread().getStackTrace();
                // set mLogWriter to null first before closing, to prevent "write" calls after
                // "close"
                BufferedWriter writer = mLogWriter;
                mLogWriter = null;

                writer.flush();
                writer.close();
            }
        } finally {
            if (mTempLogFile != null) {
                mTempLogFile.delete();
                mTempLogFile = null;
            }
        }
    }

    /**
     * Dump the contents of the input stream to this log
     *
     * @param createInputStream
     * @throws IOException
     */
    void dumpToLog(InputStream inputStream) throws IOException {
        if (mLogWriter != null) {
            StreamUtil.copyStreamToWriter(inputStream, mLogWriter);
        }
    }
}
