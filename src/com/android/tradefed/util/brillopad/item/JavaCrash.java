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

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Item to hold information associated with Java crashes.
 */
public final class JavaCrash extends AbstractLogcatItem {
    private static final String[] ALLOWED_ATTRIBUTES = {"exception", "message", "stack",
            "causeStacks"};

    public JavaCrash() {
        super(ALLOWED_ATTRIBUTES);
    }

    private JavaCrash(Map<String, Object> attributes) {
        super(ALLOWED_ATTRIBUTES, attributes);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public JavaCrash merge(IItem other) throws ConflictingItemException {
        if (this == other) {
            return this;
        }

        Map<String, Object> attributes = this.mergeAttributes(other);
        return new JavaCrash(attributes);
    }

    /**
     * Set the exception of the Java crash.
     *
     * @param exception The exception
     */
    public void setException(String exception) {
        setAttribute("exception", exception);
    }

    /**
     * Gets the exception of the Java crash.
     *
     * @return The exception
     */
    public String getException() {
        return (String) getAttribute("exception");
    }

    /**
     * Sets the message of the crash.
     *
     * @param message The message
     */
    public void setMessage(String message) {
        setAttribute("message", message);
    }

    /**
     * Gets the message of the crash.
     *
     * @return The message
     */
    public String getMessage() {
        return (String) getAttribute("message");
    }

    /**
     * Sets the main stack of the crash.
     *
     * <p>
     * The main stack is the exception, message, and all the at lines describing the crash up until
     * the first 'Caused by' line.
     * </p>
     *
     * @param stack The main stack.
     */
    public void setStack(String stack) {
        setAttribute("stack", stack);
    }

    /**
     * Gets the main stack of the crash.
     *
     * @return The main stack
     * @see JavaCrash#setStack(String)
     */
    public String getStack() {
        return (String) getAttribute("stack");
    }

    /**
     * Sets the cause stacks.
     *
     * <p>
     * The cause stacks are stacks starting with 'Caused by' followed by the exception and the lines
     * describing the crash.
     * </p>
     *
     * @param stacks A list of cause stacks.
     */
    public void setCauseStacks(List<String> stacks) {
        if (stacks == null || !stacks.isEmpty()) {
            setAttribute("causeStacks", stacks);
        }
    }

    /**
     * Add a single cause stack to the end of the list of caused stacks.
     *
     * @param causeStack A single cause stack.
     */
    public void addCauseStack(String causeStack) {
        List<String> stacks = getCauseStacks();
        if (stacks == null) {
            stacks = new LinkedList<String>();
            setAttribute("causeStacks", stacks);
        }
        stacks.add(causeStack);
    }

    /**
     * Gets the list of cause stacks.
     *
     * @return A list of cause stacks or null if there are no cause stacks.
     */
    @SuppressWarnings("unchecked")
    public List<String> getCauseStacks() {
        List<String> stacks;
        try {
            stacks = (List<String>) getAttribute("causeStacks");
        } catch (ClassCastException e) {
            return null;
        }
        if (stacks == null || stacks.isEmpty()) {
            return null;
        }
        return stacks;
    }
}
