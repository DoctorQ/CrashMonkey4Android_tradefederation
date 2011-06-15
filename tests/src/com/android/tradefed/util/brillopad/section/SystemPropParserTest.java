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

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import junit.framework.TestCase;

/**
 * Unit tests for {@link SystemPropParser}
 */
public class SystemPropParserTest extends TestCase {
    @SuppressWarnings("unchecked")
    public void testSimpleParse() {
        List<String> inputBlock = Arrays.asList(
                "[dalvik.vm.dexopt-flags]: [m=y]",
                "[dalvik.vm.heapgrowthlimit]: [48m]",
                "[dalvik.vm.heapsize]: [256m]",
                "[gsm.version.ril-impl]: [android moto-ril-multimode 1.0]");
        SystemPropParser parser = new SystemPropParser();
        ItemList br = new ItemList();

        parser.parseBlock(inputBlock, br);
        List<IItem> items = br.getItems();
        assertNotNull(items);
        assertEquals(1, items.size());
        assertTrue("Expected item of type GenericMapItem!", items.get(0) instanceof GenericMapItem);
        assertEquals(SystemPropParser.SECTION_NAME, items.get(0).getType());

        Map<String, String> map = (Map<String, String>)items.get(0);
        assertEquals(4, map.size());
        assertEquals("m=y", map.get("dalvik.vm.dexopt-flags"));
        assertEquals("48m", map.get("dalvik.vm.heapgrowthlimit"));
        assertEquals("256m", map.get("dalvik.vm.heapsize"));
        assertEquals("android moto-ril-multimode 1.0", map.get("gsm.version.ril-impl"));
    }

    /**
     * Make sure that a parse error on one line doesn't prevent the rest of the lines from being
     * parsed
     */
    @SuppressWarnings("unchecked")
    public void testParseError() {
        List<String> inputBlock = Arrays.asList(
                "[dalvik.vm.dexopt-flags]: [m=y]",
                "[ends with newline]: [yup",
                "]",
                "[dalvik.vm.heapsize]: [256m]");
        SystemPropParser parser = new SystemPropParser();
        ItemList br = new ItemList();

        parser.parseBlock(inputBlock, br);
        List<IItem> items = br.getItems();
        assertNotNull(items);
        assertEquals(1, items.size());
        assertTrue("Expected item of type GenericMapItem!", items.get(0) instanceof GenericMapItem);
        assertEquals(SystemPropParser.SECTION_NAME, items.get(0).getType());

        Map<String, String> map = (Map<String, String>)items.get(0);
        assertEquals(2, map.size());
        assertEquals("m=y", map.get("dalvik.vm.dexopt-flags"));
        assertEquals("256m", map.get("dalvik.vm.heapsize"));
    }
}

