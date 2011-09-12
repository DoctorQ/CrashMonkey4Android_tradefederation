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

import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.RegexTrie;
import com.android.tradefed.util.brillopad.AbstractBlockParser;
import com.android.tradefed.util.brillopad.IBlockParser;
import com.android.tradefed.util.brillopad.ILineParser;
import com.android.tradefed.util.brillopad.ItemList;

import java.util.LinkedList;
import java.util.List;

/**
 * A line-oriented parser that splits an input file into discrete sections and passes each section
 * to an {@link IBlockParser} to parse.
 */
public abstract class AbstractSectionParser extends AbstractBlockParser implements ILineParser {
    private RegexTrie<IBlockParser> mSectionTrie = new RegexTrie<IBlockParser>();
    private IBlockParser mCurrentParser;
    private List<String> mParseBlock = new LinkedList<String>();

    /**
     * Default constructor: use {@link NoopSectionParser} as the initial section parser.
     */
    public AbstractSectionParser() {
        this(new NoopSectionParser());
    }

    /**
     * Use the passed {@link IBlockParser} as the initial section parser.
     *
     * @param defaultParser the IBlockParser to use
     */
    public AbstractSectionParser(IBlockParser defaultParser) {
        mCurrentParser = defaultParser;

        addDefaultSectionParsers(mSectionTrie);
    }

    /**
     * A method to be overridden by subclasses that allows them to specify the parsers to use for
     * the different sections of the file.  It is assumed that a new section will start at a
     * pattern that is recognizable with a single-line regular expression, and that a given section
     * only ends when the subsequent section begins.
     * <p />
     * Parsers can skip sections by specifying that they be parsed by {@link NoopSectionParser}.
     */
    public abstract void addDefaultSectionParsers(RegexTrie<IBlockParser> sectionParsers);

    /**
     * A method to add a given section parser to the set of potential parsers to use.
     * <p />
     * @param parser The {@link IBlockParser} to add
     * @param startPattern The regular expression to trigger this parser on
    */
    public void addSectionParser(IBlockParser parser, String startPattern) {
        mSectionTrie.put(parser, startPattern);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void parseLine(String line, ItemList itemlist) {
        IBlockParser nextParser = mSectionTrie.retrieve(line);

        if (nextParser == null) {
            // no match, so buffer this for the current parser, if there is one
            if (mCurrentParser != null) {
                mParseBlock.add(line);
            } else {
                CLog.w("Line outside of parsed section: %s", line);
            }
        } else {
            // Switching parsers.  Send the parse block to the current parser and then rotate
            if (mCurrentParser != null) {
                mCurrentParser.parseBlock(mParseBlock, itemlist);
                if (!(mCurrentParser instanceof NoopSectionParser)) {
                    CLog.v("Just ran the parser; itemlist is now: %s", itemlist);
                }
            }
            mParseBlock.clear();

            // This stanza is all just debug
            String prev = null;
            String next = nextParser.getClass().getSimpleName();
            if (mCurrentParser != null) {
                prev = mCurrentParser.getClass().getSimpleName();
            }
            CLog.v("Switching parsers from %s to %s for line %s", prev, next, line);
            mCurrentParser = nextParser;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void commit(ItemList itemlist) {
        CLog.w("commit!");
        if (mCurrentParser != null) {
            mCurrentParser.parseBlock(mParseBlock, itemlist);
            if (!(mCurrentParser instanceof NoopSectionParser)) {
                CLog.v("Just committed; itemlist is now: %s", itemlist);
            }
        }
    }
}

