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

package com.android.tradefed.command;

import com.android.tradefed.config.ConfigurationException;

import org.easymock.EasyMock;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;

import junit.framework.TestCase;

/**
 * Unit tests for {@link ConfigFileParser}
 */
public class ConfigFileParserTest extends TestCase {

    /** the {@link ConfigFileParser} under test, with all dependencies mocked out */
    private ConfigFileParser mCommandFile;
    private String mMockFileData = "";
    private File mMockFile = new File("tmp");
    private ICommandScheduler mMockScheduler;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mMockScheduler = EasyMock.createMock(ICommandScheduler.class);
        mCommandFile = new ConfigFileParser() {
            @Override
            BufferedReader createConfigFileReader(File file) {
               return new BufferedReader(new StringReader(mMockFileData));
            }
        };
    }

    /**
     * Test parsing a config file with a comment and a single config.
     */
    public void testParse_singleConfig() throws Exception {
        // inject mock file data
        mMockFileData = "  #Comment followed by blank line\n \n--foo  config";
        String[] expectedArgs = new String[] {
                "--foo", "config"
        };

        mMockScheduler.addConfig(EasyMock.aryEq(expectedArgs));

        EasyMock.replay(mMockScheduler);
        mCommandFile.parseFile(mMockFile, mMockScheduler);
        EasyMock.verify(mMockScheduler);
    }

    /**
     * Make sure that a config with a quoted argument is parsed properly.
     * <p/>
     * Whitespace inside of the quoted section should be preserved. Also, embedded escaped quotation
     * marks should be ignored.
     */
    public void testParseFile_quotedConfig() throws IOException, ConfigurationException  {
        // inject mock file data
        mMockFileData = "--foo \"this is a config\" --bar \"escap\\\\ed \\\" quotation\"";
        String[] expectedArgs = new String[] {
                "--foo", "this is a config", "--bar", "escap\\\\ed \\\" quotation"
        };

        mMockScheduler.addConfig(EasyMock.aryEq(expectedArgs));

        EasyMock.replay(mMockScheduler);
        mCommandFile.parseFile(mMockFile, mMockScheduler);
        EasyMock.verify(mMockScheduler);
    }

    /**
     * Test the data where the configuration ends inside a quotation.
     */
    public void testParseFile_endOnQuote() throws IOException {
        // inject mock file data
        mMockFileData = "--foo \"this is truncated";

        EasyMock.replay(mMockScheduler);
        try {
            mCommandFile.parseFile(mMockFile, mMockScheduler);
            fail("ConfigurationException not thrown");
        } catch (ConfigurationException e) {
            // expected
        }
        EasyMock.verify(mMockScheduler);
    }

    /**
     * Test the scenario where the configuration ends inside a quotation.
     */
    public void testRun_endWithEscape() throws IOException {
        // inject mock file data
        mMockFileData = "--foo escape\\";
        // switch mock objects to verify mode
        EasyMock.replay(mMockScheduler);
        try {
            mCommandFile.parseFile(mMockFile, mMockScheduler);
            fail("ConfigurationException not thrown");
        } catch (ConfigurationException e) {
            // expected
        }
        EasyMock.verify(mMockScheduler);
    }

    // Macro-related tests
    public void testSimpleMacro() throws IOException, ConfigurationException {
        mMockFileData = "MACRO test = verify\ntest()";
        String[] expectedArgs = new String[] {"verify"};

        mMockScheduler.addConfig(EasyMock.aryEq(expectedArgs));

        EasyMock.replay(mMockScheduler);
        mCommandFile.parseFile(mMockFile, mMockScheduler);
        EasyMock.verify(mMockScheduler);
    }

    /**
     * Ensure that parsing of quoted tokens continues to work
     */
    public void testSimpleMacro_quotedTokens() throws IOException, ConfigurationException {
        mMockFileData = "MACRO test = \"verify varify vorify\"\ntest()";
        String[] expectedArgs = new String[] {"verify varify vorify"};

        mMockScheduler.addConfig(EasyMock.aryEq(expectedArgs));

        EasyMock.replay(mMockScheduler);
        mCommandFile.parseFile(mMockFile, mMockScheduler);
        EasyMock.verify(mMockScheduler);
    }

    /**
     * Ensure that parsing of names with embedded underscores works properly.
     */
    public void testSimpleMacro_underscoreName() throws IOException, ConfigurationException {
        mMockFileData = "MACRO under_score = verify\nunder_score()";
        String[] expectedArgs = new String[] {"verify"};

        mMockScheduler.addConfig(EasyMock.aryEq(expectedArgs));

        EasyMock.replay(mMockScheduler);
        mCommandFile.parseFile(mMockFile, mMockScheduler);
        EasyMock.verify(mMockScheduler);
    }

    /**
     * Test the scenario where a macro call doesn't resolve.
     */
    public void testUndefinedMacro() throws IOException {
        mMockFileData = "test()";
        EasyMock.replay(mMockScheduler);
        try {
            mCommandFile.parseFile(mMockFile, mMockScheduler);
            fail("ConfigurationException not thrown");
        } catch (ConfigurationException e) {
            // expected
        }
        EasyMock.verify(mMockScheduler);
    }

    /**
     * Test the scenario where a syntax problem causes a macro call to not resolve.
     */
    public void testUndefinedMacro_defSyntaxError() throws IOException {
        mMockFileData = "MACRO test = \n" +
                "test()";
        EasyMock.replay(mMockScheduler);
        try {
            mCommandFile.parseFile(mMockFile, mMockScheduler);
            fail("ConfigurationException not thrown");
        } catch (ConfigurationException e) {
            // expected
        }
        EasyMock.verify(mMockScheduler);
    }

    /**
     * Simple test for LONG MACRO parsing
     */
    public void testSimpleLongMacro() throws IOException, ConfigurationException {
        mMockFileData = "LONG MACRO test\n" +
                "verify\n" +
                "END MACRO\n" +
                "test()";
        String[] expectedArgs = new String[] {"verify"};

        mMockScheduler.addConfig(EasyMock.aryEq(expectedArgs));

        EasyMock.replay(mMockScheduler);
        mCommandFile.parseFile(mMockFile, mMockScheduler);
        EasyMock.verify(mMockScheduler);
    }

    /**
     * Simple test for LONG MACRO parsing with multi-line expansion
     */
    public void testSimpleLongMacro_multiline() throws IOException, ConfigurationException {
        mMockFileData = "LONG MACRO test\n" +
                "one two three\n" +
                "a b c\n" +
                "do re mi\n" +
                "END MACRO\n" +
                "test()";
        String[] expectedArgs1 = new String[] {"one", "two", "three"};
        String[] expectedArgs2 = new String[] {"a", "b", "c"};
        String[] expectedArgs3 = new String[] {"do", "re", "mi"};

        mMockScheduler.addConfig(EasyMock.aryEq(expectedArgs1));
        mMockScheduler.addConfig(EasyMock.aryEq(expectedArgs2));
        mMockScheduler.addConfig(EasyMock.aryEq(expectedArgs3));

        EasyMock.replay(mMockScheduler);
        mCommandFile.parseFile(mMockFile, mMockScheduler);
        EasyMock.verify(mMockScheduler);
    }

    /**
     * Simple test for LONG MACRO parsing with multi-line expansion
     */
    public void testLongMacro_withComment() throws IOException, ConfigurationException {
        mMockFileData = "LONG MACRO test\n" +
                "\n" +  // blank line
                "one two three\n" +
                "#a b c\n" +
                "do re mi\n" +
                "END MACRO\n" +
                "test()";
        String[] expectedArgs1 = new String[] {"one", "two", "three"};
        String[] expectedArgs2 = new String[] {"do", "re", "mi"};

        mMockScheduler.addConfig(EasyMock.aryEq(expectedArgs1));
        mMockScheduler.addConfig(EasyMock.aryEq(expectedArgs2));

        EasyMock.replay(mMockScheduler);
        mCommandFile.parseFile(mMockFile, mMockScheduler);
        EasyMock.verify(mMockScheduler);
    }

    /**
     * Test the scenario where the configuration ends inside of a LONG MACRO definition.
     */
    public void testLongMacroSyntaxError_eof() throws IOException {
        mMockFileData = "LONG MACRO test\n" +
                "verify\n" +
                // "END MACRO\n" (this is the syntax error)
                "test()";
        EasyMock.replay(mMockScheduler);
        try {
            mCommandFile.parseFile(mMockFile, mMockScheduler);
            fail("ConfigurationException not thrown");
        } catch (ConfigurationException e) {
            // expected
        }
        EasyMock.verify(mMockScheduler);
    }

    /**
     * A testcase to make sure that the internal bitmask and mLines stay in sync
     * <p>
     * This tickles a bug in the current implementation (before I fix it).  The problem is here,
     * where the inputBitmask is only set to false conditionally, but inputBitmaskCount is
     * decremented unconditionally:
     * <code>inputBitmask.set(inputIdx, sawMacro);
     * --inputBitmaskCount;</code>
     */
    public void testMacroParserSync_suffix() throws IOException, ConfigurationException {
        mMockFileData = "MACRO alpha = one beta()\n" +
                "MACRO beta = two\n" +
                "alpha()\n";
        String[] expectedArgs = new String[] {"one", "two"};
        // When the bug manifests, the result is {"one", "alpha()"}

        mMockScheduler.addConfig(EasyMock.aryEq(expectedArgs));

        EasyMock.replay(mMockScheduler);
        mCommandFile.parseFile(mMockFile, mMockScheduler);
        EasyMock.verify(mMockScheduler);
    }

    /**
     * A testcase to make sure that the internal bitmask and mLines stay in sync
     * <p>
     * This tests a case related to the _suffix test above.
     */
    public void testMacroParserSync_prefix() throws IOException, ConfigurationException {
        mMockFileData = "MACRO alpha = beta() two\n" +
                "MACRO beta = one\n" +
                "alpha()\n";
        String[] expectedArgs = new String[] {"one", "two"};

        mMockScheduler.addConfig(EasyMock.aryEq(expectedArgs));

        EasyMock.replay(mMockScheduler);
        mCommandFile.parseFile(mMockFile, mMockScheduler);
        EasyMock.verify(mMockScheduler);
    }
}
