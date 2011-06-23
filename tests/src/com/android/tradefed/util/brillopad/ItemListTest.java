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

import com.android.tradefed.util.brillopad.item.IItem;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.PatternSyntaxException;

import junit.framework.TestCase;

/**
 * Unit tests for {@link ItemList}
 */
public class ItemListTest extends TestCase {
    private ItemList mList = null;

    /**
     * {@inheritDoc}
     */
    @Override
    public void setUp() throws Exception {
        super.setUp();
        mList = new ItemList();
    }

    private static class FakeItem implements IItem {
        private String mType = null;
        public FakeItem(String type) {
            mType = type;
        }
        @Override
        public String getType() {
            return mType;
        }
        @Override
        public IItem merge(IItem other) {
            return this;
        }
        @Override
        public boolean isConsistent(IItem other) {
            return true;
        }
    }

    /**
     * Verify that if you add some {@link IItem}s, you can get them back out as well.
     */
    public void testAdd() {
        List<IItem> iitems = new ArrayList<IItem>(5);
        for (Integer i = 0; i < 5; ++i) {
            IItem item = new FakeItem(i.toString());
            iitems.add(item);
            mList.addItem(item);
        }

        assertEquals("Wrong number of iitems!", iitems.size(), mList.getItems().size());
    }

    /**
     * Verify that searching works properly
     */
    public void testSearch() {
        final String namePat = "fake %d";
        final int nameCount = 5;
        List<IItem> iitems = new ArrayList<IItem>(nameCount);
        for (Integer i = 0; i < nameCount; ++i) {
            IItem item = new FakeItem(String.format(namePat, i));
            iitems.add(item);
            mList.addItem(item);
        }

        for (Integer i = 0; i < nameCount; ++i) {
            List<IItem> searchList = mList.getItemsByType(String.format(".*%d.*", i));
            assertEquals(1, searchList.size());
            assertEquals(String.format(namePat, i), searchList.get(0).getType());
        }

        assertEquals(nameCount, mList.getItemsByType("fake \\d").size());
    }

    /**
     * Verify that {@link ItemList#getFirstItemByType} works as expected
     */
    public void testSearchFirst() {
        final String namePat = "fake %d";
        final int nameCount = 5;
        List<IItem> iitems = new ArrayList<IItem>(nameCount);
        for (Integer i = 0; i < nameCount; ++i) {
            IItem item = new FakeItem(String.format(namePat, i));
            iitems.add(item);
            mList.addItem(item);
        }

        assertNull(mList.getFirstItemByType("real \\d"));
        IItem item = mList.getFirstItemByType(".*3.*");
        assertEquals("fake 3", item.getType());
        item = mList.getFirstItemByType("fake \\d");
        assertEquals("fake 0", item.getType());
    }

    /**
     * Verify that {@link PatternSyntaxException} is thrown if an invalid regular expression is used
     */
    public void testSearchInvalid() {
        try {
            mList.getItemsByType("(");
            fail("PatternSyntaxException not thrown");
        } catch (PatternSyntaxException e) {
            // expected
        }

        try {
            mList.getFirstItemByType("(");
            fail("PatternSyntaxException not thrown");
        } catch (PatternSyntaxException e) {
            // expected
        }
    }
}

