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


/**
 * This interface defines the behavior for a line-oriented parser.  The parser should parse one
 * line at a time, keeping state internally or stashing it in the {@link ItemList} as desired, and
 * should interpret a {@code commit} call to indicate the end of input.
 */
public interface ILineParser {
    /**
     * parseList should take a line of input, do whatever is needed to parse it, and then either
     * store the results in internal state or commit them to the passed {@link ItemList}.
     * <p />
     * The parser should robustly handle parse errors and make a best effort to return as much
     * useful data as possible.
     *
     * @param line the line to parse
     * @param itemlist the itemlist in which to store any permanent state
     */
    public void parseLine(String line, ItemList itemlist);

    /**
     * A signal that input is finished.
     *
     * @param itemlist the itemlist in which to store any permanent state
     */
    public void commit(ItemList itemlist);
}

