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
import java.util.Collection;
import java.util.List;
import java.util.ListIterator;

/**
 * Populates {@link Option} fields from parsed command line arguments.
 * <p/>
 * Strings in the passed-in String[] are parsed left-to-right. Each String is classified as a short
 * option (such as "-v"), a long option (such as "--verbose"), an argument to an option (such as
 * "out.txt" in "-f out.txt"), or a non-option positional argument.
 * <p/>
 * Each option argument must map to exactly one {@link Option} field. A long option maps to the
 * {@link Option#name()}, and a short option maps to {@link Option#shortName()}. Each
 * {@link Option#name()} and {@link Option@shortName()} must be unique with respect to all other
 * {@link Option} fields.
 * <p/>
 * A simple short option is a "-" followed by a short option character. If the option requires an
 * argument (which is true of any non-boolean option), it may be written as a separate parameter,
 * but need not be. That is, "-f out.txt" and "-fout.txt" are both acceptable.
 * <p/>
 * It is possible to specify multiple short options after a single "-" as long as all (except
 * possibly the last) do not require arguments.
 * <p/>
 * A long option begins with "--" followed by several characters. If the option requires an
 * argument, it may be written directly after the option name, separated by "=", or as the next
 * argument. (That is, "--file=out.txt" or "--file out.txt".)
 * <p/>
 * A boolean long option '--name' automatically gets a '--no-name' companion. Given an option
 * "--flag", then, "--flag", "--no-flag", "--flag=true" and "--flag=false" are all valid, though
 * neither "--flag true" nor "--flag false" are allowed (since "--flag" by itself is sufficient,
 * the following "true" or "false" is interpreted separately). You can use "yes" and "no" as
 * synonyms for "true" and "false".
 * <p/>
 * Each String not starting with a "-" and not a required argument of a previous option is a
 * non-option positional argument, as are all successive Strings. Each String after a "--" is a
 * non-option positional argument.
 * <p/>
 * The fields corresponding to options are updated as their options are processed. Any remaining
 * positional arguments are returned as a List<String>.
 * <p/>
 * Here's a simple example:
 * <p/>
 * <pre>
 * // Non-@Option fields will be ignored.
 * class Options {
 *     @Option(name = "quiet", shortName = 'q')
 *     boolean quiet = false;
 *
 *     // Here the user can use --no-color.
 *     @Option(name = "color")
 *     boolean color = true;
 *
 *     @Option(name = "mode", shortName = 'm')
 *     String mode = "standard; // Supply a default just by setting the field.
 *
 *     @Option(name = "port", shortName = 'p')
 *     int portNumber = 8888;
 *
 *     // There's no need to offer a short name for rarely-used options.
 *     @Option(name = "timeout" )
 *     double timeout = 1.0;
 *
 *     @Option(name = "output-file", shortName = 'o' })
 *     File output;
 *
 *     // Multiple options are added to the collection.
 *     // The collection field itself must be non-null.
 *     @Option(name = "input-file", shortName = 'i')
 *     List<File> inputs = new ArrayList<File>();
 *
 * }
 *
 * Options options = new Options();
 * List<String> posArgs = new OptionParser(options).parse("--input-file", "/tmp/file1.txt");
 * for (File inputFile : options.inputs) {
 *     if (!options.quiet) {
 *        ...
 *     }
 *     ...
 *
 * }
 *
 * </pre>
 * See also:
 * <ul>
 *   <li>the getopt(1) man page
 *   <li>Python's "optparse" module (http://docs.python.org/library/optparse.html)
 *   <li>the POSIX "Utility Syntax Guidelines" (http://www.opengroup.org/onlinepubs/000095399/basedefs/xbd_chap12.html#tag_12_02)
 *   <li>the GNU "Standards for Command Line Interfaces" (http://www.gnu.org/prep/standards/standards.html#Command_002dLine-Interfaces)
 * </ul>
 *
 * @see {@link OptionSetter}
 */
public class ArgsOptionParser extends OptionSetter {

