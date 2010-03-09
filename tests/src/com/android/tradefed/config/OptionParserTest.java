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
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;

import junit.framework.TestCase;

/**
 * Unit tests for {@link OptionParser}.
 */
public class OptionParserTest extends TestCase {

    /** Option source with generic type. */
    private static class GenericTypeOptionSource {
        @SuppressWarnings("unused")
        @Option(name="my_option", shortName='o')
        private Collection<?> mMyOption;
    }

    /** Option source with unparameterized type. */
    private static class CollectionTypeOptionSource {
        @SuppressWarnings({
                "unused", "unchecked"
        })
        @Option(name="my_option", shortName='o')
        private Collection mMyOption;
    }

    private static class MyGeneric<T> {
    }

    /** Option source with unparameterized type. */
    private static class NonCollectionGenericTypeOptionSource {
        @SuppressWarnings("unused")
        @Option(name="my_option", shortName='o')
        private MyGeneric<String> mMyOption;
    }

    /** Option source with duplicate names. */
    private static class DuplicateOptionSource {
        @SuppressWarnings("unused")
        @Option(name="my_option", shortName='o')
        private String mMyOption;

        @SuppressWarnings("unused")
        @Option(name="my_option", shortName='o')
        private String mMyOption2;
    }

    /** option source with all supported types. */
    private static class AllTypesOptionSource {
        @Option(name="string_collection")
        private Collection<String> mStringCollection = new ArrayList<String>();

        @Option(name="string")
        private String mString = null;

        @Option(name="boolean")
        private boolean mBool = false;

        @Option(name="booleanObj")
        private Boolean mBooleanObj = false;

        @Option(name="byte")
        private byte mByte = 0;

        @Option(name="byteObj")
        private Byte mByteObj = 0;

        @Option(name="short")
        private short mShort = 0;

        @Option(name="shortObj")
        private Short mShortObj = null;

        @Option(name="int")
        private int mInt = 0;

        @Option(name="intObj")
        private Integer mIntObj = 0;

        @Option(name="long")
        private long mLong = 0;

        @Option(name="longObj")
        private Long mLongObj = null;

        @Option(name="float")
        private float mFloat = 0;

        @Option(name="floatObj")
        private Float mFloatObj = null;

        @Option(name="double")
        private double mDouble = 0;

        @Option(name="doubleObj")
        private Double mDoubleObj = null;

        @Option(name="file")
        private File mFile = null;
    }

    /**
     * Test creating an {@link OptionParser} for a source with invalid option type.
     */
    public void testOptionParser_noType() {
        try {
            new OptionParser(new GenericTypeOptionSource());
            fail("ConfigurationException not thrown");
        } catch (ConfigurationException e) {
            // expected
        }
    }

    /**
     * Test creating an {@link OptionParser} for a source with duplicate names.
     */
    public void testOptionParser_duplicateOptions() {
        try {
            new OptionParser(new DuplicateOptionSource());
            fail("ConfigurationException not thrown");
        } catch (ConfigurationException e) {
            // expected
        }
    }

    /**
     * Simple positive test method for {@link OptionParser#fieldForArg(java.lang.String)}.
     * @throws ConfigurationException
     */
    public void testFieldForArg() throws ConfigurationException {
        OptionParser parser = new OptionParser(new AllTypesOptionSource());
        assertNotNull(parser.fieldForArg("string_collection"));
    }

    /**
     * Test method that {@link OptionParser#fieldForArg(java.lang.String)} return null for unknown
     * names.
     * @throws ConfigurationException
     */
    public void testFieldForArg_unknown() throws ConfigurationException {
        OptionParser parser = new OptionParser(new AllTypesOptionSource());
        assertNull(parser.fieldForArg("unknown"));
    }

