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

import junit.framework.TestCase;

/**
 * Unit tests for {@link ArgsOptionParser}.
 */
public class ArgsOptionParserTest extends TestCase {

    /**
     * A option source with no {@link Option}'s specified.
     */
    private static class NoOptionSource {
    }

    /**
     * A option source with one {@link Option} specified.
     */
    private static class OneOptionSource {

        private static final String DEFAULT_VALUE = "default";

        @Option(name="my_option")
        private String mMyOption = DEFAULT_VALUE;
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

   // TODO: add tests for @Option's of different data types
}
