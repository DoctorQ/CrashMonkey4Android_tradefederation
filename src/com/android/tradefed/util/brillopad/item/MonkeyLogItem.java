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
 * An {@link IItem} used to store monkey log info.
 */
public class MonkeyLogItem extends GenericItem {

    private class StringSet extends HashSet<String> {
        private static final long serialVersionUID = -2206822563602989856L;
    }

    public enum DroppedCategory {
        KEYS,
        POINTERS,
        TRACKBALLS,
        FLIPS,
        ROTATIONS
    }

    private static final String TYPE = "MONKEY_LOG";

    private static final String START_TIME = "START_TIME";
    private static final String STOP_TIME = "STOP_TIME";
    private static final String PACKAGES = "PACKAGES";
    private static final String CATEGORIES = "CATEGORIES";
    private static final String THROTTLE = "THROTTLE";
    private static final String SEED = "SEED";
    private static final String TARGET_COUNT = "TARGET_COUNT";
    private static final String IGNORE_SECURITY_EXCEPTIONS = "IGNORE_SECURITY_EXCEPTIONS";
    private static final String TOTAL_DURATION = "TOTAL_TIME";
    private static final String START_UPTIME_DURATION = "START_UPTIME";
    private static final String STOP_UPTIME_DURATION = "STOP_UPTIME";
    private static final String IS_FINISHED = "IS_FINISHED";
    private static final String NO_ACTIVITIES = "NO_ACTIVITIES";
    private static final String INTERMEDIATE_COUNT = "INTERMEDIATE_COUNT";
    private static final String FINAL_COUNT = "FINAL_COUNT";
    private static final String CRASH = "CRASH";

    private static final Set<String> ATTRIBUTES = new HashSet<String>(Arrays.asList(
            START_TIME, STOP_TIME, PACKAGES, CATEGORIES, THROTTLE, SEED, TARGET_COUNT,
            IGNORE_SECURITY_EXCEPTIONS, TOTAL_DURATION, START_UPTIME_DURATION, STOP_UPTIME_DURATION,
            IS_FINISHED, NO_ACTIVITIES, INTERMEDIATE_COUNT, FINAL_COUNT, CRASH,
            DroppedCategory.KEYS.toString(),
            DroppedCategory.POINTERS.toString(),
            DroppedCategory.TRACKBALLS.toString(),
            DroppedCategory.FLIPS.toString(),
            DroppedCategory.ROTATIONS.toString()));

    /**
     * The constructor for {@link MonkeyLogItem}.
     */
    public MonkeyLogItem() {
        super(TYPE, ATTRIBUTES);

        setAttribute(PACKAGES, new StringSet());
        setAttribute(CATEGORIES, new StringSet());
        setAttribute(THROTTLE, 0);
        setAttribute(IGNORE_SECURITY_EXCEPTIONS, false);
        setAttribute(IS_FINISHED, false);
        setAttribute(NO_ACTIVITIES, false);
        setAttribute(INTERMEDIATE_COUNT, 0);
    }

    /**
     * Get the start time of the monkey log.
     */
    public Date getStartTime() {
        return (Date) getAttribute(START_TIME);
    }

    /**
     * Set the start time of the monkey log.
     */
    public void setStartTime(Date time) {
        setAttribute(START_TIME, time);
    }

    /**
     * Get the stop time of the monkey log.
     */
    public Date getStopTime() {
        return (Date) getAttribute(STOP_TIME);
    }

    /**
     * Set the stop time of the monkey log.
     */
    public void setStopTime(Date time) {
        setAttribute(STOP_TIME, time);
    }

    /**
     * Get the set of packages that the monkey is run on.
     */
    public Set<String> getPackages() {
        return (StringSet) getAttribute(PACKAGES);
    }

    /**
     * Add a package to the set that the monkey is run on.
     */
    public void addPackage(String thePackage) {
        ((StringSet) getAttribute(PACKAGES)).add(thePackage);
    }

    /**
     * Get the set of categories that the monkey is run on.
     */
    public Set<String> getCategories() {
        return (StringSet) getAttribute(CATEGORIES);
    }

    /**
     * Add a category to the set that the monkey is run on.
     */
    public void addCategory(String category) {
        ((StringSet) getAttribute(CATEGORIES)).add(category);
    }

    /**
     * Get the throttle for the monkey run.
     */
    public int getThrottle() {
        return (Integer) getAttribute(THROTTLE);
    }

    /**
     * Set the throttle for the monkey run.
     */
    public void setThrottle(int throttle) {
        setAttribute(THROTTLE, throttle);
    }

    /**
     * Get the seed for the monkey run.
     */
    public Integer getSeed() {
        return (Integer) getAttribute(SEED);
    }

