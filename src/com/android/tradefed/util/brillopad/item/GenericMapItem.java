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

import java.util.HashMap;

/**
 * An IItem that just represents a simple key/value map
 */
@SuppressWarnings("serial")
public class GenericMapItem<K, V> extends HashMap<K,V> implements IItem {
    private String mType = null;

    /**
     * No-op zero-arg constructor
     */
    public GenericMapItem() {}

    /**
     * Convenience constructor that sets the type
     */
    public GenericMapItem(String type) {
        setType(type);
    }

    /**
     * Set the self-reported type that this {@link GenericMapItem} represents.
     */
    public void setType(String type) {
        mType = type;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getType() {
        return mType;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IItem merge(IItem other) {
        // FIXME
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isConsistent(IItem other) {
        // FIXME
        return true;
    }
}
