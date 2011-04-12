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
     * The resulting array will contain all elements of both <var>startElements</var> and
     * <var>endElements</var> input arrays, in their original order.
     * </p>
     *
     * @param endElements the values to insert at end of array
     * @param startElements the values to insert at beginning of array
     * @return the newly constructed array
     */
    public static String[] buildArray(String[] endElements, String... startElements) {
        String[] newArray = new String[endElements.length + startElements.length];
        System.arraycopy(startElements, 0, newArray, 0, startElements.length);
        System.arraycopy(endElements, 0, newArray, startElements.length, endElements.length);
        return newArray;
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
}
