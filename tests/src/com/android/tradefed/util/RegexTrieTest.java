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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import junit.framework.TestCase;

/**
 * Set of unit tests to verify the behavior of the RegexTrie
 */
public class RegexTrieTest extends TestCase {
    private RegexTrie<Integer> mTrie = null;
    private static final Integer mStored = 42;

    @Override
    public void setUp() throws Exception {
        mTrie = new RegexTrie<Integer>();
    }

    public void testStringPattern() {
        mTrie.put(mStored, "[p]art1", "[p]art2", "[p]art3");
        System.err.format("Trie is '%s'\n", mTrie.toString());
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
}

