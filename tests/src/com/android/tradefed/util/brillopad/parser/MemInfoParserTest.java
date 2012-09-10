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
package com.android.tradefed.util.brillopad.parser;

import com.android.tradefed.util.brillopad.item.MemInfoItem;

import junit.framework.TestCase;

import java.util.Arrays;
import java.util.List;

/**
 * Unit tests for {@link MemInfoParser}
 */
public class MemInfoParserTest extends TestCase {
    public void testMemInfoParser() {
        List<String> inputBlock = Arrays.asList(
                "MemTotal:         353332 kB",
                "MemFree:           65420 kB",
                "Buffers:           20800 kB",
                "Cached:            86204 kB",
                "SwapCached:            0 kB");
        MemInfoParser parser = new MemInfoParser();
        MemInfoItem output = parser.parse(inputBlock);

        assertEquals(5, output.size());
        assertEquals((Integer)353332, output.get("MemTotal"));
        assertEquals((Integer)65420, output.get("MemFree"));
        assertEquals((Integer)20800, output.get("Buffers"));
        assertEquals((Integer)86204, output.get("Cached"));
        assertEquals((Integer)0, output.get("SwapCached"));
    }
}
