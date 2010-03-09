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

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * An option parser that parses options from command line arguments.
 */
class ArgsOptionParser extends OptionParser {

    static final String SHORT_NAME_PREFIX = "-";
    static final String OPTION_NAME_PREFIX = "--";

    /**
     * Creates a {@link ArgsOptionParser} for a single object.
     *
     * @param optionSource the config object.
     * @throws ConfigurationException if config object is improperly configured.
     */
    public ArgsOptionParser(Object optionSource) throws ConfigurationException {
        super(optionSource);
    }

    /**
     * Parses the command-line arguments 'args', setting the @Option fields of the 'optionSource'
     * provided to the constructor.
     *
     * @returns a {@link List} of the positional arguments left over after processing all options.
     * @throws ConfigurationException if error occurred parsing the arguments.
     */
    public List<String> parse(String[] args) throws ConfigurationException {
        return parseOptions(Arrays.asList(args).iterator());
    }

    private List<String> parseOptions(Iterator<String> args) throws ConfigurationException {
        final List<String> leftovers = new ArrayList<String>();

        // Scan 'args'.
        while (args.hasNext()) {
            final String arg = args.next();
            if (arg.equals(OPTION_NAME_PREFIX)) {
                // "--" marks the end of options and the beginning of positional arguments.
                break;
            } else if (arg.startsWith(OPTION_NAME_PREFIX)) {
                // A long option.
                parseLongOption(arg, args);
            } else if (arg.startsWith(SHORT_NAME_PREFIX)) {
                // A short option.
                parseGroupedShortOptions(arg, args);
            } else {
                // The first non-option marks the end of options.
                leftovers.add(arg);
                break;
            }
        }

        // Package up the leftovers.
        while (args.hasNext()) {
            leftovers.add(args.next());
        }
        return leftovers;
    }

    private void parseLongOption(String arg, Iterator<String> args) throws ConfigurationException {
        // remove prefix to just get name
        String name = arg.replaceFirst("^" + OPTION_NAME_PREFIX, "");
        String value = null;

        // Support "--name=value" as well as "--name value".
        final int equalsIndex = name.indexOf('=');
        if (equalsIndex != -1) {
            value = name.substring(equalsIndex + 1);
            name = name.substring(0, equalsIndex);
        }

        final Field field = fieldForArg(name);
        if (field == null) {
            // not an option for this source
            return;
        }
        final Handler handler = getHandler(field.getGenericType());
        if (value == null) {
            if (handler.isBoolean()) {
                value = arg.startsWith("--no-") ? "false" : "true";
            } else {
                value = grabNextValue(args, name, field);
            }
        }
        setValue(optionSource, field, arg, handler, value);
    }

    // Given boolean options a and b, and non-boolean option f, we want to allow:
    // -ab
    // -abf out.txt
    // -abfout.txt
    // (But not -abf=out.txt --- POSIX doesn't mention that either way, but GNU expressly forbids it.)
    private void parseGroupedShortOptions(String arg, Iterator<String> args)
            throws ConfigurationException {
        for (int i = 1; i < arg.length(); ++i) {
            final String name = SHORT_NAME_PREFIX + arg.charAt(i);
            // TODO: fix this to handle a field that was not found
            final Field field = fieldForArg(name);
            final Handler handler = getHandler(field.getGenericType());
            String value;
            if (handler.isBoolean()) {
                value = "true";
            } else {
                // We need a value. If there's anything left, we take the rest of this "short option".
                if (i + 1 < arg.length()) {
                    value = arg.substring(i + 1);
                    i = arg.length() - 1;
                } else {
                    value = grabNextValue(args, name, field);
                }
            }
            setValue(optionSource, field, arg, handler, value);
        }
    }

    /**
     * Returns the next element of 'args' if there is one. Uses 'name' and 'field' to
     * construct a helpful error message.
     *
     * @param args
     * @param name
     * @param field
     *
     * @returns
     */
    private String grabNextValue(Iterator<String> args, String name, Field field) {
        if (!args.hasNext()) {
            final String type = field.getType().getSimpleName().toLowerCase();
            throw new RuntimeException("option '" + name + "' requires a " + type + " argument");
        }
        return args.next();
    }

}
