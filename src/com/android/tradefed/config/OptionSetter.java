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

import com.android.ddmlib.Log;
import com.android.tradefed.util.ArrayUtil;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
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
public class OptionSetter {
    private static final String LOG_TAG = "OptionSetter";

    static final String BOOL_FALSE_PREFIX = "no-";
    private static final HashMap<Class<?>, Handler> handlers = new HashMap<Class<?>, Handler>();
    static final char NAMESPACE_SEPARATOR = ':';

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
    private static Handler getHandler(Type type) throws ConfigurationException {
        if (type instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) type;
            Class rawClass = (Class<?>) parameterizedType.getRawType();
            if (Collection.class.isAssignableFrom(rawClass)) {
                // handle Collection
                Type actualType = parameterizedType.getActualTypeArguments()[0];
                if (!(actualType instanceof Class)) {
                    throw new ConfigurationException(
                            "cannot handle nested parameterized type " + type);
                }
                return getHandler(actualType);
            } else if (Map.class.isAssignableFrom(rawClass)) {
                // handle Map
                Type keyType = parameterizedType.getActualTypeArguments()[0];
                Type valueType = parameterizedType.getActualTypeArguments()[1];
                if (!(keyType instanceof Class)) {
                    throw new ConfigurationException(
                            "cannot handle nested parameterized type " + keyType);
                } else if (!(valueType instanceof Class)) {
                    throw new ConfigurationException(
                            "cannot handle nested parameterized type " + valueType);
                }

                return new MapHandler(getHandler(keyType), getHandler(valueType));
            } else {
                throw new ConfigurationException(String.format(
                        "can't handle parameterized type %s; only Collection and Map are supported",
                        type));
            }
        }
        if (type instanceof Class) {
            Class<?> cType = (Class<?>) type;
            if (Collection.class.isAssignableFrom(cType)) {
                // could handle by just having a default of treating
                // contents as String but consciously decided this
                // should be an error
                throw new ConfigurationException(
                        String.format("Cannot handle non-parameterized collection %s. %s", type,
                                "use a generic Collection to specify a desired element type"));
            } else if (cType.isEnum()) {
                return new EnumHandler(cType);
            }
            return handlers.get(cType);
        }
        throw new ConfigurationException(String.format("cannot handle unknown field type %s",
                type));
    }

    private final Collection<Object> mOptionSources;
    private final Map<String, OptionFieldsForName> mOptionMap;

    /**
     * Container for the list of option fields with given name.
     * <p/>
     * Used to enforce constraint that fields with same name can exist in different option sources,
     * but not the same option source
     */
    private class OptionFieldsForName implements Iterable<Map.Entry<Object, Field>> {

        private Map<Object, Field> mSourceFieldMap = new HashMap<Object, Field>();

        void addField(String name, Object source, Field field) throws ConfigurationException {
            if (size() > 0) {
                Handler existingFieldHandler = getHandler(getFirstField().getType());
                Handler newFieldHandler = getHandler(field.getType());
                if (!existingFieldHandler.equals(newFieldHandler)) {
                    throw new ConfigurationException(String.format(
                            "@Option field with name '%s' in class '%s' is defined with a " +
                            "different type than same option in class '%s'",
                            name, source.getClass().getName(),
                            getFirstObject().getClass().getName()));
                }
            }
            if (mSourceFieldMap.put(source, field) != null) {
                throw new ConfigurationException(String.format(
                        "@Option field with name '%s' is defined more than once in class '%s'",
                        name, source.getClass().getName()));
            }
        }

        public int size() {
            return mSourceFieldMap.size();
        }

        public Field getFirstField() throws ConfigurationException {
            if (size() <= 0) {
                // should never happen
                throw new ConfigurationException("no option fields found");
            }
            return mSourceFieldMap.values().iterator().next();
        }

        public Object getFirstObject() throws ConfigurationException {
            if (size() <= 0) {
                // should never happen
                throw new ConfigurationException("no option fields found");
            }
            return mSourceFieldMap.keySet().iterator().next();
        }

        public Iterator<Map.Entry<Object, Field>> iterator() {
            return mSourceFieldMap.entrySet().iterator();
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

    private OptionFieldsForName fieldsForArg(String name) throws ConfigurationException {
        OptionFieldsForName fields = mOptionMap.get(name);
        if (fields == null || fields.size() == 0) {
            throw new ConfigurationException(String.format("Could not find option with name %s",
                    name));
        }
        return fields;
    }

    /**
     * Returns a string describing the type of the field with given name.
     *
     * @param name the {@link Option} field name
     * @return a {@link String} describing the field's type
     * @throws ConfigurationException if field could not be found
     */
    public String getTypeForOption(String name) throws ConfigurationException {
        return fieldsForArg(name).getFirstField().getType().getSimpleName().toLowerCase();
    }

    /**
     * Sets the value for an option.
     * @param optionName the name of Option to set
     * @param valueText the value
     * @throws ConfigurationException if Option cannot be found or valueText is wrong type
     */
    @SuppressWarnings("unchecked")
    public void setOptionValue(String optionName, String valueText) throws ConfigurationException {
        OptionFieldsForName optionFields = fieldsForArg(optionName);
        for (Map.Entry<Object, Field> fieldEntry : optionFields) {

            Object optionSource = fieldEntry.getKey();
            Field field = fieldEntry.getValue();
            Handler handler = getHandler(field.getGenericType());
            Object value = handler.translate(valueText);
            if (value == null) {
                final String type = field.getType().getSimpleName();
                throw new ConfigurationException(
                        String.format("Couldn't convert '%s' to a %s for option '%s'", valueText,
                                type, optionName));
            }
            try {
                field.setAccessible(true);
                if (Collection.class.isAssignableFrom(field.getType())) {
                    Collection collection = (Collection)field.get(optionSource);
                    if (collection == null) {
                        throw new ConfigurationException(String.format(
                                "internal error: no storage allocated for field '%s' (used for " +
                                "option '%s') in class '%s'",
                                field.getName(), optionName, optionSource.getClass().getName()));
                    }
                    collection.add(value);
                } else {
                    field.set(optionSource, value);
                }
            } catch (IllegalAccessException e) {
                throw new ConfigurationException(String.format(
                        "internal error when setting option '%s'", optionName), e);
            }
        }
    }

    /**
     * Sets the key and value for a Map option.
     * @param optionName the name of Option to set
     * @param keyText the key, if applicable.  Will be ignored for non-Map fields
     * @param valueText the value
     * @throws ConfigurationException if Option cannot be found or valueText is wrong type
     */
    @SuppressWarnings("unchecked")
    public void setOptionMapValue(String optionName, String keyText, String valueText)
            throws ConfigurationException {
        // FIXME: try to unify code paths with setOptionValue
        OptionFieldsForName optionFields = fieldsForArg(optionName);
        for (Map.Entry<Object, Field> fieldEntry : optionFields) {

            Object optionSource = fieldEntry.getKey();
            Field field = fieldEntry.getValue();
            Handler handler = getHandler(field.getGenericType());
            if (handler == null || !(handler instanceof MapHandler)) {
                throw new ConfigurationException("Not a map!");
            }

            MapEntry pair = null;
            try {
                pair = ((MapHandler) handler).translate(keyText, valueText);
                if (pair == null) {
                    throw new IllegalArgumentException();
                }
            } catch (IllegalArgumentException e) {
                ParameterizedType pType = (ParameterizedType) field.getGenericType();
                Type keyType = pType.getActualTypeArguments()[0];
                Type valueType = pType.getActualTypeArguments()[1];

                String keyTypeName = ((Class)keyType).getSimpleName().toLowerCase();
                String valueTypeName = ((Class)valueType).getSimpleName().toLowerCase();

                String message = "";
                if (e.getMessage().contains("key")) {
                    message = String.format(
                            "Couldn't convert '%s' to a %s for the key of mapoption '%s'",
                            keyText, keyTypeName, optionName);
                } else if (e.getMessage().contains("value")) {
                    message = String.format(
                            "Couldn't convert '%s' to a %s for the value of mapoption '%s'",
                            valueText, valueTypeName, optionName);
                } else {
                    message = String.format("Failed to convert key '%s' to type %s and/or " +
                            "value '%s' to type %s for mapoption '%s'",
                            keyText, keyTypeName, valueText, valueTypeName, optionName);
                }
                throw new ConfigurationException(message);
            }
            try {
                field.setAccessible(true);
                if (!Map.class.isAssignableFrom(field.getType())) {
                    throw new ConfigurationException(String.format(
                            "internal error: not a map field!"));
                }
                Map map = (Map)field.get(optionSource);
                if (map == null) {
                    throw new ConfigurationException(String.format(
                            "internal error: no storage allocated for field '%s' (used for " +
                            "option '%s') in class '%s'",
                            field.getName(), optionName, optionSource.getClass().getName()));
                }
                map.put(pair.mKey, pair.mValue);
            } catch (IllegalAccessException e) {
                throw new ConfigurationException(String.format(
                        "internal error when setting option '%s'", optionName), e);
            }
        }
    }

    /**
     * Cache the available options and report any problems with the options themselves right away.
     *
     * @return a {@link Map} of {@link Option} field name to {@link OptionField}s
     * @throws ConfigurationException if any {@link Option} are incorrectly specified
     */
    private Map<String, OptionFieldsForName> makeOptionMap() throws ConfigurationException {
        final Map<String, OptionFieldsForName> optionMap =
                new HashMap<String, OptionFieldsForName>();
        for (Object objectSource: mOptionSources) {
            addOptionsForObject(objectSource, optionMap);
        }
        return optionMap;
    }

    /**
     * Adds all option fields (both declared and inherited) to the <var>optionMap</var> for
     * provided <var>optionClass</var>.
     *
     * @param optionSource
     * @param optionMap
     * @param optionClass
     * @throws ConfigurationException
     */
    private void addOptionsForObject(Object optionSource,
            Map<String, OptionFieldsForName> optionMap) throws ConfigurationException {
        Collection<Field> optionFields = getOptionFieldsForClass(optionSource.getClass());
        for (Field field : optionFields) {
            final Option option = field.getAnnotation(Option.class);
            if (option.name().indexOf(NAMESPACE_SEPARATOR) != -1) {
                throw new ConfigurationException(String.format(
                        "Option name '%s' in class '%s' is invalid. " +
                        "Option names cannot contain the namespace separator character '%c'",
                        option.name(), optionSource.getClass().getName(), NAMESPACE_SEPARATOR));
            }
            addNameToMap(optionMap, optionSource, option.name(), field);
            addNamespacedOptionToMap(optionMap, optionSource, option.name(), field);
            if (option.shortName() != Option.NO_SHORT_NAME) {
                addNameToMap(optionMap, optionSource, String.valueOf(option.shortName()), field);
                addNamespacedOptionToMap(optionMap, optionSource,
                        String.valueOf(option.shortName()), field);
            }
            if (isBooleanField(field)) {
                // add the corresponding "no" option to make boolean false
                addNameToMap(optionMap, optionSource, BOOL_FALSE_PREFIX + option.name(), field);
                addNamespacedOptionToMap(optionMap, optionSource, BOOL_FALSE_PREFIX + option.name(),
                        field);
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

    /**
     * Return the given {@link Field}'s value as a {@link String}.
     *
     * @param field the {@link Field}
     * @param optionObject the {@link Object} to get field's value from.
     * @return the field's value as a {@link String}, or <code>null</code> if field is not set or is
     *         empty (in case of {@link Collection}s
     */
    static String getFieldValueAsString(Field field, Object optionObject) {
        try {
            field.setAccessible(true);
            Object fieldValue = field.get(optionObject);
            if (fieldValue == null) {
                return null;
            }
            if (fieldValue instanceof Collection) {
                Collection collection = (Collection)fieldValue;
                if (collection.isEmpty()) {
                    return null;
                }
            } else if (fieldValue instanceof Map) {
                Map map = (Map)fieldValue;
                if (map.isEmpty()) {
                    return null;
                }
            }
            return fieldValue.toString();
        } catch (IllegalArgumentException e) {
            Log.w(LOG_TAG, String.format(
                    "Could not read value for field %s in class %s. Reason: %s", field.getName(),
                    optionObject.getClass().getName(), e));
            return null;
        } catch (IllegalAccessException e) {
            Log.w(LOG_TAG, String.format(
                    "Could not read value for field %s in class %s. Reason: %s", field.getName(),
                    optionObject.getClass().getName(), e));
            return null;
        }
    }

    /**
     * Returns the help text describing the valid values for the Enum field.
     *
     * @param field the {@link Field} to get values for
     * @return the appropriate help text, or an empty {@link String} if the field is not an Enum.
     */
    static String getEnumFieldValuesAsString(Field field) {
        Class<?> type = field.getType();
        Object[] vals = type.getEnumConstants();
        if (vals == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder(" Valid values: [");
        sb.append(ArrayUtil.join(", ", vals));
        sb.append("]");
        return sb.toString();
    }

    public boolean isBooleanOption(String name) throws ConfigurationException {
        Field field = fieldsForArg(name).getFirstField();
        return isBooleanField(field);
    }

    static boolean isBooleanField(Field field) throws ConfigurationException {
        return getHandler(field.getGenericType()).isBoolean();
    }

    public boolean isMapOption(String name) throws ConfigurationException {
        Field field = fieldsForArg(name).getFirstField();
        return isMapField(field);
    }

    static boolean isMapField(Field field) throws ConfigurationException {
        return getHandler(field.getGenericType()).isMap();
    }

    private void addNameToMap(Map<String, OptionFieldsForName> optionMap, Object optionSource,
            String name, Field field) throws ConfigurationException {
        OptionFieldsForName fields = optionMap.get(name);
        if (fields == null) {
            fields = new OptionFieldsForName();
            optionMap.put(name, fields);
        }
        fields.addField(name, optionSource, field);
        if (getHandler(field.getGenericType()) == null) {
            throw new ConfigurationException(String.format(
                    "Option name '%s' in class '%s' is invalid. Unsupported @Option field type '%s'",
                    name, optionSource.getClass().getName(), field.getType()));
        }
    }

    /**
     * Adds the namespaced versions of the option to the map
     */
    private void addNamespacedOptionToMap(Map<String, OptionFieldsForName> optionMap,
            Object optionSource, String name, Field field) throws ConfigurationException {
        if (optionSource.getClass().isAnnotationPresent(OptionClass.class)) {
            final OptionClass classAnnotation = optionSource.getClass().getAnnotation(
                    OptionClass.class);
            addNameToMap(optionMap, optionSource, String.format("%s%c%s", classAnnotation.alias(),
                    NAMESPACE_SEPARATOR, name), field);
        }
        addNameToMap(optionMap, optionSource, String.format("%s%c%s",
                optionSource.getClass().getName(), NAMESPACE_SEPARATOR, name), field);
    }

    private abstract static class Handler {
        // Only BooleanHandler should ever override this.
        boolean isBoolean() {
            return false;
        }

        // Only MapHandler should ever override this.
        boolean isMap() {
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

    private static class MapEntry {
        public Object mKey = null;
        public Object mValue = null;

        /**
         * Convenience constructor
         */
        MapEntry(Object key, Object value) {
            mKey = key;
            mValue = value;
        }
    }

    /**
     * A {@see Handler} to handle values for Map fields.  The {@code Object} returned is a
     * MapEntry
     */
    private static class MapHandler extends Handler {
        private Handler mKeyHandler;
        private Handler mValueHandler;

        MapHandler(Handler keyHandler, Handler valueHandler) {
            if (keyHandler == null || valueHandler == null) {
                throw new NullPointerException();
            }

            mKeyHandler = keyHandler;
            mValueHandler = valueHandler;
        }

        @Override
        boolean isMap() {
            return true;
        }

        @Override
        Object translate(String valueText) {
            return null;
        }

        MapEntry translate(String keyText, String valueText) {
            Object key = mKeyHandler.translate(keyText);
            Object value = mValueHandler.translate(valueText);
            if (key == null) {
                throw new IllegalArgumentException("Failed to parse key");
            } else if (value == null) {
                throw new IllegalArgumentException("Failed to parse value");
            }

            return new MapEntry(key, value);
        }
    }

    /**
     * @ {@link Handler} to handle values for {@link Enum} fields.
     */
    private static class EnumHandler extends Handler {
        private final Class/*<?>*/ mEnumType;

        EnumHandler(Class<?> enumType) {
            mEnumType = enumType;
        }


        @Override
        Object translate(String valueText) {
            return translate(valueText, true);
        }

        Object translate(String valueText, boolean shouldTryUpperCase) {
            try {
                return Enum.valueOf(mEnumType, valueText);
            } catch (IllegalArgumentException e) {
                // Will be thrown if the value can't be mapped back to the enum
                if (shouldTryUpperCase) {
                    // Try to automatically map variable-case strings to uppercase.  This is
                    // reasonable since most Enum constants tend to be uppercase by convention.
                    return translate(valueText.toUpperCase(Locale.ENGLISH), false);
                } else {
                    return null;
                }
            }
        }
    }
}