    /**
     * Test passing an unknown generic type into {@link OptionParser#getHandler(Type)}.
     */
    public void testGetHandler_unknownType() throws SecurityException, NoSuchFieldException {
        try {
            OptionParser parser = new OptionParser(new AllTypesOptionSource());
            Field field = CollectionTypeOptionSource.class.getDeclaredFields()[0];
            parser.getHandler(field.getGenericType());
            fail("ConfigurationException not thrown");
        } catch (ConfigurationException e) {
            // expected
        }
    }

    /**
     * Test passing a non-parameterized Collection into {@link OptionParser#getHandler(Type)}.
     */
    public void testGetHandler_unparameterizedType() throws SecurityException,
            NoSuchFieldException {
        try {
            OptionParser parser = new OptionParser(new AllTypesOptionSource());
            Field field = NonCollectionGenericTypeOptionSource.class.getDeclaredFields()[0];
            parser.getHandler(field.getGenericType());
            fail("ConfigurationException not thrown");
        } catch (ConfigurationException e) {
            // expected
        }
    }

    /**
     * Test {@link OptionParser#setValue(Field, String, OptionParser.Handler, String)} for
     * a String.
     * @throws ConfigurationException
     */
    public void testSetValue_string() throws ConfigurationException {
        AllTypesOptionSource optionSource = new AllTypesOptionSource();
        final String expectedValue = "stringvalue";
        assertSetValue(optionSource, "string", expectedValue);
        assertEquals(expectedValue, optionSource.mString);
    }

    /**
     * Test {@link OptionParser#setValue(Field, String, OptionParser.Handler, String)} for
     * a Collection.
     * @throws ConfigurationException
     */
    public void testSetValue_collection() throws ConfigurationException, IOException {
        AllTypesOptionSource optionSource = new AllTypesOptionSource();
        final String expectedValue = "stringvalue";
        assertSetValue(optionSource, "string_collection", expectedValue);
        assertEquals(1, optionSource.mStringCollection.size());
        assertTrue(optionSource.mStringCollection.contains(expectedValue));
    }

    /**
     * Test {@link OptionParser#setValue(Field, String, OptionParser.Handler, String)} for
     * a boolean.
     * @throws ConfigurationException
     */
    public void testSetValue_boolean() throws ConfigurationException {
        AllTypesOptionSource optionSource = new AllTypesOptionSource();
        assertSetValue(optionSource, "boolean", "true");
        assertEquals(true, optionSource.mBool);
    }

    /**
     * Test {@link OptionParser#setValue(Field, String, OptionParser.Handler, String)} for
     * a boolean for a non-boolean value.
     * @throws ConfigurationException
     */
    public void testSetValue_booleanInvalid() throws ConfigurationException {
        AllTypesOptionSource optionSource = new AllTypesOptionSource();
        assertSetValueInvalid(optionSource, "boolean", "blah");
    }

    /**
     * Test {@link OptionParser#setValue(Field, String, OptionParser.Handler, String)} for
     * a Boolean.
     * @throws ConfigurationException
     */
    public void testSetValue_booleanObj() throws ConfigurationException {
        AllTypesOptionSource optionSource = new AllTypesOptionSource();
        assertSetValue(optionSource, "booleanObj", "true");
        assertTrue(optionSource.mBooleanObj);
    }

    /**
     * Test {@link OptionParser#setValue(Field, String, OptionParser.Handler, String)} for
     * a byte.
     * @throws ConfigurationException
     */
    public void testSetValue_byte() throws ConfigurationException {
        AllTypesOptionSource optionSource = new AllTypesOptionSource();
        assertSetValue(optionSource, "byte", "2");
        assertEquals(2, optionSource.mByte);
    }

    /**
     * Test {@link OptionParser#setValue(Field, String, OptionParser.Handler, String)} for
     * a byte for an invalid value.
     * @throws ConfigurationException
     */
    public void testSetValue_byteInvalid() throws ConfigurationException {
        AllTypesOptionSource optionSource = new AllTypesOptionSource();
        assertSetValueInvalid(optionSource, "byte", "blah");
    }

