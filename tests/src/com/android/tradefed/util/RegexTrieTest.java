/*
 * Copyright (C) 2010 The Android Open Source Project
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
package com.android.tradefed.util;

import com.android.tradefed.util.RegexTrie.CompPattern;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;

import junit.framework.TestCase;

/**
 * Set of unit tests to verify the behavior of the RegexTrie
 */
public class RegexTrieTest extends TestCase {
    private RegexTrie<Integer> mTrie = null;
    private static final Integer mStored = 42;
    private static final List<String> mNullList = list((String)null);

    @Override
    public void setUp() throws Exception {
        mTrie = new RegexTrie<Integer>();
    }

    private void dumpTrie(RegexTrie trie) {
        System.err.format("Trie is '%s'\n", trie.toString());
    }

    public void testStringPattern() {
        mTrie.put(mStored, "[p]art1", "[p]art2", "[p]art3");
        Integer retrieved = mTrie.retrieve("part1", "part2", "part3");
        assertEquals(mStored, retrieved);
    }

    public void testAlternation_single() {
        mTrie.put(mStored, "alpha|beta");
        Integer retrieved;
        retrieved = mTrie.retrieve("alpha");
        assertEquals(mStored, retrieved);
        retrieved = mTrie.retrieve("beta");
        assertEquals(mStored, retrieved);
        retrieved = mTrie.retrieve("alpha|beta");
        assertNull(retrieved);
        retrieved = mTrie.retrieve("gamma");
        assertNull(retrieved);
        retrieved = mTrie.retrieve("alph");
        assertNull(retrieved);
    }

    public void testAlternation_multiple() {
        mTrie.put(mStored, "a|alpha", "b|beta");
        Integer retrieved;
        retrieved = mTrie.retrieve("a", "b");
        assertEquals(mStored, retrieved);
        retrieved = mTrie.retrieve("a", "beta");
        assertEquals(mStored, retrieved);
        retrieved = mTrie.retrieve("alpha", "b");
        assertEquals(mStored, retrieved);
        retrieved = mTrie.retrieve("alpha", "beta");
        assertEquals(mStored, retrieved);

        retrieved = mTrie.retrieve("alpha");
        assertNull(retrieved);
        retrieved = mTrie.retrieve("beta");
        assertNull(retrieved);
        retrieved = mTrie.retrieve("alpha", "bet");
        assertNull(retrieved);
    }

    private static List<String> list(String... strings) {
        List<String> retList = new ArrayList<String>(strings.length);
        for (String str : strings) {
            retList.add(str);
        }
        return retList;
    }

    public void testGroups_fullMatch() {
        mTrie.put(mStored, "a|(alpha)", "b|(beta)");
        Integer retrieved;
        List<List<String>> groups = new ArrayList<List<String>>();

        retrieved = mTrie.retrieve(groups, "a", "b");
        assertEquals(mStored, retrieved);
        assertEquals(2, groups.size());
        assertEquals(mNullList, groups.get(0));
        assertEquals(mNullList, groups.get(1));

        retrieved = mTrie.retrieve(groups, "a", "beta");
        assertEquals(mStored, retrieved);
        assertEquals(2, groups.size());
        assertEquals(mNullList, groups.get(0));
        assertEquals(list("beta"), groups.get(1));

        retrieved = mTrie.retrieve(groups, "alpha", "b");
        assertEquals(mStored, retrieved);
        assertEquals(2, groups.size());
        assertEquals(list("alpha"), groups.get(0));
        assertEquals(mNullList, groups.get(1));

        retrieved = mTrie.retrieve(groups, "alpha", "beta");
        assertEquals(mStored, retrieved);
        assertEquals(2, groups.size());
        assertEquals(list("alpha"), groups.get(0));
        assertEquals(list("beta"), groups.get(1));
    }

    public void testGroups_partialMatch() {
        mTrie.put(mStored, "a|(alpha)", "b|(beta)");
        Integer retrieved;
        List<List<String>> groups = new ArrayList<List<String>>();

        retrieved = mTrie.retrieve(groups, "alpha");
        assertNull(retrieved);
        assertEquals(1, groups.size());
        assertEquals(list("alpha"), groups.get(0));

        retrieved = mTrie.retrieve(groups, "beta");
        assertNull(retrieved);
        assertEquals(0, groups.size());

        retrieved = mTrie.retrieve(groups, "alpha", "bet");
        assertNull(retrieved);
        assertEquals(1, groups.size());
        assertEquals(list("alpha"), groups.get(0));

        retrieved = mTrie.retrieve(groups, "alpha", "betar");
        assertNull(retrieved);
        assertEquals(1, groups.size());
        assertEquals(list("alpha"), groups.get(0));

        retrieved = mTrie.retrieve(groups, "alpha", "beta", "gamma");
        assertNull(retrieved);
        assertEquals(2, groups.size());
        assertEquals(list("alpha"), groups.get(0));
        assertEquals(list("beta"), groups.get(1));
    }

    public void testMultiChild() {
        mTrie.put(mStored + 1, "a", "b");
        mTrie.put(mStored + 2, "a", "c");
        dumpTrie(mTrie);

        Object retrieved;
        retrieved = mTrie.retrieve("a", "b");
        assertEquals(mStored + 1, retrieved);
        retrieved = mTrie.retrieve("a", "c");
        assertEquals(mStored + 2, retrieved);
    }

    /**
     * Make sure that {@link CompPattern#equals} works as expected.  Shake a proverbial fist at Java
     */
    public void testCompPattern_equality() {
        String regex = "regex";
        Pattern p1 = Pattern.compile(regex);
        Pattern p2 = Pattern.compile(regex);
        Pattern pOther = Pattern.compile("other");
        CompPattern cp1 = new CompPattern(p1);
        CompPattern cp2 = new CompPattern(p2);
        CompPattern cpOther = new CompPattern(pOther);

        // This is the problem with Pattern as implemented
        assertFalse(p1.equals(p2));
        assertFalse(p2.equals(p1));

        // Make sure that wrapped patterns with the same regex are considered equivalent
        assertTrue(cp2.equals(p1));
        assertTrue(cp2.equals(p2));
        assertTrue(cp2.equals(cp1));

        // And make sure that wrapped patterns with different regexen are still considered different
        assertFalse(cp2.equals(pOther));
        assertFalse(cp2.equals(cpOther));
    }

    public void testCompPattern_hashmap() {
        HashMap<CompPattern, Integer> map = new HashMap<CompPattern, Integer>();
        String regex = "regex";
        Pattern p1 = Pattern.compile(regex);
        Pattern p2 = Pattern.compile(regex);
        Pattern pOther = Pattern.compile("other");
        CompPattern cp1 = new CompPattern(p1);
        CompPattern cp2 = new CompPattern(p2);
        CompPattern cpOther = new CompPattern(pOther);

        map.put(cp1, mStored);
        assertTrue(map.containsKey(cp1));
        assertTrue(map.containsKey(cp2));
        assertFalse(map.containsKey(cpOther));

        map.put(cpOther, mStored);
        assertEquals(map.size(), 2);
        assertTrue(map.containsKey(cp1));
        assertTrue(map.containsKey(cp2));
        assertTrue(map.containsKey(cpOther));
    }
}

