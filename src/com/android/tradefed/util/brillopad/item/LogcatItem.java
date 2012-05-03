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
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * An {@link IItem} used to store logcat info.
 */
public class LogcatItem extends GenericItem {
    public static final String TYPE = "LOGCAT";

    private static final String START_TIME = "START_TIME";
    private static final String STOP_TIME = "STOP_TIME";
    private static final String EVENTS = "EVENTS";

    private static final Set<String> ATTRIBUTES = new HashSet<String>(Arrays.asList(
            START_TIME, STOP_TIME, EVENTS));

    private class ItemList extends LinkedList<IItem> {
        private static final long serialVersionUID = 1088529764741812025L;
    }

    /**
     * The constructor for {@link LogcatItem}.
     */
    public LogcatItem() {
        super(TYPE, ATTRIBUTES);

        setAttribute(EVENTS, new ItemList());
    }

    /**
     * Get the start time of the logcat.
     */
    public Date getStartTime() {
        return (Date) getAttribute(START_TIME);
    }

    /**
     * Set the start time of the logcat.
     */
    public void setStartTime(Date time) {
        setAttribute(START_TIME, time);
    }

    /**
     * Get the stop time of the logcat.
     */
    public Date getStopTime() {
        return (Date) getAttribute(STOP_TIME);
    }

    /**
     * Set the stop time of the logcat.
     */
    public void setStopTime(Date time) {
        setAttribute(STOP_TIME, time);
    }

    /**
     * Get the list of all {@link IItem} events.
     */
    public List<IItem> getEvents() {
        return (ItemList) getAttribute(EVENTS);
    }

    /**
     * Add an {@link IItem} event to the end of the list of events.
     */
    public void addEvent(IItem event) {
        ((ItemList) getAttribute(EVENTS)).add(event);
    }

    /**
     * Get the list of all {@link AnrItem} events.
     */
    public List<AnrItem> getAnrs() {
        List<AnrItem> anrs = new LinkedList<AnrItem>();
        for (IItem item : getEvents()) {
            if (item instanceof AnrItem) {
                anrs.add((AnrItem) item);
            }
        }
        return anrs;
    }

    /**
     * Get the list of all {@link JavaCrashItem} events.
     */
    public List<JavaCrashItem> getJavaCrashes() {
        List<JavaCrashItem> jcs = new LinkedList<JavaCrashItem>();
        for (IItem item : getEvents()) {
            if (item instanceof JavaCrashItem) {
                jcs.add((JavaCrashItem) item);
            }
        }
        return jcs;
    }

    /**
     * Get the list of all {@link NativeCrashItem} events.
     */
    public List<NativeCrashItem> getNativeCrashes() {
        List<NativeCrashItem> ncs = new LinkedList<NativeCrashItem>();
        for (IItem item : getEvents()) {
            if (item instanceof NativeCrashItem) {
                ncs.add((NativeCrashItem) item);
            }
        }
        return ncs;
    }
}