    /**
     * Test {@link OptionParser#setValue(Field, String, OptionParser.Handler, String)} for
     * a Byte.
     * @throws ConfigurationException
     */
    public void testSetValue_byteObj() throws ConfigurationException {
        AllTypesOptionSource optionSource = new AllTypesOptionSource();
        assertSetValue(optionSource, "byteObj", "2");
        assertTrue(2 == optionSource.mByteObj);
    }

    /**
     * Test {@link OptionParser#setValue(Field, String, OptionParser.Handler, String)} for
     * a short.
     * @throws ConfigurationException
     */
    public void testSetValue_short() throws ConfigurationException {
        AllTypesOptionSource optionSource = new AllTypesOptionSource();
        assertSetValue(optionSource, "short", "2");
        assertTrue(2 == optionSource.mShort);
    }

    /**
     * Test {@link OptionParser#setValue(Field, String, OptionParser.Handler, String)} for
     * a Short.
     * @throws ConfigurationException
     */
    public void testSetValue_shortObj() throws ConfigurationException {
        AllTypesOptionSource optionSource = new AllTypesOptionSource();
        assertSetValue(optionSource, "shortObj", "2");
        assertTrue(2 == optionSource.mShortObj);
    }

    /**
     * Test {@link OptionParser#setValue(Field, String, OptionParser.Handler, String)} for
     * a short for an invalid value.
     * @throws ConfigurationException
     */
    public void testSetValue_shortInvalid() throws ConfigurationException {
        AllTypesOptionSource optionSource = new AllTypesOptionSource();
        assertSetValueInvalid(optionSource, "short", "blah");
    }

    /**
     * Test {@link OptionParser#setValue(Field, String, OptionParser.Handler, String)} for
     * a int.
     * @throws ConfigurationException
     */
    public void testSetValue_int() throws ConfigurationException {
        AllTypesOptionSource optionSource = new AllTypesOptionSource();
        assertSetValue(optionSource, "int", "2");
        assertTrue(2 == optionSource.mInt);
    }

    /**
     * Test {@link OptionParser#setValue(Field, String, OptionParser.Handler, String)} for
     * a Integer.
     * @throws ConfigurationException
     */
    public void testSetValue_intObj() throws ConfigurationException {
        AllTypesOptionSource optionSource = new AllTypesOptionSource();
        assertSetValue(optionSource, "intObj", "2");
        assertTrue(2 == optionSource.mIntObj);
    }

    /**
     * Test {@link OptionParser#setValue(Field, String, OptionParser.Handler, String)} for
     * a int for an invalid value.
     * @throws ConfigurationException
     */
    public void testSetValue_intInvalid() throws ConfigurationException {
        AllTypesOptionSource optionSource = new AllTypesOptionSource();
        assertSetValueInvalid(optionSource, "int", "blah");
    }

    /**
     * Test {@link OptionParser#setValue(Field, String, OptionParser.Handler, String)} for
     * a long.
     * @throws ConfigurationException
     */
    public void testSetValue_long() throws ConfigurationException {
        AllTypesOptionSource optionSource = new AllTypesOptionSource();
        assertSetValue(optionSource, "long", "2");
        assertTrue(2 == optionSource.mLong);
    }

    /**
     * Test {@link OptionParser#setValue(Field, String, OptionParser.Handler, String)} for
     * a Long.
     * @throws ConfigurationException
     */
    public void testSetValue_longObj() throws ConfigurationException {
        AllTypesOptionSource optionSource = new AllTypesOptionSource();
        assertSetValue(optionSource, "longObj", "2");
        assertTrue(2 == optionSource.mLongObj);
    }

    /**
     * Test {@link OptionParser#setValue(Field, String, OptionParser.Handler, String)} for
     * a long for an invalid value.
     * @throws ConfigurationException
     */
    public void testSetValue_longInvalid() throws ConfigurationException {
        AllTypesOptionSource optionSource = new AllTypesOptionSource();
        assertSetValueInvalid(optionSource, "long", "blah");
    }

