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

import com.android.tradefed.util.ArrayUtil;

import java.util.Date;
import java.util.Map;

/**
 * Base item for items which can be found in the logcat.  Provides common methods for getting the
 * time, process and thread of the item.
 */
public abstract class AbstractLogcatItem extends AbstractItem {
    private static final String[] ALLOWED_ATTRIBUTES = {"time", "process", "thread"};

    protected AbstractLogcatItem(String[] allowedAttributes) {
        super(ArrayUtil.buildArray(ALLOWED_ATTRIBUTES, allowedAttributes));
    }

    protected AbstractLogcatItem(String[] allowedAttributes, Map<String, Object> attributes) {
        super(ArrayUtil.buildArray(ALLOWED_ATTRIBUTES, allowedAttributes), attributes);
    }

    /**
     * Set the time when the event occurred.
     *
     * @param time The time of the event
     */
    public void setTime(Date time) {
        setAttribute("time", time);
    }

    /**
     * Get the time when the event occurred.
     *
     * @return The time of the event
     */
    public Date getTime() {
        return (Date) getAttribute("time");
    }

    /**
     * Set the process ID that the event occurred on.
     *
     * @param process The process ID
     */
    public void setProcess(Integer process) {
        setAttribute("process", process);
    }

    /**
     * Get the process ID that the event occurred on.
     *
     * @return The process ID
     */
    public Integer getProcess() {
        return (Integer) getAttribute("process");
    }

    /**
     * Set the thread ID that the event occurred on.
     *
     * @param thread The thread ID
     */
    public void setThread(Integer thread) {
        setAttribute("thread", thread);
    }

    /**
     * Get the thread ID that the event occurred on.
     *
     * @return The thread ID
     */
    public Integer getThread() {
        return (Integer) getAttribute("thread");
    }
}
