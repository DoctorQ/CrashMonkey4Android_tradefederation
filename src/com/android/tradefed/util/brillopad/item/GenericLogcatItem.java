/*
 * Copyright (C) 2012 The Android Open Source Project
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

import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

/**
 * A generic item containing attributes for time, process, and thread and can be extended for
 * items such as {@link AnrItem} and {@link JavaCrashItem}.
 */
public abstract class GenericLogcatItem extends GenericItem {
    private static final String EVENT_TIME = "EVENT_TIME";
    private static final String PID = "PID";
    private static final String TID = "TID";
    private static final String APP = "APP";

    private static final Set<String> ATTRIBUTES = new HashSet<String>(Arrays.asList(
            EVENT_TIME, PID, TID, APP));

    /**
     * Constructor for {@link GenericLogcatItem}.
     *
     * @param type The type of the item.
     * @param attributes A list of allowed attributes.
     */
    protected GenericLogcatItem(String type, Set<String> attributes) {
        super(type, getAllAttributes(attributes));
    }

    /**
     * Get the {@link Date} object when the event happened.
     */
    public Date getEventTime() {
        return (Date) getAttribute(EVENT_TIME);
    }

    /**
     * Set the {@link Date} object when the event happened.
     */
    public void setEventTime(Date time) {
        setAttribute(EVENT_TIME, time);
    }

    /**
     * Get the PID of the event.
     */
    public Integer getPid() {
        return (Integer) getAttribute(PID);
    }

    /**
     * Set the PID of the event.
     */
    public void setPid(Integer pid) {
        setAttribute(PID, pid);
    }

    /**
     * Get the TID of the event.
     */
    public Integer getTid() {
        return (Integer) getAttribute(TID);
    }

    /**
     * Set the TID of the event.
     */
    public void setTid(Integer tid) {
        setAttribute(TID, tid);
    }

    /**
     * Get the app or package name of the event.
     */
    public String getApp() {
        return (String) getAttribute(APP);
    }

    /**
     * Set the app or package name of the event.
     */
    public void setApp(String app) {
        setAttribute(APP, app);
    }

    /**
     * Combine an array of attributes with the internal list of attributes.
     */
    private static Set<String> getAllAttributes(Set<String> attributes) {
        Set<String> allAttributes = new HashSet<String>(ATTRIBUTES);
        allAttributes.addAll(attributes);
        return allAttributes;
    }
}
