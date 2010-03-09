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

import java.util.List;

import junit.framework.TestCase;

/**
 * Unit tests for {@link ArgsOptionParser}.
 */
public class ArgsOptionParserTest extends TestCase {

    /**
     * An option source with one {@link Option} specified.
     */
    private static class OneOptionSource {

        private static final String DEFAULT_VALUE = "default";

        @Option(name="my_option", shortName='o')
        private String mMyOption = DEFAULT_VALUE;
    }

    /**
     * An option source with boolean {@link Option} specified.
     */
    private static class BooleanOptionSource {

        private static final boolean DEFAULT_BOOL = false;
        private static final String DEFAULT_VALUE = "default";

        @Option(name="my_boolean", shortName='b')
        private boolean mMyBool = DEFAULT_BOOL;

        @Option(name="my_option", shortName='o')
        protected String mMyOption = DEFAULT_VALUE;
    }

    /**
     * An option source with boolean {@link Option} specified with default = true.
     */
    private static class BooleanTrueOptionSource {

        private static final boolean DEFAULT_BOOL = true;

        @Option(name="my_boolean", shortName='b')
        private boolean mMyBool = DEFAULT_BOOL;
    }

    /**
    * Test passing an empty argument list for an object that has one option specified.
    * <p/>
    * Expected that the option field should retain its default value.
    */
   public void testParse_noArg() throws ConfigurationException {
       OneOptionSource object = new OneOptionSource();
       ArgsOptionParser parser = new ArgsOptionParser(object);
       parser.parse(new String[] {});
       assertEquals(OneOptionSource.DEFAULT_VALUE, object.mMyOption);
   }

   /**
    * Test passing an single argument for an object that has one option specified.
    */
   public void testParse_oneArg() throws ConfigurationException {
       OneOptionSource object = new OneOptionSource();
       ArgsOptionParser parser = new ArgsOptionParser(object);
       final String expectedValue = "set";
       parser.parse(new String[] {"--my_option", expectedValue});
       assertEquals(expectedValue, object.mMyOption);
   }

   /**
    * Test passing an single argument for an object that has one option specified, using the
    * option=value notation.
    */
   public void testParse_oneArgEquals() throws ConfigurationException {
       OneOptionSource object = new OneOptionSource();
       ArgsOptionParser parser = new ArgsOptionParser(object);
       final String expectedValue = "set";
       parser.parse(new String[] {String.format("--my_option=%s", expectedValue)});
       assertEquals(expectedValue, object.mMyOption);
   }

   /**
    * Test passing a single argument for an object that has one option specified, using the
    * short option notation.
    */
   public void testParse_oneShortArg() throws ConfigurationException {
       OneOptionSource object = new OneOptionSource();
       ArgsOptionParser parser = new ArgsOptionParser(object);
       final String expectedValue = "set";
       parser.parse(new String[] {"-o", expectedValue});
       assertEquals(expectedValue, object.mMyOption);
   }

   /**
    * Test passing a short args with an unused argument
    */
   public void testParse_shortArgUnused() throws ConfigurationException {
       OneOptionSource object = new OneOptionSource();
       ArgsOptionParser parser = new ArgsOptionParser(object);
       final String expectedValue = "set";
       final String unusedPosArg = "unused";
       final String unusedPosArg2 = "unused2";
       List<String> leftOver = parser.parse(new String[] {"-o", expectedValue, "-u", unusedPosArg,
               unusedPosArg2});
       assertEquals(expectedValue, object.mMyOption);
       assertTrue(leftOver.contains(unusedPosArg));
       assertTrue(leftOver.contains(unusedPosArg2));
   }

   /**
    * Test that "--" marks the beginning of positional arguments
    */
   public void testParse_posArgs() throws ConfigurationException {
       OneOptionSource object = new OneOptionSource();
       ArgsOptionParser parser = new ArgsOptionParser(object);
       final String expectedValue = "set";
       // have a position argument with a long option prefix, to try to confuse the parser
       final String posArg = "--unused";
       List<String> leftOver = parser.parse(new String[] {"-o", expectedValue, "--", posArg});
       assertEquals(expectedValue, object.mMyOption);
       assertTrue(leftOver.contains(posArg));
   }

   /**
    * Test passing a single boolean argument.
    */
   public void testParse_boolArg() throws ConfigurationException {
       BooleanOptionSource object = new BooleanOptionSource();
       ArgsOptionParser parser = new ArgsOptionParser(object);
       parser.parse(new String[] {"-b"});
       assertTrue(object.mMyBool);
   }

   /**
    * Test passing a boolean argument with another short argument.
    */
   public void testParse_boolTwoArg() throws ConfigurationException {
       BooleanOptionSource object = new BooleanOptionSource();
       ArgsOptionParser parser = new ArgsOptionParser(object);
       final String expectedValue = "set";
       parser.parse(new String[] {"-bo", expectedValue});
       assertTrue(object.mMyBool);
       assertEquals(expectedValue, object.mMyOption);
   }

   /**
    * Test passing a boolean argument with another short argument, with value concatenated.
    * e.g -bovalue
    */
   public void testParse_boolTwoArgValue() throws ConfigurationException {
       BooleanOptionSource object = new BooleanOptionSource();
       ArgsOptionParser parser = new ArgsOptionParser(object);
       final String expectedValue = "set";
       parser.parse(new String[] {String.format("-bo%s", expectedValue)});
       assertTrue(object.mMyBool);
       assertEquals(expectedValue, object.mMyOption);
   }

   /**
    * Test the "--no-<bool option>" syntax
    */
   public void testParse_boolFalse() throws ConfigurationException {
       BooleanTrueOptionSource object = new BooleanTrueOptionSource();
       ArgsOptionParser parser = new ArgsOptionParser(object);
       parser.parse(new String[] {"--no-my_boolean"});
       assertFalse(object.mMyBool);
   }

   /**
    * Test the boolean long option syntax
    */
   public void testParse_boolLong() throws ConfigurationException {
       BooleanOptionSource object = new BooleanOptionSource();
       ArgsOptionParser parser = new ArgsOptionParser(object);
       parser.parse(new String[] {"--my_boolean"});
       assertTrue(object.mMyBool);
   }

   /**
    * Test passing arg string where value is missing
    */
   public void testParse_missingValue() throws ConfigurationException {
       OneOptionSource object = new OneOptionSource();
       ArgsOptionParser parser = new ArgsOptionParser(object);
       try {
           parser.parse(new String[] {"--my_option"});
           fail("ConfigurationException not thrown");
       } catch (ConfigurationException e) {
           // expected
       }
   }

   /**
    * Test parsing args for an option that does not exist.
    */
   public void testParse_optionNotPresent() throws ConfigurationException {
       OneOptionSource object = new OneOptionSource();
       ArgsOptionParser parser = new ArgsOptionParser(object);
       final String expectedValue = "set";
       // extra option should be ignored
       parser.parse(new String[] {"--my_option", "set", "--not_here", "value"});
       assertEquals(expectedValue, object.mMyOption);
   }
}
