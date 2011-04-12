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
import java.util.LinkedList;
import java.util.List;

import junit.framework.TestCase;

/**
 * Unit test for {@link JavaCrash}
 */
public class JavaCrashTest extends TestCase {
    /** JavaCrash containing no information */
    private JavaCrash mEmptyJc1;
    /** JavaCrash containing no information */
    private JavaCrash mEmptyJc2;
    /** JavaCrash containing partial information */
    private JavaCrash mPartialJc1;
    /** JavaCrash containing partial information complementing mPartialJc1 */
    private JavaCrash mPartialJc2;
    /** JavaCrash containing complete information */
    private JavaCrash mFullJc1;
    /** JavaCrash containing complete information */
    private JavaCrash mFullJc2;
    /** JavaCrash containing inconsistent information */
    private JavaCrash mInconsistentJc;

    /** Expected cause stacks for JavaCrashes */
    private List<String> mCauseStacks;

    /**
     * Initialize the JavaCrashes
     */
    @Override
    public void setUp() {
        mEmptyJc1 = new JavaCrash();
        mEmptyJc2 = new JavaCrash();

        mPartialJc1 = new JavaCrash();
        mPartialJc1.setException("Exception");
        mPartialJc1.setMessage("message");
        mPartialJc1.setStack("Exception: message\n\tat source.java");

        mPartialJc2 = new JavaCrash();
        mPartialJc2.addCauseStack("Caused by: Exception1");
        mPartialJc2.addCauseStack("Caused by: Exception2");
        mPartialJc2.setTime(new Date(0));
        mPartialJc2.setProcess(5);
        mPartialJc2.setThread(7);

        mFullJc1 = new JavaCrash();
        mFullJc1.setException("Exception");
        mFullJc1.setMessage("message");
        mFullJc1.setStack("Exception: message\n\tat source.java");
        mFullJc1.addCauseStack("Caused by: Exception1");
        mFullJc1.addCauseStack("Caused by: Exception2");
        mFullJc1.setTime(new Date(0));
        mFullJc1.setProcess(5);
        mFullJc1.setThread(7);

        mFullJc2 = new JavaCrash();
        mFullJc2.setException("Exception");
        mFullJc2.setMessage("message");
        mFullJc2.setStack("Exception: message\n\tat source.java");
        mFullJc2.addCauseStack("Caused by: Exception1");
        mFullJc2.addCauseStack("Caused by: Exception2");
        mFullJc2.setTime(new Date(0));
        mFullJc2.setProcess(5);
        mFullJc2.setThread(7);

        mInconsistentJc = new JavaCrash();
        mInconsistentJc.setException("DifferentException");
        mInconsistentJc.setMessage("different message");
        mInconsistentJc.setStack("DifferentException: different message\n\tat source.java");
        mInconsistentJc.addCauseStack("Caused by: DifferentException1");
        mInconsistentJc.addCauseStack("Caused by: DifferentException2");
        mInconsistentJc.setTime(new Date(10));
        mInconsistentJc.setProcess(13);
        mInconsistentJc.setThread(17);

        mCauseStacks = new LinkedList<String>();
        mCauseStacks.add("Caused by: Exception1");
        mCauseStacks.add("Caused by: Exception2");
    }

    /**
     * Test for {@link JavaCrash#merge(IItem)}
     */
    public void testMerge() throws ConflictingItemException {
        JavaCrash jc = mEmptyJc1.merge(mEmptyJc1);
        assertNull(jc.getException());
        assertNull(jc.getMessage());
        assertNull(jc.getStack());
        assertNull(jc.getCauseStacks());
        assertNull(jc.getTime());
        assertNull(jc.getProcess());
        assertNull(jc.getThread());

        try {
            jc = mEmptyJc1.merge(null);
            fail("Expected ConflictingItemException");
        } catch (ConflictingItemException e) {
            // Expected
        }

        try {
            jc = mEmptyJc1.merge(EasyMock.createMock(IItem.class));
            fail("Expected ConflictingItemException");
        } catch (ConflictingItemException e) {
            // Expected
        }

        jc = mEmptyJc1.merge(mEmptyJc2);
        assertNull(jc.getException());
        assertNull(jc.getMessage());
        assertNull(jc.getStack());
        assertNull(jc.getCauseStacks());
        assertNull(jc.getTime());
        assertNull(jc.getProcess());
        assertNull(jc.getThread());

        jc = mPartialJc1.merge(mPartialJc2);
        assertEquals("Exception", jc.getException());
        assertEquals("message", jc.getMessage());
        assertEquals("Exception: message\n\tat source.java", jc.getStack());
        assertEquals(mCauseStacks, jc.getCauseStacks());
        assertEquals(new Date(0), jc.getTime());
        assertEquals(new Integer(5), jc.getProcess());
        assertEquals(new Integer(7), jc.getThread());

        jc = mFullJc1.merge(mFullJc2);
        assertEquals("Exception", jc.getException());
        assertEquals("message", jc.getMessage());
        assertEquals("Exception: message\n\tat source.java", jc.getStack());
        assertEquals(mCauseStacks, jc.getCauseStacks());
        assertEquals(new Date(0), jc.getTime());
        assertEquals(new Integer(5), jc.getProcess());
        assertEquals(new Integer(7), jc.getThread());

        try {
            jc = mFullJc1.merge(mInconsistentJc);
            fail("Expected ConflictingItemException");
        } catch (ConflictingItemException e) {
            // Expected
        }
    }

    /**
     * Test for {@link JavaCrash#setCauseStacks(java.util.List)},
     * {@link JavaCrash#addCauseStack(String)}, and {@link JavaCrash#getCauseStacks()}.
     */
    public void testCauseStack() {
        JavaCrash jc = new JavaCrash();
        jc.setCauseStacks(new LinkedList<String>());
        assertNull(jc.getAttribute("causeStacks"));
        assertNull(jc.getCauseStacks());

        List<String> stacks = new LinkedList<String>();
        stacks.add("1");
        stacks.add("2");
        jc.setCauseStacks(stacks);
        assertEquals(stacks, jc.getCauseStacks());
        jc.addCauseStack("3");
        assertEquals(3, jc.getCauseStacks().size());
        assertEquals("3", jc.getCauseStacks().get(2));
    }
}