    /**
     * Test {@link OptionParser#setValue(Field, String, OptionParser.Handler, String)} for
     * a float.
     * @throws ConfigurationException
     */
    public void testSetValue_float() throws ConfigurationException {
        AllTypesOptionSource optionSource = new AllTypesOptionSource();
        assertSetValue(optionSource, "float", "2.1");
        assertEquals(2.1, optionSource.mFloat, 0.01);
    }

    /**
     * Test {@link OptionParser#setValue(Field, String, OptionParser.Handler, String)} for
     * a Float.
     * @throws ConfigurationException
     */
    public void testSetValue_floatObj() throws ConfigurationException {
        AllTypesOptionSource optionSource = new AllTypesOptionSource();
        assertSetValue(optionSource, "floatObj", "2.1");
        assertEquals(2.1, optionSource.mFloatObj, 0.01);
    }

    /**
     * Test {@link OptionParser#setValue(Field, String, OptionParser.Handler, String)} for
     * a float for an invalid value.
     * @throws ConfigurationException
     */
    public void testSetValue_floatInvalid() throws ConfigurationException {
        AllTypesOptionSource optionSource = new AllTypesOptionSource();
        assertSetValueInvalid(optionSource, "float", "blah");
    }

    /**
     * Test {@link OptionParser#setValue(Field, String, OptionParser.Handler, String)} for
     * a float.
     * @throws ConfigurationException
     */
    public void testSetValue_double() throws ConfigurationException {
        AllTypesOptionSource optionSource = new AllTypesOptionSource();
        assertSetValue(optionSource, "double", "2.1");
        assertEquals(2.1, optionSource.mDouble, 0.01);
    }

    /**
     * Test {@link OptionParser#setValue(Field, String, OptionParser.Handler, String)} for
     * a Float.
     * @throws ConfigurationException
     */
    public void testSetValue_doubleObj() throws ConfigurationException {
        AllTypesOptionSource optionSource = new AllTypesOptionSource();
        assertSetValue(optionSource, "doubleObj", "2.1");
        assertEquals(2.1, optionSource.mDoubleObj, 0.01);
    }

    /**
     * Test {@link OptionParser#setValue(Field, String, OptionParser.Handler, String)} for
     * a double for an invalid value.
     * @throws ConfigurationException
     */
    public void testSetValue_doubleInvalid() throws ConfigurationException {
        AllTypesOptionSource optionSource = new AllTypesOptionSource();
        assertSetValueInvalid(optionSource, "double", "blah");
    }

    /**
     * Test {@link OptionParser#setValue(Field, String, OptionParser.Handler, String)} for
     * a File.
     * @throws ConfigurationException
     * @throws IOException
     */
    public void testSetValue_file() throws ConfigurationException, IOException {
        AllTypesOptionSource optionSource = new AllTypesOptionSource();
        File tmpFile = File.createTempFile("testSetValue_file", "txt");
        assertSetValue(optionSource, "file", tmpFile.getAbsolutePath());
        assertEquals(tmpFile.getAbsolutePath(), optionSource.mFile.getAbsolutePath());
    }

    /**
     * Perform {@link OptionParser#setValue(Field, String, OptionParser.Handler, String)} for a
     * given option.
     */
    private void assertSetValue(AllTypesOptionSource optionSource, final String optionName,
            final String expectedValue) throws ConfigurationException {
        OptionParser parser = new OptionParser(optionSource);
        Field field = parser.fieldForArg(optionName);
        assertNotNull(field);
        OptionParser.Handler handler = parser.getHandler(field.getGenericType());
        parser.setValue(field, optionName, handler, expectedValue);
    }

    /**
     * Perform {@link OptionParser#setValue(Field, String, OptionParser.Handler, String)} for a
     * given option, with an invalid value for the option type.
     */
    private void assertSetValueInvalid(AllTypesOptionSource optionSource, final String optionName,
            final String expectedValue)  {
        try {
            assertSetValue(optionSource, optionName, expectedValue);
            fail("ConfigurationException not thrown");
        } catch (ConfigurationException e) {
            // expected
        }
    }
}
