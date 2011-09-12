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

import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Unit tests for {@link AbstractBlockParser}
 */
public class AbstractBlockParserTest extends TestCase {
    private static final String COMMIT_MSG = "COMMITTERS! COMMITTERS! COMMITTERS!";

    private List<String> mExpectedLines = new ArrayList<String>();
    private ItemList mItemList = null;

    private class BlockParser extends AbstractBlockParser {
        @Override
        public void parseLine(String line, ItemList itemlist) {
            String expectedLine = mExpectedLines.remove(0);
            assertEquals(expectedLine, line);
            assertEquals(mItemList, itemlist);
        }

        @Override
        public void commit(ItemList itemlist) {
            String expectedLine = mExpectedLines.remove(0);
            assertEquals(COMMIT_MSG, expectedLine);
            assertEquals(mItemList, itemlist);
        }
    }

    /**
     * Simply verify that the AbstractBlockParser turns a single parseBlock call into the
     * appropriate sequence of parseLine calls, followed by a commit() call
     */
    public void testSimple() {
        List<String> lines = Arrays.asList("alpha", "beta", "gamma", "delta");
        mItemList = new ItemList();
        BlockParser parser = new BlockParser();
        mExpectedLines.addAll(lines);
        mExpectedLines.add(COMMIT_MSG);

        parser.parseBlock(lines, mItemList);
        assertEquals(0, mExpectedLines.size());
    }
}