    static final String SHORT_NAME_PREFIX = "-";
    static final String OPTION_NAME_PREFIX = "--";

    /**
     * Creates a {@link ArgsOptionParser} for a collection of objects.
     *
     * @param optionSource the config objects.
     * @throws ConfigurationException if config objects is improperly configured.
     */
    public ArgsOptionParser(Collection<Object> optionSources) throws ConfigurationException {
        super(optionSources);
    }

    /**
     * Creates a {@link ArgsOptionParser} for one or more objects.
     *
     * @param optionSource the config objects.
     * @throws ConfigurationException if config objects is improperly configured.
     */
    public ArgsOptionParser(Object... optionSources) throws ConfigurationException {
        super(optionSources);
    }

    /**
     * Parses the command-line arguments 'args', setting the @Option fields of the 'optionSource'
     * provided to the constructor.
     *
     * @returns a {@link List} of the positional arguments left over after processing all options.
     * @throws ConfigurationException if error occurred parsing the arguments.
     */
    public List<String> parse(String[] args) throws ConfigurationException {
        return parseOptions(Arrays.asList(args).listIterator());
    }

    private List<String> parseOptions(ListIterator<String> args) throws ConfigurationException {
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

    private void parseLongOption(String arg, ListIterator<String> args)
            throws ConfigurationException {
        // remove prefix to just get name
        String name = arg.replaceFirst("^" + OPTION_NAME_PREFIX, "");
        String value = null;

        // Support "--name=value" as well as "--name value".
        final int equalsIndex = name.indexOf('=');
        if (equalsIndex != -1) {
            value = name.substring(equalsIndex + 1);
            name = name.substring(0, equalsIndex);
        }

        if (value == null) {
            if (isBooleanOption(name)) {
                value = name.startsWith(BOOL_FALSE_PREFIX) ? "false" : "true";
            } else {
                value = grabNextValue(args, name);
            }
        }
        setOptionValue(name, value);
    }

    // Given boolean options a and b, and non-boolean option f, we want to allow:
    // -ab
    // -abf out.txt
    // -abfout.txt
    // (But not -abf=out.txt --- POSIX doesn't mention that either way, but GNU expressly forbids
    // it.)
    private void parseGroupedShortOptions(String arg, ListIterator<String> args)
            throws ConfigurationException {
        for (int i = 1; i < arg.length(); ++i) {
            final String name = String.valueOf(arg.charAt(i));
            String value;
            if (isBooleanOption(name)) {
                value = "true";
            } else {
                // We need a value. If there's anything left, we take the rest of this
                // "short option".
                if (i + 1 < arg.length()) {
                    value = arg.substring(i + 1);
                    i = arg.length() - 1;
                } else {
                    value = grabNextValue(args, name);
                }
            }
            setOptionValue(name, value);
        }
    }

    /**
     * Returns the next element of 'args' if there is one. Uses 'name' to construct a helpful error
     * message.
     *
     * @param args the arg iterator
     * @param name the name of current argument
     * @throws ConfigurationException if no argument is present
     *
     * @returns the next element
     */
    private String grabNextValue(ListIterator<String> args, String name)
            throws ConfigurationException {
        if (!args.hasNext()) {
            String type = getTypeForOption(name);
            throw new ConfigurationException(String.format("option '%s' requires a '%s' argument",
                    name, type));
        }
        return args.next();
    }

    /**
     * Output help text for all {@link Option} fields in <param>optionClass</param>
     *
     * @param optionClass the class to print help text for
     * @return a String containing user-friendly help text for all Option fields
     */
    public static String getOptionHelp(final Class<?> optionClass) {
        StringBuilder out = new StringBuilder();
        String eol = System.getProperty("line.separator");
        for (Field field : optionClass.getDeclaredFields()) {
            if (field.isAnnotationPresent(Option.class)) {
                final Option option = field.getAnnotation(Option.class);
                out.append(String.format("    %s%s: %s", OPTION_NAME_PREFIX,
                        option.name(), option.description()));
                out.append(eol);
            }
        }
        return out.toString();
    }
}
