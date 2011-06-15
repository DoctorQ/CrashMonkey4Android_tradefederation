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

import com.android.tradefed.util.brillopad.item.IItem;

import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * A generic datastore which simply contains a {@link List} of {@link IItem}s parsed by one or more
 * of the Brillopad parsers.  It also provides convenient methods to find specific {@link IItem}s.
 */
public class ItemList {
    private List<IItem> mItems = new LinkedList<IItem>();

    /**
     * Append the specified item to this {@code ItemList}.
     *
     * @param IItem the item to append
     */
    public void addItem(IItem item) {
        if (item == null) {
            throw new NullPointerException();
        }
        mItems.add(item);
    }

    /**
     * Retrieve all of the {@link IItem}s stored in this {@code ItemList}
     *
     * @return the {@link List} of {@link IItem}s stored in the list
     */
    public List<IItem> getItems() {
        return mItems;
    }

    /**
     * Return all of the {@link IItem}s whose type is matched by the supplied regular expression
     * This is a convenience shim for {@see #getItemsByType(Pattern)}.
     *
     * @see #getFirstItemByType(Pattern)
     * @param regex a regular expression to try to match against the {@link IItem} Type fields
     * @return the {@link List} of matching {@link IItem}s
     * @throws PatternSyntaxException if the regular expression is invalid
     */
    public List<IItem> getItemsByType(String regex) throws PatternSyntaxException {
        return getItemsByType(Pattern.compile(regex));
    }

    /**
     * Return all of the {@link IItem}s whose type is matched by the supplied {@link Pattern}
     *
     * @param filter a {@link Pattern} to try to match against the {@link IItem} Type fields
     * @return the {@link List} of matching {@link IItem}s
     * @throws PatternSyntaxException if the regular expression is invalid
     */
    public List<IItem> getItemsByType(Pattern filter) throws PatternSyntaxException {
        List<IItem> results = new LinkedList<IItem>();

        for (IItem item : mItems) {
            String section = item.getType();
            if (section == null) {
                continue;
            }
            Matcher m = filter.matcher(section);
            if (m.matches()) {
                results.add(item);
            }
        }

        return results;
    }

    /**
     * Return the first {@link IItem} whose type is matched by the supplied {@link Pattern}.
     * This is a convenience shim for {@see #getFirstItemByType(Pattern)}.
     *
     * @see #getFirstItemByType(Pattern)
     * @param filter a {@link Pattern} to try to match against the {@link IItem} Type fields
     * @return the first matching {@link IItem}, or {@code null} if none matched
     * @throws PatternSyntaxException if the regular expression is invalid
     */
    public IItem getFirstItemByType(String regex) throws PatternSyntaxException {
        return getFirstItemByType(Pattern.compile(regex));
    }

    /**
     * Return the first {@link IItem} whose type is matched by the supplied {@link Pattern}.  This
     * method is intended as an alternative to the {@see #getItemsByType} methods for cases where:
     * <ul>
     *   <li>The caller assumes that only at most one {@link IItem} will match</li>
     *   <li>Or the caller doesn't care which of multiple matching {@link IItem}s it receives</li>
     * </ul>
     *
     * @param filter a {@link Pattern} to try to match against the {@link IItem} Type fields
     * @return the first matching {@link IItem}, or {@code null} if none matched
     * @throws PatternSyntaxException if the regular expression is invalid
     */
    public IItem getFirstItemByType(Pattern filter) throws PatternSyntaxException {
        for (IItem item : mItems) {
            String section = item.getType();
            if (section == null) {
                continue;
            }
            Matcher m = filter.matcher(section);
            if (m.matches()) {
                // winner
                return item;
            }
        }

        // no matches, sad
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return mItems.toString();
    }
}

