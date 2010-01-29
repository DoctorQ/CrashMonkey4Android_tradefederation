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

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.HashMap;

/**
 *
 * TODO: split up this javadoc into different objects
 *
 * Parses command line options.
 *
 * Strings in the passed-in String[] are parsed left-to-right. Each
 * String is classified as a short option (such as "-v"), a long
 * option (such as "--verbose"), an argument to an option (such as
 * "out.txt" in "-f out.txt"), or a non-option positional argument.
 *
 * A simple short option is a "-" followed by a short option
 * character. If the option requires an argument (which is true of any
 * non-boolean option), it may be written as a separate parameter, but
 * need not be. That is, "-f out.txt" and "-fout.txt" are both
 * acceptable.
 *
 * It is possible to specify multiple short options after a single "-"
 * as long as all (except possibly the last) do not require arguments.
 *
 * A long option begins with "--" followed by several characters. If
 * the option requires an argument, it may be written directly after
 * the option name, separated by "=", or as the next argument. (That
 * is, "--file=out.txt" or "--file out.txt".)
 *
 * A boolean long option '--name' automatically gets a '--no-name'
 * companion. Given an option "--flag", then, "--flag", "--no-flag",
 * "--flag=true" and "--flag=false" are all valid, though neither
 * "--flag true" nor "--flag false" are allowed (since "--flag" by
 * itself is sufficient, the following "true" or "false" is
 * interpreted separately). You can use "yes" and "no" as synonyms for
 * "true" and "false".
 *
 * Each String not starting with a "-" and not a required argument of
 * a previous option is a non-option positional argument, as are all
 * successive Strings. Each String after a "--" is a non-option
 * positional argument.
 *
 * Parsing of numeric fields such byte, short, int, long, float, and
 * double fields is supported. This includes both unboxed and boxed
 * versions (e.g. int vs Integer). If there is a problem parsing the
 * argument to match the desired type, a runtime exception is thrown.
 *
 * File option fields are supported by simply wrapping the string
 * argument in a File object without testing for the existence of the
 * file.
 *
 * Parameterized Collection fields such as List<File> and Set<String>
 * are supported as long as the parameter type is otherwise supported
 * by the option parser. The collection field should be initialized
 * with an appropriate collection instance.
 *
 * The fields corresponding to options are updated as their options
 * are processed. Any remaining positional arguments are returned as a
 * List<String>.
 *
 * Here's a simple example:
 *
 * // This doesn't need to be a separate class, if your application doesn't warrant it.
 * // Non-@Option fields will be ignored.
 * class Options {
 *     @Option(names = { "-q", "--quiet" })
 *     boolean quiet = false;
 *
 *     // Boolean options require a long name if it's to be possible to explicitly turn them off.
 *     // Here the user can use --no-color.
 *     @Option(names = { "--color" })
 *     boolean color = true;
 *
 *     @Option(names = { "-m", "--mode" })
 *     String mode = "standard; // Supply a default just by setting the field.
 *
 *     @Option(names = { "-p", "--port" })
 *     int portNumber = 8888;
 *
 *     // There's no need to offer a short name for rarely-used options.
 *     @Option(names = { "--timeout" })
 *     double timeout = 1.0;
 *
 *     @Option(names = { "-o", "--output-file" })
 *     File output;
 *
 *     // Multiple options are added to the collection.
 *     // The collection field itself must be non-null.
 *     @Option(names = { "-i", "--input-file" })
 *     List<File> inputs = new ArrayList<File>();
 *
 * }
 *
 * class Main {
 *     public static void main(String[] args) {
 *         Options options = new Options();
 *         List<String> inputFilenames = new OptionParser(options).parse(args);
 *         for (String inputFilename : inputFilenames) {
 *             if (!options.quiet) {
 *                 ...
 *             }
 *             ...
 *         }
 *     }
 * }
 *
 * See also:
 *
 *  the getopt(1) man page
 *  Python's "optparse" module (http://docs.python.org/library/optparse.html)
 *  the POSIX "Utility Syntax Guidelines" (http://www.opengroup.org/onlinepubs/000095399/basedefs/xbd_chap12.html#tag_12_02)
 *  the GNU "Standards for Command Line Interfaces" (http://www.gnu.org/prep/standards/standards.html#Command_002dLine-Interfaces)
 *
 *
 *  ported from dalvik.runner.OptionParser
 */
class OptionParser {

