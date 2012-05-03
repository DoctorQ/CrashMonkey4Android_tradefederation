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
 * An {@link IItem} used to store Bugreport info.
 */
public class BugreportItem extends GenericItem {
    public static final String TYPE = "BUGREPORT";

    private static final String TIME = "TIME";
    private static final String MEM_INFO = "MEM_INFO";
    private static final String PROCRANK = "PROCRANK";
    private static final String SYSTEM_LOG = "SYSTEM_LOG";
    private static final String SYSTEM_PROPS = "SYSTEM_PROPS";

    private static final Set<String> ATTRIBUTES = new HashSet<String>(Arrays.asList(
            TIME, MEM_INFO, PROCRANK, SYSTEM_LOG, SYSTEM_PROPS));

    /**
     * The constructor for {@link BugreportItem}.
     */
    public BugreportItem() {
        super(TYPE, ATTRIBUTES);
    }

    /**
     * Get the time of the bugreport.
     */
    public Date getTime() {
        return (Date) getAttribute(TIME);
    }

    /**
     * Set the time of the bugreport.
     */
    public void setTime(Date time) {
        setAttribute(TIME, time);
    }

    /**
     * Get the {@link MemInfoItem} of the bugreport.
     */
    public MemInfoItem getMemInfo() {
        return (MemInfoItem) getAttribute(MEM_INFO);
    }

    /**
     * Set the {@link MemInfoItem} of the bugreport.
     */
    public void setMemInfo(MemInfoItem memInfo) {
        setAttribute(MEM_INFO, memInfo);
    }

    /**
     * Get the {@link ProcrankItem} of the bugreport.
     */
    public ProcrankItem getProcrank() {
        return (ProcrankItem) getAttribute(PROCRANK);
    }

    /**
     * Set the {@link ProcrankItem} of the bugreport.
     */
    public void setProcrank(ProcrankItem procrank) {
        setAttribute(PROCRANK, procrank);
    }

    /**
     * Get the {@link LogcatItem} of the bugreport.
     */
    public LogcatItem getSystemLog() {
        return (LogcatItem) getAttribute(SYSTEM_LOG);
    }

    /**
     * Set the {@link LogcatItem} of the bugreport.
     */
    public void setSystemLog(LogcatItem systemLog) {
        setAttribute(SYSTEM_LOG, systemLog);
    }

    /**
     * Get the {@link SystemPropsItem} of the bugreport.
     */
    public SystemPropsItem getSystemProps() {
        return (SystemPropsItem) getAttribute(SYSTEM_PROPS);
    }

    /**
     * Set the {@link SystemPropsItem} of the bugreport.
     */
    public void setSystemProps(SystemPropsItem systemProps) {
        setAttribute(SYSTEM_PROPS, systemProps);
    }
}
