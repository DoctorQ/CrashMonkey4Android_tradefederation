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
package com.android.tradefed.util.brillopad.item;

import java.util.Map;

import junit.framework.TestCase;

/**
 * Unit test for {@link AbstractItem}.
 */
public class AbstractItemTest extends TestCase {
    private String mStringAttribute = "String";
    private Integer mIntegerAttribute = 1;

    /** Empty item with no attributes set */
    private StubAbstractItem mEmptyItem1;
    /** Empty item with no attributes set */
    private StubAbstractItem mEmptyItem2;
    /** Item with only the string attribute set */
    private StubAbstractItem mStringItem;
    /** Item with only the integer attribute set */
    private StubAbstractItem mIntegerItem;
    /** Item with both attributes set, product of mStringItem and mIntegerItem */
    private StubAbstractItem mFullItem1;
    /** Item with both attributes set, product of mStringItem and mIntegerItem */
    private StubAbstractItem mFullItem2;
    /** Item that is inconsistent with the others */
    private StubAbstractItem mInconsistentItem;

    @Override
    public void setUp() {
        mEmptyItem1 = new StubAbstractItem();
        mEmptyItem2 = new StubAbstractItem();
        mStringItem = new StubAbstractItem();
        mStringItem.setAttribute("string", mStringAttribute);
        mIntegerItem = new StubAbstractItem();
        mIntegerItem.setAttribute("integer", mIntegerAttribute);
        mFullItem1 = new StubAbstractItem();
        mFullItem1.setAttribute("string", mStringAttribute);
        mFullItem1.setAttribute("integer", mIntegerAttribute);
        mFullItem2 = new StubAbstractItem();
        mFullItem2.setAttribute("string", mStringAttribute);
        mFullItem2.setAttribute("integer", mIntegerAttribute);
        mInconsistentItem = new StubAbstractItem();
        mInconsistentItem.setAttribute("string", "gnirts");
        mInconsistentItem.setAttribute("integer", 2);
    }

    /**
     * Test for {@link AbstractItem#mergeAttributes(IItem)}.
     */
    public void testMergeAttributes() throws ConflictingItemException {
        Map<String, Object> attributes;

        attributes = mEmptyItem1.mergeAttributes(mEmptyItem1);
        assertNull(attributes.get("string"));
        assertNull(attributes.get("integer"));

        attributes = mEmptyItem1.mergeAttributes(mEmptyItem2);
        assertNull(attributes.get("string"));
        assertNull(attributes.get("integer"));

        attributes = mEmptyItem2.mergeAttributes(mEmptyItem1);
        assertNull(attributes.get("string"));
        assertNull(attributes.get("integer"));

        attributes = mEmptyItem1.mergeAttributes(mStringItem);
        assertEquals(mStringAttribute, attributes.get("string"));
        assertNull(attributes.get("integer"));

        attributes = mStringItem.mergeAttributes(mEmptyItem1);
        assertEquals(mStringAttribute, attributes.get("string"));
        assertNull(attributes.get("integer"));

        attributes = mIntegerItem.mergeAttributes(mStringItem);
        assertEquals(mStringAttribute, attributes.get("string"));
        assertEquals(mIntegerAttribute, attributes.get("integer"));

        attributes = mEmptyItem1.mergeAttributes(mFullItem1);
        assertEquals(mStringAttribute, attributes.get("string"));
        assertEquals(mIntegerAttribute, attributes.get("integer"));

        attributes = mFullItem1.mergeAttributes(mEmptyItem1);
        assertEquals(mStringAttribute, attributes.get("string"));
        assertEquals(mIntegerAttribute, attributes.get("integer"));

        attributes = mFullItem1.mergeAttributes(mFullItem2);
        assertEquals(mStringAttribute, attributes.get("string"));
        assertEquals(mIntegerAttribute, attributes.get("integer"));

        try {
            mFullItem1.mergeAttributes(mInconsistentItem);
            fail("Expecting a ConflictingItemException");
        } catch (ConflictingItemException e) {
            // Expected
        }
    }