    /**
     * Set the seed for the monkey run.
     */
    public void setSeed(int seed) {
        setAttribute(SEED, seed);
    }

    /**
     * Get the target count for the monkey run.
     */
    public Integer getTargetCount() {
        return (Integer) getAttribute(TARGET_COUNT);
    }

    /**
     * Set the target count for the monkey run.
     */
    public void setTargetCount(int count) {
        setAttribute(TARGET_COUNT, count);
    }

    /**
     * Get if the ignore security exceptions flag is set for the monkey run.
     */
    public boolean getIgnoreSecurityExceptions() {
        return (Boolean) getAttribute(IGNORE_SECURITY_EXCEPTIONS);
    }

    /**
     * Set if the ignore security exceptions flag is set for the monkey run.
     */
    public void setIgnoreSecurityExceptions(boolean ignore) {
        setAttribute(IGNORE_SECURITY_EXCEPTIONS, ignore);
    }

    /**
     * Get the total duration of the monkey run in milliseconds.
     */
    public Long getTotalDuration() {
        return (Long) getAttribute(TOTAL_DURATION);
    }

    /**
     * Set the total duration of the monkey run in milliseconds.
     */
    public void setTotalDuration(long time) {
        setAttribute(TOTAL_DURATION, time);
    }

    /**
     * Get the start uptime duration of the monkey run in milliseconds.
     */
    public Long getStartUptimeDuration() {
        return (Long) getAttribute(START_UPTIME_DURATION);
    }

    /**
     * Set the start uptime duration of the monkey run in milliseconds.
     */
    public void setStartUptimeDuration(long uptime) {
        setAttribute(START_UPTIME_DURATION, uptime);
    }

    /**
     * Get the stop uptime duration of the monkey run in milliseconds.
     */
    public Long getStopUptimeDuration() {
        return (Long) getAttribute(STOP_UPTIME_DURATION);
    }

    /**
     * Set the stop uptime duration of the monkey run in milliseconds.
     */
    public void setStopUptimeDuration(long uptime) {
        setAttribute(STOP_UPTIME_DURATION, uptime);
    }

    /**
     * Get if the monkey run finished without crashing.
     */
    public boolean getIsFinished() {
        return (Boolean) getAttribute(IS_FINISHED);
    }

    /**
     * Set if the monkey run finished without crashing.
     */
    public void setIsFinished(boolean finished) {
        setAttribute(IS_FINISHED, finished);
    }

    /**
     * Get if the monkey run aborted due to no activies to run.
     */
    public boolean getNoActivities() {
        return (Boolean) getAttribute(NO_ACTIVITIES);
    }

    /**
     * Set if the monkey run aborted due to no activies to run.
     */
    public void setNoActivities(boolean noActivities) {
        setAttribute(NO_ACTIVITIES, noActivities);
    }


    /**
     * Get the intermediate count for the monkey run.
     * <p>
     * This count starts at 0 and increments every 100 events. This number should be within 100 of
     * the final count.
     * </p>
     */
    public int getIntermediateCount() {
        return (Integer) getAttribute(INTERMEDIATE_COUNT);
    }

    /**
     * Set the intermediate count for the monkey run.
     * <p>
     * This count starts at 0 and increments every 100 events. This number should be within 100 of
     * the final count.
     * </p>
     */
    public void setIntermediateCount(int count) {
        setAttribute(INTERMEDIATE_COUNT, count);
    }

    /**
     * Get the final count for the monkey run.
     */
    public Integer getFinalCount() {
        return (Integer) getAttribute(FINAL_COUNT);
    }

    /**
     * Set the final count for the monkey run.
     */
    public void setFinalCount(int count) {
        setAttribute(FINAL_COUNT, count);
    }

    /**
     * Get the dropped events count for a {@link DroppedCategory} for the monkey run.
     */
    public Integer getDroppedCount(DroppedCategory category) {
        return (Integer) getAttribute(category.toString());
    }

    /**
     * Set the dropped events count for a {@link DroppedCategory} for the monkey run.
     */
    public void setDroppedCount(DroppedCategory category, int count) {
        setAttribute(category.toString(), count);
    }

    /**
     * Get the {@link AnrItem} or {@link JavaCrashItem} for the monkey run or null if there was no
     * crash.
     */
    public GenericLogcatItem getCrash() {
        return (GenericLogcatItem) getAttribute(CRASH);
    }

    /**
     * Set the {@link AnrItem} or {@link JavaCrashItem} for the monkey run.
     */
    public void setCrash(GenericLogcatItem crash) {
        setAttribute(CRASH, crash);
    }
}
