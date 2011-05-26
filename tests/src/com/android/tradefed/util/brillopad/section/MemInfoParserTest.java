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
package com.android.tradefed.util.brillopad.section;

import com.android.tradefed.util.brillopad.ItemList;
import com.android.tradefed.util.brillopad.item.GenericMapItem;
import com.android.tradefed.util.brillopad.item.IItem;
import com.android.tradefed.util.brillopad.section.MemInfoParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import junit.framework.TestCase;

/**
 * Unit tests for {@link MemInfoParser}
 */
public class MemInfoParserTest extends TestCase {
    public void testMemInfoParser() {
        List<String> inputBlock = list(
                "MemTotal:         353332 kB",
                "MemFree:           65420 kB",
                "Buffers:           20800 kB",
                "Cached:            86204 kB",
                "SwapCached:            0 kB");
        MemInfoParser parser = new MemInfoParser();
        ItemList br = new ItemList();

        parser.parseBlock(inputBlock, br);
        List<IItem> items = br.getItems();
        assertNotNull(items);
        assertEquals(1, items.size());
        assertTrue("Expected item of type GenericMapItem!", items.get(0) instanceof GenericMapItem);
        assertEquals(MemInfoParser.SECTION_NAME, items.get(0).getType());

        Map<String, Integer> output = (GenericMapItem<String, Integer>)items.get(0);
        assertEquals(5, output.size());
        assertEquals((Integer)353332, output.get("MemTotal"));
        assertEquals((Integer)65420, output.get("MemFree"));
        assertEquals((Integer)20800, output.get("Buffers"));
        assertEquals((Integer)86204, output.get("Cached"));
        assertEquals((Integer)0, output.get("SwapCached"));
    }

    private static List<String> list(String... strings) {
        List<String> retList = new ArrayList<String>(strings.length);
        for (String str : strings) {
            retList.add(str);
        }
        return retList;
    }
}

