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

// note: import used for javadoc
import com.android.tradefed.util.brillopad.item.IItem;

import java.util.List;

/**
 * This interface defines the behavior for a block-oriented parser.  The parser will receive a block
 * of input that it should consider complete.  It should do whatever is necessary to parse the input
 * and commit the parsed data as arbitrarily many {@link IItem} instances in the passed
 * {@link ItemList}.  Furthermore, the parser should be robust against invalid input -- the input
 * format may drift over time.
 */
public interface IBlockParser {

    /**
     * Parses a block of input, and stores the parsed {@link IItem}s in the passed {@link ItemList}.
     * <p/>
     * This method will be called at most once per parser instance.
     */
    public void parseBlock(List<String> input, ItemList itemlist);
}

