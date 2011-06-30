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
package com.android.tradefed.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * Utility methods for arrays
 */
public class ArrayUtil {

    private ArrayUtil() {
    }

    /**
     * Build an array from the provided contents.
     *
     * <p>
     * The resulting array will be the concatenation of <var>arrays</var> input arrays, in their
     * original order.
     * </p>
     *
     * @param arrays the arrays to concatenate
     * @return the newly constructed array
     */
    public static String[] buildArray(String[]... arrays) {
        int length = 0;
        for (String[] array : arrays) {
            length += array.length;
        }
        String[] newArray = new String[length];
        int offset = 0;
        for (String[] array : arrays) {
            System.arraycopy(array, 0, newArray, offset, array.length);
            offset += array.length;
        }
        return newArray;
    }

    /**
     * Convert a varargs list/array to an {@link List}.  This is useful for building instances of
     * {@link List} by hand.  Note that this differs from {@link java.util.Arrays#asList} in that
     * the returned array is mutable.
     *
     * @param inputAry an array, or a varargs list
     * @return a {@link List} instance with the identical contents
     */
    public static <T> List<T> list(T... inputAry) {
        List<T> retList = new ArrayList<T>(inputAry.length);
        for (T item : inputAry) {
            retList.add(item);
        }
        return retList;
    }

    public static String join(String sep, List<String> pieces) {
        StringBuilder sb = new StringBuilder();
        Iterator iter = pieces.iterator();
        if (iter.hasNext()) {
            sb.append(iter.next());
        }
        while (iter.hasNext()) {
            sb.append(sep);
            sb.append(iter.next());
        }
        return sb.toString();
    }

    public static String join(String sep, String... pieces) {
        return join(sep, Arrays.asList(pieces));
    }
}

