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

import org.easymock.EasyMock;

import java.util.Date;

import junit.framework.TestCase;

/**
 * Unit test for {@link NativeCrash}
 */
public class NativeCrashTest extends TestCase{
    /** NativeCrash containing no information */
    private NativeCrash mEmptyNc1;
    /** NativeCrash containing no information */
    private NativeCrash mEmptyNc2;
    /** NativeCrash containing partial information */
    private NativeCrash mPartialNc1;
    /** NativeCrash containing partial information complementing mPartialNc1 */
    private NativeCrash mPartialNc2;
    /** NativeCrash containing complete information */
    private NativeCrash mFullNc1;
    /** NativeCrash containing complete information */
    private NativeCrash mFullNc2;
    /** NativeCrash containing inconsistent information */
    private NativeCrash mInconsistentNc;

    /**
     * Initialize the NativeCrashes
     */
    @Override
    public void setUp() {
        mEmptyNc1 = new NativeCrash();
        mEmptyNc2 = new NativeCrash();

        mPartialNc1 = new NativeCrash();
        mPartialNc1.setStack("stack");
        mPartialNc1.setTime(new Date(0));

        mPartialNc2 = new NativeCrash();
        mPartialNc2.setProcess(5);
        mPartialNc2.setThread(7);

        mFullNc1 = new NativeCrash();
        mFullNc1.setStack("stack");
        mFullNc1.setTime(new Date(0));
        mFullNc1.setProcess(5);
        mFullNc1.setThread(7);

        mFullNc2 = new NativeCrash();
        mFullNc2.setStack("stack");
        mFullNc2.setTime(new Date(0));
        mFullNc2.setProcess(5);
        mFullNc2.setThread(7);

        mInconsistentNc = new NativeCrash();
        mInconsistentNc.setStack("different stack");
        mInconsistentNc.setTime(new Date(10));
        mInconsistentNc.setProcess(13);
        mInconsistentNc.setThread(17);
    }

    /**
     * Test for {@link NativeCrash#merge(IItem)}
     */
    public void testMerge() throws ConflictingItemException {
        NativeCrash nc = mEmptyNc1.merge(mEmptyNc1);
        assertNull(nc.getStack());
        assertNull(nc.getTime());
        assertNull(nc.getProcess());
        assertNull(nc.getThread());

        try {
            nc = mEmptyNc1.merge(null);
            fail("Expected ConflictingItemException");
        } catch (ConflictingItemException e) {
            // Expected
        }

        try {
            nc = mEmptyNc1.merge(EasyMock.createMock(IItem.class));
            fail("Expected ConflictingItemException");
        } catch (ConflictingItemException e) {
            // Expected
        }

        nc = mEmptyNc1.merge(mEmptyNc2);
        assertNull(nc.getStack());
        assertNull(nc.getTime());
        assertNull(nc.getProcess());
        assertNull(nc.getThread());

        nc = mPartialNc1.merge(mPartialNc2);
        assertEquals("stack", nc.getStack());
        assertEquals(new Date(0), nc.getTime());
        assertEquals(new Integer(5), nc.getProcess());
        assertEquals(new Integer(7), nc.getThread());

        nc = mFullNc1.merge(mFullNc2);
        assertEquals("stack", nc.getStack());
        assertEquals(new Date(0), nc.getTime());
        assertEquals(new Integer(5), nc.getProcess());
        assertEquals(new Integer(7), nc.getThread());

        try {
            nc = mFullNc1.merge(mInconsistentNc);
            fail("Expected ConflictingItemException");
        } catch (ConflictingItemException e) {
            // Expected
        }
    }
}
