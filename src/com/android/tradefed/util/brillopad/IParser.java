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
package com.android.tradefed.util.brillopad;

import java.io.BufferedReader;
import java.io.IOException;

/**
 * Interface for all parsers.
 *
 * <p>
 * Parsers are meant to be used only once and contain state.  This means that an input can be split
 * up and parsed by the appropriate parse method without any affect on the parsing.  For example,
 * a buffered reader containing 10 lines can be broken into 10 individual lines with
 * {@link IParser#parse(String)} being run 10 times and that will produce the same state as
 * {@link IParser#parse(BufferedReader)} being run once with the original buffered reader.
 * </p>
 */
public interface IParser {

    /**
     * Parses an {@link java.io.BufferedReader} object.
     *
     * @param input The {@link java.io.BufferedReader} object
     * @throws IOException If there was a problem reading the Buffered reader
     */
    public void parse(BufferedReader input) throws IOException;

    /**
     * Parsers an array of {@link java.lang.String} objects.
     *
     * @param input The {@link java.lang.String} array.
     * @throws IOException If any of the strings are null.
     */
    public void parse(String[] input) throws IOException;

    /**
     * Parses a single {@link java.lang.String}
     *
     * @param line
     */
    public void parse(String line);
}