    /**
     * Test for {@link AbstractItem#isConsistent(IItem)}.
     */
    public void testIsConsistent() {
        assertTrue(mEmptyItem1.isConsistent(mEmptyItem1));
        assertFalse(mEmptyItem1.isConsistent(null));
        assertTrue(mEmptyItem1.isConsistent(mEmptyItem2));
        assertTrue(mEmptyItem2.isConsistent(mEmptyItem1));
        assertTrue(mEmptyItem1.isConsistent(mStringItem));
        assertTrue(mStringItem.isConsistent(mEmptyItem1));
        assertTrue(mIntegerItem.isConsistent(mStringItem));
        assertTrue(mEmptyItem1.isConsistent(mFullItem1));
        assertTrue(mFullItem1.isConsistent(mEmptyItem1));
        assertTrue(mFullItem1.isConsistent(mFullItem2));
        assertFalse(mFullItem1.isConsistent(mInconsistentItem));
    }

    /**
     * Test {@link AbstractItem#equals(Object)}.
     */
    public void testEquals() {
        assertTrue(mEmptyItem1.equals(mEmptyItem1));
        assertFalse(mEmptyItem1.equals(null));
        assertTrue(mEmptyItem1.equals(mEmptyItem2));
        assertTrue(mEmptyItem2.equals(mEmptyItem1));
        assertFalse(mEmptyItem1.equals(mStringItem));
        assertFalse(mStringItem.equals(mEmptyItem1));
        assertFalse(mIntegerItem.equals(mStringItem));
        assertFalse(mEmptyItem1.equals(mFullItem1));
        assertFalse(mFullItem1.equals(mEmptyItem1));
        assertTrue(mFullItem1.equals(mFullItem2));
        assertFalse(mFullItem1.equals(mInconsistentItem));
    }

    /**
     * Test for {@link AbstractItem#setAttribute(String, Object)} and
     * {@link AbstractItem#getAttribute(String)}.
     */
    public void testAttributes() {
        StubAbstractItem item = new StubAbstractItem();

        assertNull(item.getAttribute("string"));
        assertNull(item.getAttribute("integer"));

        item.setAttribute("string", mStringAttribute);
        item.setAttribute("integer", mIntegerAttribute);

        assertEquals(mStringAttribute, item.getAttribute("string"));
        assertEquals(mIntegerAttribute, item.getAttribute("integer"));

        item.setAttribute("string", null);
        item.setAttribute("integer", null);

        assertNull(item.getAttribute("string"));
        assertNull(item.getAttribute("integer"));

        try {
            item.setAttribute("object", new Object());
            fail("Failed to throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // Expected because "object" is not "string" or "integer".
        }
    }

    /**
     * Test for {@link AbstractItem#areEqual(Object, Object)}
     */
    public void testAreEqual() {
        assertTrue(AbstractItem.areEqual(null, null));
        assertTrue(AbstractItem.areEqual("test", "test"));
        assertFalse(AbstractItem.areEqual(null, "test"));
        assertFalse(AbstractItem.areEqual("test", null));
        assertFalse(AbstractItem.areEqual("test", ""));
    }

    /**
     * Test for {@link AbstractItem#areConsistent(Object, Object)}
     */
    public void testAreConsistent() {
        assertTrue(AbstractItem.areConsistent(null, null));
        assertTrue(AbstractItem.areConsistent("test", "test"));
        assertTrue(AbstractItem.areConsistent(null, "test"));
        assertTrue(AbstractItem.areConsistent("test", null));
        assertFalse(AbstractItem.areConsistent("test", ""));
    }

    /**
     * Test for {@link AbstractItem#mergeObjects(Object, Object)}
     */
    public void testMergeObjects() throws ConflictingItemException {
        assertNull(AbstractItem.mergeObjects(null, null));
        assertEquals("test", AbstractItem.mergeObjects("test", "test"));
        assertEquals("test", AbstractItem.mergeObjects(null, "test"));
        assertEquals("test", AbstractItem.mergeObjects("test", null));

        try {
            assertEquals("test", AbstractItem.mergeObjects("test", ""));
            fail("Expected ConflictingItemException to be thrown");
        } catch (ConflictingItemException e) {
            // Expected because "test" conflicts with "".
        }
    }
}
