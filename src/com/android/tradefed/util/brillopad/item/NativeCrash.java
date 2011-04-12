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

import java.util.Map;

/**
 * Item to hold information associated with native crashes.
 */
public final class NativeCrash extends AbstractLogcatItem {
    private static final String[] ALLOWED_ATTRIBUTES = {"stack"};

    public NativeCrash() {
        super(ALLOWED_ATTRIBUTES);
    }

    private NativeCrash(Map<String, Object> attributes) {
        super(ALLOWED_ATTRIBUTES, attributes);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public NativeCrash merge(IItem other) throws ConflictingItemException {
        if (this == other) {
            return this;
        }

        return new NativeCrash(mergeAttributes(other));
    }

    /**
     * Sets the stack of the crash.
     *
     * @param stack The stack.
     */
    public void setStack(String stack) {
        setAttribute("stack", stack);
    }

    /**
     * Gets the stack of the crash.
     *
     * @return The stack
     */
    public String getStack() {
        return (String) getAttribute("stack");
    }
}
