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
 * Unit test for {@link Anr}
 */
public class AnrTest extends TestCase {
    /** Anr containing no information */
    private Anr mEmptyAnr1;
    /** Anr containing no information */
    private Anr mEmptyAnr2;
    /** Anr containing partial information */
    private Anr mPartialAnr1;
    /** Anr containing partial information complementing mPartialAnr1 */
    private Anr mPartialAnr2;
    /** Anr containing complete information */
    private Anr mFullAnr1;
    /** Anr containing complete information */
    private Anr mFullAnr2;
    /** Anr inconsistent with the other Anrs */
    private Anr mInconsistentAnr;

    /**
     * Initialize the Anrs
     */
    @Override
    public void setUp() {
        mEmptyAnr1 = new Anr();
        mEmptyAnr2 = new Anr();

        mPartialAnr1 = new Anr();
        mPartialAnr1.setPackageName("com.android.package");
        mPartialAnr1.setReason("reason");
        mPartialAnr1.setCpuUser(new Double(13));
        mPartialAnr1.setCpuIoWait(new Double(19));
        mPartialAnr1.setLoad1(new Double(2.0));
        mPartialAnr1.setLoad15(new Double(5.0));
        mPartialAnr1.setProcess(new Integer(101));

        mPartialAnr2 = new Anr();
        mPartialAnr2.setActivity("com.android.package/.Activity");
        mPartialAnr2.setCpuTotal(new Double(11));
        mPartialAnr2.setCpuKernel(new Double(17));
        mPartialAnr2.setCpuIrq(new Double(23));
        mPartialAnr2.setLoad5(new Double(3.0));
        mPartialAnr2.setTime(new Date(0));
        mPartialAnr2.setThread(new Integer(103));

        mFullAnr1 = new Anr();
        mFullAnr1.setPackageName("com.android.package");
        mFullAnr1.setActivity("com.android.package/.Activity");
        mFullAnr1.setReason("reason");
        mFullAnr1.setCpuTotal(new Double(11));
        mFullAnr1.setCpuUser(new Double(13));
        mFullAnr1.setCpuKernel(new Double(17));
        mFullAnr1.setCpuIoWait(new Double(19));
        mFullAnr1.setCpuIrq(new Double(23));
        mFullAnr1.setLoad1(new Double(2.0));
        mFullAnr1.setLoad5(new Double(3.0));
        mFullAnr1.setLoad15(new Double(5.0));
        mFullAnr1.setTime(new Date(0));
        mFullAnr1.setProcess(new Integer(101));
        mFullAnr1.setThread(new Integer(103));

        mFullAnr2 = new Anr();
        mFullAnr2.setPackageName("com.android.package");
        mFullAnr2.setActivity("com.android.package/.Activity");
        mFullAnr2.setReason("reason");
        mFullAnr2.setCpuTotal(new Double(11));
        mFullAnr2.setCpuUser(new Double(13));
        mFullAnr2.setCpuKernel(new Double(17));
        mFullAnr2.setCpuIoWait(new Double(19));
        mFullAnr2.setCpuIrq(new Double(23));
        mFullAnr2.setLoad1(new Double(2.0));
        mFullAnr2.setLoad5(new Double(3.0));
        mFullAnr2.setLoad15(new Double(5.0));
        mFullAnr2.setTime(new Date(0));
        mFullAnr2.setProcess(new Integer(101));
        mFullAnr2.setThread(new Integer(103));

        mInconsistentAnr = new Anr();
        mInconsistentAnr.setPackageName("com.android.differentpacakge");
        mInconsistentAnr.setActivity("com.android.differentpackage/.DifferentActivity");
        mInconsistentAnr.setReason("different reason");
        mInconsistentAnr.setCpuTotal(new Double(23));
        mInconsistentAnr.setCpuUser(new Double(29));
        mInconsistentAnr.setCpuKernel(new Double(31));
        mInconsistentAnr.setCpuIoWait(new Double(37));
        mInconsistentAnr.setCpuIrq(new Double(41));
        mInconsistentAnr.setLoad1(new Double(7.0));
        mInconsistentAnr.setLoad5(new Double(11.0));
        mInconsistentAnr.setLoad15(new Double(13.0));
        mInconsistentAnr.setTime(new Date(10));
        mInconsistentAnr.setProcess(new Integer(107));
        mInconsistentAnr.setThread(new Integer(109));
    }

