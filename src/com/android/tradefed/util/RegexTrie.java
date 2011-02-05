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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The RegexTrie is a trie where each _stored_ segment of the key is a regex {@link Pattern}.  Thus,
 * the full _stored_ key is a List<Pattern> rather than a String as in a standard trie.  Note that
 * the {@link get(Object key)} requires a List<String>, which will be matched against the
 * {@link Pattern}s, rather than checked for equality as in a standard trie.  It will likely perform
 * poorly for large datasets.
 */
public class RegexTrie<V> {
    private V mValue = null;
    private Map<CompPattern, RegexTrie<V>> mChildren =
            new LinkedHashMap<CompPattern, RegexTrie<V>>();

    /**
     * Patterns aren't comparable by default, which prevents you from retrieving them from a
     * HashTable.  This is a simple stub class that makes a Pattern with a working
     * {@link CompPattern#equals()} method.
     */
    static class CompPattern {
        protected final Pattern mPattern;

        CompPattern(Pattern pattern) {
            if (pattern == null) {
                throw new NullPointerException();
            }
            mPattern = pattern;
        }

        @Override
        public boolean equals(Object other) {
            Pattern otherPat;
            if (other instanceof Pattern) {
                otherPat = (Pattern) other;
            } else if (other instanceof CompPattern) {
                CompPattern otherCPat = (CompPattern) other;
                otherPat = otherCPat.mPattern;
            } else {
                return false;
            }
            return mPattern.toString().equals(otherPat.toString());
        }

        @Override
        public int hashCode() {
            return mPattern.toString().hashCode();
        }

        @Override
        public String toString() {
            return String.format("CP(%s)", mPattern.toString());
        }

        public Matcher matcher(String string) {
            return mPattern.matcher(string);
        }
    }

    public void clear() {
        mValue = null;
        for (RegexTrie child : mChildren.values()) {
            child.clear();
        }
        mChildren.clear();
    }

    boolean containsKey(String... strings) {
        return retrieve(strings) != null;
    }

    V recursivePut(V value, List<Pattern> patterns) {
        // Cases:
        // 1) patterns is empty -- set our value
        // 2) patterns is non-empty -- recurse downward, creating a child if necessary
        if (patterns.isEmpty()) {
            V oldValue = mValue;
            mValue = value;
            return oldValue;
        } else {
            CompPattern curKey = new CompPattern(patterns.get(0));
            List<Pattern> nextKeys = patterns.subList(1, patterns.size());

            // Create a new child to handle
            RegexTrie<V> nextChild = mChildren.get(curKey);
            if (nextChild == null) {
                nextChild = new RegexTrie<V>();
                mChildren.put(curKey, nextChild);
            }
            return nextChild.recursivePut(value, nextKeys);
        }
    }

    /**
     * Add an entry to the trie.
     *
     * @param value The value to set
     * @param patterns The sequence of {@link Pattern}s that must be sequentially matched to
     *        retrieve the associated {@code value}
     */
    public V put(V value, Pattern... patterns) {
        if (patterns.length == 0) {
            throw new IllegalArgumentException("pattern list must be non-empty");
        }
        List<Pattern> pList = Arrays.asList(patterns);
        return recursivePut(value, pList);
    }

    /**
     * This helper method takes a list of regular expressions as {@link String}s and compiles them
     * on-the-fly before adding the subsequent {@link Pattern}s to the trie
     *
     * @param value The value to set
     * @param patterns The sequence of regular expressions (as {@link String}s) that must be
     *        sequentially matched to retrieve the associated {@code value}.  Each String will be
     *        compiled as a {@link Pattern} before invoking {@link #put(V, Pattern...)}.
     */
    public V put(V value, String... regexen) {
        Pattern[] patterns = new Pattern[regexen.length];
        for (int i = 0; i < regexen.length; ++i) {
            patterns[i] = Pattern.compile(regexen[i]);
        }
        return put(value, patterns);
    }

    V recursiveRetrieve(List<List<String>> groups, List<String> strings) {
        // Cases:
        // 1) strings is empty -- return our value
        // 2) strings is non-empty -- find the first child that matches, recurse downward
        if (strings.isEmpty()) {
            return mValue;
        } else {
            String curKey = strings.get(0);
            List<String> nextKeys = strings.subList(1, strings.size());

            for (Map.Entry<CompPattern, RegexTrie<V>> child : mChildren.entrySet()) {
                Matcher matcher = child.getKey().matcher(curKey);
                if (matcher.matches()) {
                    if (groups != null) {
                        List<String> captures = new ArrayList<String>(matcher.groupCount());
                        for (int i = 0; i < matcher.groupCount(); i++) {
                            // i+1 since group 0 is the entire matched string
                            captures.add(matcher.group(i+1));
                        }
                        groups.add(captures);
                    }

                    return child.getValue().recursiveRetrieve(groups, nextKeys);
                }
            }

            // no match
            return null;
        }
    }

    /**
     * Fetch a value from the trie, by matching the provided sequence of {@link String}s to a
     * sequence of {@link Pattern}s stored in the trie.
     *
     * @param strings A sequence of {@link String}s to match
     * @return The associated value, or {@code null} if no value was found
     */
    public V retrieve(String... strings) {
        return retrieve(null, strings);
    }

    /**
     * Fetch a value from the trie, by matching the provided sequence of {@link String}s to a
     * sequence of {@link Pattern}s stored in the trie.  This version of the method also returns
     * a {@link List} of capture groups for each {@link Pattern} that was matched.
     * <p />
     * Each entry in the outer List corresponds to one level of {@code Pattern} in the trie.
     * For each level, the list of capture groups will be stored.  If there were no captures
     * for a particular level, an empty list will be stored.
     * <p />
     * Note that {@code groups} will be {@link List#clear()}ed before the retrieval begins.
     * Also, if the retrieval fails after a partial sequence of matches, {@code groups} will
     * still reflect the capture groups from the partial match.
     *
     * @param groups A {@code List<List<String>>} through which capture groups will be returned.
     * @param strings A sequence of {@link String}s to match
     * @return The associated value, or {@code null} if no value was found
     */
    public V retrieve(List<List<String>> groups, String... strings) {
        if (strings.length == 0) {
            throw new IllegalArgumentException("string list must be non-empty");
        }
        List<String> sList = Arrays.asList(strings);
        if (groups != null) {
            groups.clear();
        }
        return recursiveRetrieve(groups, sList);
    }

    @Override
    public String toString() {
        return String.format("{V: %s, C: %s}", mValue, mChildren);
    }
}