    private static final HashMap<Class<?>, Handler> handlers = new HashMap<Class<?>, Handler>();
    static {
        handlers.put(boolean.class, new BooleanHandler());
        handlers.put(Boolean.class, new BooleanHandler());

        handlers.put(byte.class, new ByteHandler());
        handlers.put(Byte.class, new ByteHandler());
        handlers.put(short.class, new ShortHandler());
        handlers.put(Short.class, new ShortHandler());
        handlers.put(int.class, new IntegerHandler());
        handlers.put(Integer.class, new IntegerHandler());
        handlers.put(long.class, new LongHandler());
        handlers.put(Long.class, new LongHandler());

        handlers.put(float.class, new FloatHandler());
        handlers.put(Float.class, new FloatHandler());
        handlers.put(double.class, new DoubleHandler());
        handlers.put(Double.class, new DoubleHandler());

        handlers.put(String.class, new StringHandler());
        handlers.put(File.class, new FileHandler());
    }
    @SuppressWarnings("unchecked")
    Handler getHandler(Type type) throws ConfigurationException {
        if (type instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) type;
            Class rawClass = (Class<?>) parameterizedType.getRawType();
            if (!Collection.class.isAssignableFrom(rawClass)) {
                throw new ConfigurationException("cannot handle non-collection parameterized type "
                        + type);
            }
            Type actualType = parameterizedType.getActualTypeArguments()[0];
            if (!(actualType instanceof Class)) {
                throw new ConfigurationException("cannot handle nested parameterized type " + type);
            }
            return getHandler(actualType);
        }
        if (type instanceof Class) {
            if (Collection.class.isAssignableFrom((Class) type)) {
                // could handle by just having a default of treating
                // contents as String but consciously decided this
                // should be an error
                throw new ConfigurationException(
                        String.format("Cannot handle non-parameterized collection %s. %s", type,
                                "use a generic Collection to specify a desired element type"));
            }
            return handlers.get((Class<?>) type);
        }
        throw new ConfigurationException(String.format("cannot handle unknown field type %s",
                type));
    }

    protected final Object optionSource;
    private final HashMap<String, Field> optionMap;

    /**
     * Constructs a new OptionParser for setting the @Option fields of 'optionSource'.
     * @throws ConfigurationException
     */
    public OptionParser(Object optionSource) throws ConfigurationException {
        this.optionSource = optionSource;
        this.optionMap = makeOptionMap();
    }

    protected Field fieldForArg(String name) throws ConfigurationException {
        final Field field = optionMap.get(name);
        if (field == null) {
            throw new ConfigurationException("unrecognized option '" + name + "'");
        }
        return field;
    }

    @SuppressWarnings("unchecked")
    protected static void setValue(Object object, Field field, String arg, Handler handler,
            String valueText) throws ConfigurationException {

        Object value = handler.translate(valueText);
        if (value == null) {
            final String type = field.getType().getSimpleName().toLowerCase();
            throw new ConfigurationException("couldn't convert '" + valueText + "' to a " + type
                    + " for option '" + arg + "'");
        }
        try {
            field.setAccessible(true);
            if (Collection.class.isAssignableFrom(field.getType())) {
                Collection collection = (Collection)field.get(object);
                collection.add(value);
            } else {
                field.set(object, value);
            }
        } catch (IllegalAccessException ex) {
            throw new ConfigurationException("internal error", ex);
        }
    }

    /**
     * Cache the available options and report any problems with the options themselves right away.
     * @return
     * @throws ConfigurationException
     */
    private HashMap<String, Field> makeOptionMap() throws ConfigurationException {
        final HashMap<String, Field> optionMap = new HashMap<String, Field>();
        final Class<?> optionClass = optionSource.getClass();
        for (Field field : optionClass.getDeclaredFields()) {
            if (field.isAnnotationPresent(Option.class)) {
                final Option option = field.getAnnotation(Option.class);
                addNameToMap(optionMap, option.name(), field);
                if (option.shortName() != null && !"".equals(option.shortName())) {
                    addNameToMap(optionMap, option.shortName(), field);
                }
            }
        }
        return optionMap;
    }

    /**
     * @param string
     * @param field
     * @throws ConfigurationException
     */
    private void addNameToMap(HashMap<String, Field> optionMap, String name, Field field)
            throws ConfigurationException {
        if (optionMap.put(name, field) != null) {
            throw new ConfigurationException("found multiple @Options sharing the name '" + name
                    + "'");
        }
        if (getHandler(field.getGenericType()) == null) {
            throw new ConfigurationException("unsupported @Option field type '" + field.getType()
                    + "'");
        }
    }

    abstract static class Handler {
        // Only BooleanHandler should ever override this.
        boolean isBoolean() {
            return false;
        }

        /**
         * Returns an object of appropriate type for the given Handle, corresponding to 'valueText'.
         * Returns null on failure.
         */
        abstract Object translate(String valueText);
    }

    static class BooleanHandler extends Handler {
        @Override boolean isBoolean() {
            return true;
        }

        Object translate(String valueText) {
            if (valueText.equalsIgnoreCase("true") || valueText.equalsIgnoreCase("yes")) {
                return Boolean.TRUE;
            } else if (valueText.equalsIgnoreCase("false") || valueText.equalsIgnoreCase("no")) {
                return Boolean.FALSE;
            }
            return null;
        }
    }

    static class ByteHandler extends Handler {
        Object translate(String valueText) {
            try {
                return Byte.parseByte(valueText);
            } catch (NumberFormatException ex) {
                return null;
            }
        }
    }

    static class ShortHandler extends Handler {
        Object translate(String valueText) {
            try {
                return Short.parseShort(valueText);
            } catch (NumberFormatException ex) {
                return null;
            }
        }
    }

    static class IntegerHandler extends Handler {
        Object translate(String valueText) {
            try {
                return Integer.parseInt(valueText);
            } catch (NumberFormatException ex) {
                return null;
            }
        }
    }

    static class LongHandler extends Handler {
        Object translate(String valueText) {
            try {
                return Long.parseLong(valueText);
            } catch (NumberFormatException ex) {
                return null;
            }
        }
    }

    static class FloatHandler extends Handler {
        Object translate(String valueText) {
            try {
                return Float.parseFloat(valueText);
            } catch (NumberFormatException ex) {
                return null;
            }
        }
    }

    static class DoubleHandler extends Handler {
        Object translate(String valueText) {
            try {
                return Double.parseDouble(valueText);
            } catch (NumberFormatException ex) {
                return null;
            }
        }
    }

    static class StringHandler extends Handler {
        Object translate(String valueText) {
            return valueText;
        }
    }

    static class FileHandler extends Handler {
        Object translate(String valueText) {
            return new File(valueText);
        }
    }
}
