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

package com.android.tradefed.util;

import junit.framework.TestCase;

import java.util.Arrays;

/**
 * Unit tests for {@link QuotationAwareTokenizer}
 */
public class QuotationAwareTokenizerTest extends TestCase {

    private static void verify(String input, String[] expected) throws IllegalArgumentException {
        String[] observed = QuotationAwareTokenizer.tokenizeLine(input);

        if (expected.length != observed.length) {
            fail(String.format("Expected and observed arrays are different lengths: expected %s " +
                    "but observed %s.", Arrays.toString(expected), Arrays.toString(observed)));
        }

        for (int i = 0; i < expected.length; ++i) {
            assertEquals(String.format("Array compare failed at element %d:", i, expected[i],
                    observed[i]), expected[i], observed[i]);
        }
    }

    /**
     * Simple parse
     */
    public void testParse_simple() throws IllegalArgumentException {
        String input = "  one  two three";
        String[] expected = new String[] {"one", "two", "three"};
        verify(input, expected);
    }

    /**
     * Whitespace inside of the quoted section should be preserved. Also, embedded escaped quotation
     * marks should be ignored.
     */
    public void testParse_quotation() throws IllegalArgumentException {
        String input = "--foo \"this is a config\" --bar \"escap\\\\ed \\\" quotation\"";
        String[] expected = new String[] {"--foo", "this is a config", "--bar",
                "escap\\\\ed \\\" quotation"};
        verify(input, expected);
    }

    /**
     * Test the scenario where the input ends inside a quotation.
     */
    public void testParseFile_endOnQuote() {
        String input = "--foo \"this is truncated";

        try {
            QuotationAwareTokenizer.tokenizeLine(input);
            fail("IllegalArgumentException not thrown.");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    /**
     * Test the scenario where the input ends in the middle of an escape character.
     */
    public void testRun_endWithEscape() {
        String input = "--foo escape\\";

        try {
            QuotationAwareTokenizer.tokenizeLine(input);
            fail("IllegalArgumentException not thrown.");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }
}
