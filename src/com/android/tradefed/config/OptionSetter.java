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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Populates {@link Option} fields.
 * <p/>
 * Setting of numeric fields such byte, short, int, long, float, and double fields is supported.
 * This includes both unboxed and boxed versions (e.g. int vs Integer). If there is a problem
 * setting the argument to match the desired type, a {@link ConfigurationException} is thrown.
 * <p/>
 * File option fields are supported by simply wrapping the string argument in a File object without
 * testing for the existence of the file.
 * <p/>
 * Parameterized Collection fields such as List<File> and Set<String> are supported as long as the
 * parameter type is otherwise supported by the option setter. The collection field should be
 * initialized with an appropriate collection instance.
 * <p/>
 * All fields will be processed, including public, protected, default (package) access, private and
 * inherited fields.
 * <p/>
 *
 * ported from dalvik.runner.OptionParser
 * @see {@link ArgsOptionParser}
 */
class OptionSetter {

    static final String BOOL_FALSE_PREFIX = "no-";
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
    private Handler getHandler(Type type) throws ConfigurationException {
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

    private final Collection<Object> mOptionSources;
    private final Map<String, OptionField> mOptionMap;

    private static class OptionField {
        final Object mOptionSource;
        final Field mField;

        OptionField(Object optionSource, Field field) {
            mOptionSource = optionSource;
            mField = field;
        }
    }

    /**
     * Constructs a new OptionParser for setting the @Option fields of 'optionSources'.
     * @throws ConfigurationException
     */
    public OptionSetter(Object... optionSources) throws ConfigurationException {
        this(Arrays.asList(optionSources));
    }

    /**
     * Constructs a new OptionParser for setting the @Option fields of 'optionSources'.
     * @throws ConfigurationException
     */
    public OptionSetter(Collection<Object> optionSources) throws ConfigurationException {
        mOptionSources = optionSources;
        mOptionMap = makeOptionMap();
    }

    private OptionField fieldForArg(String name) throws ConfigurationException {
        OptionField field = mOptionMap.get(name);
        if (field == null) {
            throw new ConfigurationException(String.format("Could not find option with name %s",
                    name));
        }
        return field;
    }

    /**
     * Returns a string describing the type of the field with given name.
     *
     * @param name the {@link Option} field name
     * @return a {@link String} describing the field's type
     * @throws ConfigurationException if field could not be found
     */
    public String getTypeForOption(String name) throws ConfigurationException {
        return fieldForArg(name).mField.getType().getSimpleName().toLowerCase();
    }

    /**
     * Sets the value for an option.
     * @param optionName the name of Option to set
     * @param valueText the value
     * @throws ConfigurationException if Option cannot be found or valueText is wrong type
     */
    @SuppressWarnings("unchecked")
    public void setOptionValue(String optionName, String valueText) throws ConfigurationException {
        OptionField optionField = fieldForArg(optionName);
        Field field = optionField.mField;
        Handler handler = getHandler(field.getGenericType());
        Object value = handler.translate(valueText);
        if (value == null) {
            final String type = field.getType().getSimpleName().toLowerCase();
            throw new ConfigurationException(
                    String.format("Couldn't convert '%s' to a %s for option '%s'", valueText, type,
                            optionName));
        }
        try {
            field.setAccessible(true);
            if (Collection.class.isAssignableFrom(field.getType())) {
                Collection collection = (Collection)field.get(optionField.mOptionSource);
                collection.add(value);
            } else {
                field.set(optionField.mOptionSource, value);
            }
        } catch (IllegalAccessException e) {
            throw new ConfigurationException(String.format(
                    "internal error when setting option '%s'", optionName), e);
        }
    }

    /**
     * Cache the available options and report any problems with the options themselves right away.
     * @return a {@link Map} of {@link Option} field name to {@link OptionField}
     * @throws ConfigurationException if any {@link Option} are incorrectly specified
     */
    private Map<String, OptionField> makeOptionMap() throws ConfigurationException {
        final Map<String, OptionField> optionMap = new HashMap<String, OptionField>();
        for (Object objectSource: mOptionSources) {
            addOptionsForObject(objectSource, optionMap);
        }
        return optionMap;
    }

    /**
     * Adds all option fields (both declared and inherited) to the
     * <var>optionMap</var> for provided <var>optionClass</var>
     *
     * @param optionSource
     * @param optionMap
     * @param optionClass
     * @throws ConfigurationException
     */
    private void addOptionsForObject(Object optionSource, Map<String, OptionField> optionMap)
            throws ConfigurationException {
        Collection<Field> optionFields = getOptionFieldsForClass(optionSource.getClass());
        for (Field field : optionFields) {
            final Option option = field.getAnnotation(Option.class);
            addNameToMap(optionMap, optionSource, option.name(), field);
            if (option.shortName() != Option.NO_SHORT_NAME) {
                addNameToMap(optionMap, optionSource, String.valueOf(option.shortName()), field);
            }
            if (isBooleanField(field)) {
                // add the corresponding "no" option to make boolean false
                addNameToMap(optionMap, optionSource, BOOL_FALSE_PREFIX + option.name(), field);
            }
        }
    }

    /**
     * Gets a list of all {@link Option} fields (both declared and inherited) for given class.
     *
     * @param optionClass the {@link Class} to search
     * @return a {@link Collection} of fields annotated with {@link Option}
     */
    protected static Collection<Field> getOptionFieldsForClass(final Class<?> optionClass) {
        Collection<Field> fieldList = new ArrayList<Field>();
        buildOptionFieldsForClass(optionClass, fieldList);
        return fieldList;
    }

    /**
     * Recursive method that adds all option fields (both declared and inherited) to the
     * <var>optionFields</var> for provided <var>optionClass</var>
     *
     * @param optionClass
     * @param optionFields
     */
    private static void buildOptionFieldsForClass(final Class<?> optionClass,
            Collection<Field> optionFields) {
        for (Field field : optionClass.getDeclaredFields()) {
            if (field.isAnnotationPresent(Option.class)) {
                optionFields.add(field);
            }
        }
        Class<?> superClass = optionClass.getSuperclass();
        if (superClass != null) {
            buildOptionFieldsForClass(superClass, optionFields);
        }
    }

    public boolean isBooleanOption(String name) throws ConfigurationException {
        Field field = fieldForArg(name).mField;
        return isBooleanField(field);
    }

    private boolean isBooleanField(Field field) throws ConfigurationException {
        return getHandler(field.getGenericType()).isBoolean();
    }

    private void addNameToMap(Map<String, OptionField> optionMap, Object optionSource, String name,
            Field field) throws ConfigurationException {
        OptionField optionField = new OptionField(optionSource, field);
        if (optionMap.put(name, optionField) != null) {
            throw new ConfigurationException("found multiple @Options sharing the name '" + name
                    + "'");
        }
        if (getHandler(field.getGenericType()) == null) {
            throw new ConfigurationException("unsupported @Option field type '" + field.getType()
                    + "'");
        }
    }

    private abstract static class Handler {
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

    private static class BooleanHandler extends Handler {
        @Override boolean isBoolean() {
            return true;
        }

        @Override
        Object translate(String valueText) {
            if (valueText.equalsIgnoreCase("true") || valueText.equalsIgnoreCase("yes")) {
                return Boolean.TRUE;
            } else if (valueText.equalsIgnoreCase("false") || valueText.equalsIgnoreCase("no")) {
                return Boolean.FALSE;
            }
            return null;
        }
    }

    private static class ByteHandler extends Handler {
        @Override
        Object translate(String valueText) {
            try {
                return Byte.parseByte(valueText);
            } catch (NumberFormatException ex) {
                return null;
            }
        }
    }

    private static class ShortHandler extends Handler {
        @Override
        Object translate(String valueText) {
            try {
                return Short.parseShort(valueText);
            } catch (NumberFormatException ex) {
                return null;
            }
        }
    }

    private static class IntegerHandler extends Handler {
        @Override
        Object translate(String valueText) {
            try {
                return Integer.parseInt(valueText);
            } catch (NumberFormatException ex) {
                return null;
            }
        }
    }

    private static class LongHandler extends Handler {
        @Override
        Object translate(String valueText) {
            try {
                return Long.parseLong(valueText);
            } catch (NumberFormatException ex) {
                return null;
            }
        }
    }

    private static class FloatHandler extends Handler {
        @Override
        Object translate(String valueText) {
            try {
                return Float.parseFloat(valueText);
            } catch (NumberFormatException ex) {
                return null;
            }
        }
    }

    private static class DoubleHandler extends Handler {
        @Override
        Object translate(String valueText) {
            try {
                return Double.parseDouble(valueText);
            } catch (NumberFormatException ex) {
                return null;
            }
        }
    }

    private static class StringHandler extends Handler {
        @Override
        Object translate(String valueText) {
            return valueText;
        }
    }

    private static class FileHandler extends Handler {
        @Override
        Object translate(String valueText) {
            return new File(valueText);
        }
    }
}
