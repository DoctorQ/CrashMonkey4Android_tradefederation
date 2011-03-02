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
 * Item to hold information associated with ANRs.
 */
public final class Anr extends AbstractLogcatItem {
    private static final String[] ALLOWED_ATTRIBUTES = {"packageName", "activity", "reason",
            "cpuTotal", "cpuUser", "cpuKernel", "cpuIoWait", "cpuIrq", "load1", "load5", "load15"};

    public Anr() {
        super(ALLOWED_ATTRIBUTES);
    }

    private Anr(Map<String, Object> attributes) {
        super(ALLOWED_ATTRIBUTES, attributes);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Anr merge(IItem other) throws ConflictingItemException {
        if (this == other) {
            return this;
        }

        return new Anr(mergeAttributes(other));
    }

    /**
     * Set the package name that caused the ANR.
     *
     * @param packageName The package name
     */
    public void setPackageName(String packageName) {
        setAttribute("packageName", packageName);
    }

    /**
     * Gets the package name of the ANR.
     *
     * @return The package name
     */
    public String getPackageName() {
        return (String) getAttribute("packageName");
    }

    /**
     * Sets the activity that caused the ANR.
     *
     * @param activity The activity
     */
    public void setActivity(String activity) {
        setAttribute("activity", activity);
    }

    /**
     * Gets the activity of the ANR.
     *
     * @return The activity
     */
    public String getActivity() {
        return (String) getAttribute("activity");
    }

    /**
     * Sets the reason that caused the ANR.
     *
     * @param reason The reason
     */
    public void setReason(String reason) {
        setAttribute("reason", reason);
    }

    /**
     * Gets the reason of the ANR.
     *
     * @return The reason
     */
    public String getReason() {
        return (String) getAttribute("reason");
    }

    /**
     * Sets the total CPU usage at the time of the ANR.
     *
     * @param cpuTotal The total CPU usage
     */
    public void setCpuTotal(Double cpuTotal) {
        setAttribute("cpuTotal", cpuTotal);
    }

    /**
     * Gets the total CPU usage of the ANR.
     *
     * @return The total CPU usage
     */
    public Double getCpuTotal() {
        return (Double) getAttribute("cpuTotal");
    }

    /**
     * Sets the user CPU usage at the time of the ANR.
     *
     * @param cpuUser The user CPU usage
     */
    public void setCpuUser(Double cpuUser) {
        setAttribute("cpuUser", cpuUser);
    }

    /**
     * Gets the user CPU usage of the ANR.
     *
     * @return The user CPU usage
     */
    public Double getCpuUser() {
        return (Double) getAttribute("cpuUser");
    }

    /**
     * Sets the kernel CPU usage at the time of the ANR.
     *
     * @param cpuKernel The kernel CPU usage
     */
    public void setCpuKernel(Double cpuKernel) {
        setAttribute("cpuKernel", cpuKernel);
    }

    /**
     * Gets the kernel CPU usage of the ANR.
     *
     * @return The kernel CPU usage
     */
    public Double getCpuKernel() {
        return (Double) getAttribute("cpuKernel");
    }

    /**
     * Sets the IO wait CPU usage at the time of the ANR.
     *
     * @param cpuIoWait The IO wait CPU usage
     */
    public void setCpuIoWait(Double cpuIoWait) {
        setAttribute("cpuIoWait", cpuIoWait);
    }

    /**
     * Gets the IO wait CPU usage of the ANR.
     *
     * @return The IO wait CPU usage
     */
    public Double getCpuIoWait() {
        return (Double) getAttribute("cpuIoWait");
    }

    /**
     * Sets the IRQ CPU usage at the time of the ANR.
     *
     * @param cpuIrq The IRQ CPU usage
     */
    public void setCpuIrq(Double cpuIrq) {
        setAttribute("cpuIrq", cpuIrq);
    }

    /**
     * Gets the IRQ CPU usage of the ANR.
     *
     * @return The IRQ CPU usage
     */
    public Double getCpuIrq() {
        return (Double) getAttribute("cpuIrq");
    }

    /**
     * Set the 1 minute load average.
     *
     * @param load1 The 1 minute load average
     */
    public void setLoad1(Double load1) {
        setAttribute("load1", load1);
    }

    /**
     * Gets the 1 minute load average.
     *
     * @return The 1 minute load average
     */
    public Double getLoad1() {
        return (Double) getAttribute("load1");
    }

    /**
     * Set the 5 minute load average.
     *
     * @param load5 The 5 minute load average
     */
    public void setLoad5(Double load5) {
        setAttribute("load5", load5);
    }

    /**
     * Gets the 5 minute load average.
     *
     * @return The 5 minute load average
     */
    public Double getLoad5() {
        return (Double) getAttribute("load5");
    }

    /**
     * Set the 15 minute load average.
     *
     * @param load15 The 15 minute load average
     */
    public void setLoad15(Double load15) {
        setAttribute("load15", load15);
    }

    /**
     * Gets the 15 minute load average.
     *
     * @return The 15 minute load average
     */
    public Double getLoad15() {
        return (Double) getAttribute("load15");
    }
}
