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

/**
 * Interface for all items that are created by any parser.
 *
 * <p>
 * Items may contain one or more items.  For example, a Bugreport item will contain a Logcat item,
 * which itself might contain ANR, JavaCrash, and NativeCrash items.
 * </p>
 */
public interface IItem {

    /**
     * Merges the item and another into an item with the most complete information.
     *
     * <p>
     * Goes through each field in the item preferring non-null fields over null fields.
     * </p>
     *
     * @param other The other item
     * @return The product of both items combined.
     * @throws ConflictingItemException If the two items are not consistent.
     */
    public IItem merge(IItem other) throws ConflictingItemException;

    /**
     * Checks that the item and another are consistent.
     *
     * <p>
     * Consistency means that no individual fields in either item conflict with the other.
     * However, one item might contain more complete information.  Two items of different types
     * are never consistent.
     * </p>
     *
     * @param other The other item.
     * @return True if the objects are the same type and all the fields are either equal or one of
     * the fields is null.
     */
    public boolean isConsistent(IItem other);
}
