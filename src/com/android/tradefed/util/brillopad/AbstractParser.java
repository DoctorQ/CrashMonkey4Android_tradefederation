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
 * Abstract parser to implement common methods of all parsers.
 */
public abstract class AbstractParser implements IParser {

    /**
     * {@inheritDoc}
     */
    @Override
    public void parse(BufferedReader input) throws IOException {
        String line;
        while ((line = input.readLine()) != null) {
            parse(line);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void parse(String[] input) throws IOException {
        for (String line : input) {
            if (line == null) {
                throw new IOException("Encountered a null line");
            }
            parse(line);
        }
    }
}
