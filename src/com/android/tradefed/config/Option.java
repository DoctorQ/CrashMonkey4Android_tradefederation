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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotates a field as representing a {@link IConfiguration} option.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Option {

    static final char NO_SHORT_NAME = '0';

    /**
     * The mandatory unique name for this option.
     * This will map to a command line argument prefixed with two '-' characters.
     * For example, an {@link Option} with name 'help' would be specified with '--help' on the
     * command line.
     */
    String name();

    /**
     * Optional abbreviated name for option.
     * This will map to a command line argument prefixed with a single '-'.
     * e.g. "-h" where h = shortName.
     *
     * '0' is reserved to mean the option has no shortName.
     **/
    char shortName() default NO_SHORT_NAME;

    /**
     * User friendly description of the option.
     */
    String description() default "";
}