    /**
     * Test for {@link Anr#merge(IItem)}
     */
    public void testMerge() throws ConflictingItemException {
        Anr anr = mEmptyAnr1.merge(mEmptyAnr1);
        assertNull(anr.getPackageName());
        assertNull(anr.getActivity());
        assertNull(anr.getReason());
        assertNull(anr.getCpuUser());
        assertNull(anr.getCpuTotal());
        assertNull(anr.getCpuKernel());
        assertNull(anr.getCpuIoWait());
        assertNull(anr.getCpuIrq());
        assertNull(anr.getLoad1());
        assertNull(anr.getLoad5());
        assertNull(anr.getLoad15());
        assertNull(anr.getTime());
        assertNull(anr.getProcess());
        assertNull(anr.getThread());

        try {
            anr = mEmptyAnr1.merge(null);
            fail("Expected ConflictingItemException");
        } catch (ConflictingItemException e) {
            // Expected
        }

        try {
            anr = mEmptyAnr1.merge(EasyMock.createMock(IItem.class));
            fail("Expected ConflictingItemException");
        } catch (ConflictingItemException e) {
            // Expected
        }

        anr = mEmptyAnr1.merge(mEmptyAnr2);
        assertNull(anr.getPackageName());
        assertNull(anr.getActivity());
        assertNull(anr.getReason());
        assertNull(anr.getCpuTotal());
        assertNull(anr.getCpuUser());
        assertNull(anr.getCpuKernel());
        assertNull(anr.getCpuIoWait());
        assertNull(anr.getCpuIrq());
        assertNull(anr.getLoad1());
        assertNull(anr.getLoad5());
        assertNull(anr.getLoad15());
        assertNull(anr.getTime());
        assertNull(anr.getProcess());
        assertNull(anr.getThread());

        anr = mPartialAnr1.merge(mPartialAnr2);
        assertEquals("com.android.package", anr.getPackageName());
        assertEquals("com.android.package/.Activity", anr.getActivity());
        assertEquals("reason", anr.getReason());
        assertEquals(new Double(11), anr.getCpuTotal());
        assertEquals(new Double(13), anr.getCpuUser());
        assertEquals(new Double(17), anr.getCpuKernel());
        assertEquals(new Double(19), anr.getCpuIoWait());
        assertEquals(new Double(23), anr.getCpuIrq());
        assertEquals(new Double(2.0), anr.getLoad1());
        assertEquals(new Double(3.0), anr.getLoad5());
        assertEquals(new Double(5.0), anr.getLoad15());
        assertEquals(new Date(0), anr.getTime());
        assertEquals(new Integer(101), anr.getProcess());
        assertEquals(new Integer(103), anr.getThread());

        anr = mFullAnr1.merge(mFullAnr2);
        assertEquals("com.android.package", anr.getPackageName());
        assertEquals("com.android.package/.Activity", anr.getActivity());
        assertEquals("reason", anr.getReason());
        assertEquals(new Double(11), anr.getCpuTotal());
        assertEquals(new Double(13), anr.getCpuUser());
        assertEquals(new Double(17), anr.getCpuKernel());
        assertEquals(new Double(19), anr.getCpuIoWait());
        assertEquals(new Double(23), anr.getCpuIrq());
        assertEquals(new Double(2.0), anr.getLoad1());
        assertEquals(new Double(3.0), anr.getLoad5());
        assertEquals(new Double(5.0), anr.getLoad15());
        assertEquals(new Date(0), anr.getTime());
        assertEquals(new Integer(101), anr.getProcess());
        assertEquals(new Integer(103), anr.getThread());

        try {
            anr = mFullAnr1.merge(mInconsistentAnr);
            fail("Expected ConflictingItemException");
        } catch (ConflictingItemException e) {
            // Expected
        }
    }
}
