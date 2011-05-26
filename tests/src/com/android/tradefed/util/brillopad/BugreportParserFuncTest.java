/*
 * Copyright (C) 2011 The Android Open Source Project
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
package com.android.tradefed.util.brillopad;

import com.android.tradefed.util.brillopad.item.IItem;

import org.easymock.EasyMock;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.List;

import junit.framework.TestCase;

/**
 * Functional tests for {@link BugreportParser}
 */
public class BugreportParserFuncTest extends TestCase {
    private static final File BR_FILE = new File("/tmp/bugreport.txt");

/* FIXME: flesh this out with known bugreports
    public void disable_testFullMockedParse() throws Exception {
        BugreportParser parser = new BugreportParser();
        ISectionParser mockSP = EasyMock.createStrictMock(ISectionParser.class);
        parser.addSectionParser(mockSP, "------ MEMORY INFO .*");

        // Set up expectations
        EasyMock.expect(mockSP.parseBlock((List<String>)EasyMock.anyObject())).andReturn(null);
        EasyMock.replay(mockSP);

        if (!BR_FILE.exists()) {
            fail(String.format("Full Parse test requires sample bugreport at %s", BR_FILE));
        }

        BufferedReader reader = new BufferedReader(new FileReader(BR_FILE));
        parser.parse(reader);
        EasyMock.verify(mockSP);
    }
*/

    /**
     * A "test" that is intended to force Brillopad to parse a bugreport found at {@code BR_FILE}.
     * The purpose of this is to assist a developer in checking why a given bugreport file might not
     * be parsed correctly by Brillopad.
     * <p />
     * Because the name doesn't start with "test", this method won't be picked up automatically by a
     * JUnit test runner, and must be run by hand.  This is as intended.
     */
    public void manualTestFullParse() throws Exception {
        BugreportParser parser = new BugreportParser();

        if (!BR_FILE.exists()) {
            fail(String.format("Full Parse test requires sample bugreport at %s", BR_FILE));
        }

        BufferedReader reader = new BufferedReader(new FileReader(BR_FILE));
        ItemList bugreport = new ItemList();
        parser.parse(reader, bugreport);

        List<IItem> jc = bugreport.getByType("JAVA CRASH");
        List<IItem> nc = bugreport.getByType("NATIVE CRASH");
        List<IItem> anr = bugreport.getByType("ANR");
        System.err.format("Got %d items for JAVA CRASH, %d for NATIVE CRASH, and %d for ANR\n",
                jc.size(), nc.size(), anr.size());
        for (IItem item : bugreport.getItems()) {
            System.err.format("Got item with type %s\n", item.getType());
        }
    }
}

