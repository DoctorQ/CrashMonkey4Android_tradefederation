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
}
