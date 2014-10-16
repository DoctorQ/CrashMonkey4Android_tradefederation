/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.tradefed.config;

import com.android.tradefed.log.LogUtil.CLog;

import java.lang.reflect.Field;
import java.util.Collection;  // imported for javadoc
import java.util.Map;  // imported for javadoc

/**
 * 当一个变量被多次指定的时候,通过该枚举可以控制其赋值规则,控制其不能变成集合或者map
 * Controls the behavior when an option is specified multiple times.  Note that this enum assumes
 * that the values to be set are not {@link Collection}s or {@link Map}s.
 */
public enum OptionUpdateRule {
    /** once an option is set, subsequent attempts to update it should be ignored. 多次赋值时,该变量的值永远都取第一次的值,意思为一旦赋值,永远不变*/
    FIRST {
        @Override
        Object update(String optionName, Object current, Object update)
                throws ConfigurationException {
            if (current == null) return update;
            CLog.d("Ignoring update for option %s", optionName);
            return current;
        }
    },

    /** if an option is set multiple times, ignore all but the last value. 多次赋值时,该变量的值永远都取最后一次的值,即后面的覆盖前面的*/
    LAST {
        @Override
        Object update(String optionName, Object current, Object update)
                throws ConfigurationException {
            return update;
        }
    },

    /** for {@link Comparable} options, keep the one that compares as the greatest. 多次赋值时,该变量会做比较取最大的值*/
    GREATEST {
        @Override
        Object update(String optionName, Object current, Object update)
                throws ConfigurationException {
            if (current == null) return update;
            if (compare(optionName, current, update) < 0) {
                // current < update; so use the update
                return update;
            } else {
                // current >= update; so keep current
                return current;
            }
        }
    },

    /** for {@link Comparable} options, keep the one that compares as the least.多次赋值时,该变量会做比较取最小的值 */
    LEAST {
        @Override
        Object update(String optionName, Object current, Object update)
                throws ConfigurationException {
            if (current == null) return update;
            if (compare(optionName, current, update) > 0) {
                // current > update; so use the update
                return update;
            } else {
                // current <= update; so keep current
                return current;
            }
        }
    },

    /** throw a {@link ConfigurationException} if this option is set more than once. 该值不可变,如果被多次赋值就报错*/
    IMMUTABLE {
        @Override
        Object update(String optionName, Object current, Object update)
                throws ConfigurationException {
            if (current == null) return update;
            throw new ConfigurationException(String.format(
                    "Attempted to update immutable value (%s) for option \"%s\"", optionName,
                    optionName));
        }
    };

    abstract Object update(String optionName, Object current, Object update)
            throws ConfigurationException;

    /**
      * Takes the current value and the update value, and returns the value to be set.  Assumes
      * that <code>update</code> is never null.
      */
    public Object update(String optionName, Object optionSource, Field field, Object update)
            throws ConfigurationException {
        Object current;
        try {
            current = field.get(optionSource);
        } catch (IllegalAccessException e) {
            throw new ConfigurationException(String.format(
                    "internal error when setting option '%s'", optionName), e);
        }
        return update(optionName, current, update);
    }

    /**
     * Check if the objects are {@link Comparable}, and if so, compare them using
     * {@see Comparable#compareTo}.
     */
    @SuppressWarnings("unchecked")
    private static int compare(String optionName, Object current, Object update)
            throws ConfigurationException {
        Comparable compCurrent;
        if (current instanceof Comparable) {
            compCurrent = (Comparable) current;
        } else {
            throw new ConfigurationException(String.format(
                    "internal error: Class %s for option %s was used with GREATEST or LEAST " +
                    "updateRule, but does not implement Comparable.",
                    current.getClass().getSimpleName(), optionName));
        }

        try {
            return compCurrent.compareTo(update);
        } catch (ClassCastException e) {
            throw new ConfigurationException(String.format(
                    "internal error: Failed to compare %s (%s) and %s (%s)",
                    current.getClass().getName(), current, update.getClass().getName(), update), e);
        }
    }
}

