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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        private static final String OPTION_NAME = "my_option";
        private static final String OPTION_DESC = "option description";

        @Option(name=OPTION_NAME, shortName='o', description=OPTION_DESC)
        private String mMyOption = DEFAULT_VALUE;
    }

    /**
     * An option source with one {@link Option} specified.
     */
    private static class MapOptionSource {

        private static final String OPTION_NAME = "my_option";
        private static final String OPTION_DESC = "option description";

        @Option(name=OPTION_NAME, shortName='o', description=OPTION_DESC)
        private Map<Integer, Boolean> mMyOption = new HashMap<Integer, Boolean>();
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
     * An option source that has a superclass with options
     */
    private static class InheritedOptionSource extends OneOptionSource {

        private static final String OPTION_NAME = "my_sub_option";
        private static final String OPTION_DESC = "sub description";

        @SuppressWarnings("unused")
        @Option(name=OPTION_NAME, description=OPTION_DESC)
        private String mMySubOption = "";
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
    * Test passing an single argument for an object that has one option specified.
    */
   public void testParse_oneMapArg() throws ConfigurationException {
       MapOptionSource object = new MapOptionSource();
       ArgsOptionParser parser = new ArgsOptionParser(object);
       final int expectedKey = 13;
       final boolean expectedValue = true;
       parser.parse(new String[] {"--my_option", Integer.toString(expectedKey),
               Boolean.toString(expectedValue)});
       assertNotNull(object.mMyOption);
       assertEquals(1, object.mMyOption.size());
       assertEquals(expectedValue, (boolean) object.mMyOption.get(expectedKey));
   }

   /**
    * Test passing an single argument for an object that has one option specified.
    */
   public void testParseMapArg_mismatchKeyType() throws ConfigurationException {
       MapOptionSource object = new MapOptionSource();
       ArgsOptionParser parser = new ArgsOptionParser(object);
       final String expectedKey = "istanbul";
       final boolean expectedValue = true;
       try {
           parser.parse(new String[] {"--my_option", expectedKey, Boolean.toString(expectedValue)});
           fail("ConfigurationException not thrown");
       } catch (ConfigurationException e) {
           // expect an exception that explicitly mentions that the "key" is incorrect
           assertTrue(String.format("Expected exception message to contain 'key': %s",
                   e.getMessage()), e.getMessage().contains("key"));
           assertTrue(String.format("Expected exception message to contain '%s': %s",
                   expectedKey, e.getMessage()), e.getMessage().contains(expectedKey));
       }
   }

   /**
    * Test passing an single argument for an object that has one option specified.
    */
   public void testParseMapArg_mismatchValueType() throws ConfigurationException {
       MapOptionSource object = new MapOptionSource();
       ArgsOptionParser parser = new ArgsOptionParser(object);
       final int expectedKey = 13;
       final String expectedValue = "notconstantinople";
       try {
           parser.parse(new String[] {"--my_option", Integer.toString(expectedKey), expectedValue});
           fail("ConfigurationException not thrown");
       } catch (ConfigurationException e) {
           // expect an exception that explicitly mentions that the "value" is incorrect
           assertTrue(String.format("Expected exception message to contain 'value': '%s'",
                   e.getMessage()), e.getMessage().contains("value"));
           assertTrue(String.format("Expected exception message to contain '%s': %s",
                   expectedValue, e.getMessage()), e.getMessage().contains(expectedValue));
       }
   }

   /**
    * Test passing an single argument for an object that has one option specified.
    */
   public void testParseMapArg_missingKey() throws ConfigurationException {
       MapOptionSource object = new MapOptionSource();
       ArgsOptionParser parser = new ArgsOptionParser(object);
       try {
           parser.parse(new String[] {"--my_option"});
           fail("ConfigurationException not thrown");
       } catch (ConfigurationException e) {
           // expect an exception that explicitly mentions that the "key" is incorrect
           assertTrue(String.format("Expected exception message to contain 'key': '%s'",
                   e.getMessage()), e.getMessage().contains("key"));
       }
   }

   /**
    * Test passing an single argument for an object that has one option specified.
    */
   public void testParseMapArg_missingValue() throws ConfigurationException {
       MapOptionSource object = new MapOptionSource();
       ArgsOptionParser parser = new ArgsOptionParser(object);
       final int expectedKey = 13;
       try {
           parser.parse(new String[] {"--my_option", Integer.toString(expectedKey)});
           fail("ConfigurationException not thrown");
       } catch (ConfigurationException e) {
           // expect an exception that explicitly mentions that the "value" is incorrect
           assertTrue(String.format("Expected exception message to contain 'value': '%s'",
                   e.getMessage()), e.getMessage().contains("value"));
       }
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
       try {
           parser.parse(new String[] {"--my_option", "set", "--not_here", "value"});
           fail("ConfigurationException not thrown");
       } catch (ConfigurationException e) {
           // expected
       }
   }

   /**
    * Test that help text is displayed for all fields
    */
   public void testGetOptionHelp() {
       String help = ArgsOptionParser.getOptionHelp(new InheritedOptionSource());
       assertTrue(help.contains(InheritedOptionSource.OPTION_NAME));
       assertTrue(help.contains(InheritedOptionSource.OPTION_DESC));
       assertTrue(help.contains(OneOptionSource.OPTION_NAME));
       assertTrue(help.contains(OneOptionSource.OPTION_DESC));
       assertTrue(help.contains(OneOptionSource.DEFAULT_VALUE));
   }
}
