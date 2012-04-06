/*
 * Copyright (C) 2012 The Android Open Source Project
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

import jline.ConsoleReader;

import java.io.IOException;
import java.io.OutputStream;

/**
 * An OutputStream that can be used to make {@code System.out.print()} play nice with the user's
 * {@link ConsoleReader} buffer.
 * <p />
 * In trivial performance tests, this class did not have a measurable performance impact.
 */
public class ConsoleReaderOutputStream extends OutputStream {
    /**
     * ANSI "clear line" (Esc + "[2K") followed by carriage return
     * See: http://ascii-table.com/ansi-escape-sequences-vt-100.php
     */
    private static final String ANSI_CR = "\u001b[2K\r";
    private static final String CR = "\r";
    private final ConsoleReader mConsoleReader;

    public ConsoleReaderOutputStream(ConsoleReader reader) {
        if (reader == null) throw new NullPointerException();
        mConsoleReader = reader;
    }

    @Override
    public void flush() {
        try {
            mConsoleReader.flushConsole();
        } catch (IOException e) {
            // ignore
        }
    }

    /**
     * A special implementation to keep the user's command buffer visible when asynchronous tasks
     * write to stdout.
     * <p />
     * If a full-line write is detected (one that terminates with "\n"), we:
     * <ol>
     *   <li>Clear the current line (which will contain the prompt and the user's buffer</li>
     *   <li>Print the full line(s), which will drop us on a new line</li>
     *   <li>Redraw the prompt and the user's buffer</li>
     * </ol>
     * <p />
     * By doing so, we never skip any asynchronously-logged output, but we still keep the prompt and
     * the user's buffer as the last items on the screen.
     */
    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        final boolean fullLine = b[off + len - 1] == '\n';
        if (fullLine) {
            if (mConsoleReader.getTerminal().isANSISupported()) {
                // use ANSI escape codes to clear the line and jump to the beginning
                mConsoleReader.printString(ANSI_CR);
            } else {
                // Just jump to the beginning of the line to print the message
                mConsoleReader.printString(CR);
            }
        }

        mConsoleReader.printString(new String(b, off, len));

        if (fullLine) {
            mConsoleReader.drawLine();
            mConsoleReader.flushConsole();
        }
    }

    // FIXME: it'd be nice if ConsoleReader had a way to write a character rather than just a
    // FIXME: String.  As is, this method makes me cringe.  Especially since the first thing
    // FIXME: ConsoleReader does is convert it back into a char array :o(
    @Override
    public void write(int b) throws IOException {
        char[] str = new char[] {(char)(b & 0xff)};
        mConsoleReader.printString(new String(str));
    }
}

