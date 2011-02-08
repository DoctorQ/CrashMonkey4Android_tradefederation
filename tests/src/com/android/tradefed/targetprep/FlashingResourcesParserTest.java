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

package com.android.tradefed.targetprep;

import com.android.tradefed.targetprep.FlashingResourcesParser;
import com.android.tradefed.targetprep.IFlashingResourcesParser;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.util.FileUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import junit.framework.TestCase;

/**
 * Unit tests for {@link FlashingResourcesParser}.
 */
public class FlashingResourcesParserTest extends TestCase {

    /**
     * Test that {@link FlashingResourcesParser#parseAndroidInfo(BufferedReader)} parses valid data
     * correctly.
     */
    public void testParseAndroidInfo() throws IOException {
        final String validInfoData = "require board=board1|board2\n" + // valid
                "require version-bootloader=1.0.1\n" + // valid
                "cylon=blah\n" + // valid
                "blah"; // not valid
        BufferedReader reader = new BufferedReader(new StringReader(validInfoData));
        Map<String, List<String>> result = FlashingResourcesParser.parseAndroidInfo(reader);
        assertEquals(3, result.size());
        List<String> boards = result.get(FlashingResourcesParser.BOARD_KEY);
        assertEquals(2, boards.size());
        assertEquals("board1", boards.get(0));
        assertEquals("board2", boards.get(1));
        List<String> bootloaders = result.get(FlashingResourcesParser.BOOTLOADER_VERSION_KEY);
        assertEquals("1.0.1", bootloaders.get(0));
    }

    /**
     * Test that {@link FlashingResourcesParser#parseAndroidInfo(BufferedReader)} parses valid data
     * correctly.
     *
     * When both 'require board=foo' and 'require product=bar' lines are present, the board line
     * should supercede the product line
     */
    public void testParseAndroidInfo_boardAndProduct() throws Exception {
        final String validInfoData = "require product=alpha|beta\n" +
                "require board=gamma|delta";
        BufferedReader reader = new BufferedReader(new StringReader(validInfoData));

        IFlashingResourcesParser parser = new FlashingResourcesParser(reader);
        Collection<String> reqBoards = parser.getRequiredBoards();
        assertEquals(2, reqBoards.size());
        assertTrue(reqBoards.contains("gamma"));
        assertTrue(reqBoards.contains("delta"));
    }

    /**
     * Test that {@link FlashingResourcesParser#parseAndroidInfo(BufferedReader)} parses valid data
     * correctly.
     */
    public void testParseAndroidInfo_onlyBoard() throws Exception {
        final String validInfoData = "require board=gamma|delta";
        BufferedReader reader = new BufferedReader(new StringReader(validInfoData));

        IFlashingResourcesParser parser = new FlashingResourcesParser(reader);
        Collection<String> reqBoards = parser.getRequiredBoards();
        assertEquals(2, reqBoards.size());
        assertTrue(reqBoards.contains("gamma"));
        assertTrue(reqBoards.contains("delta"));
    }

    /**
     * Test that {@link FlashingResourcesParser#parseAndroidInfo(BufferedReader)} parses valid data
     * correctly.
     *
     * When only 'require product=bar' line is present, it should be passed out in lieu of the
     * (missing) board line.
     */
    public void testParseAndroidInfo_onlyProduct() throws Exception {
        final String validInfoData = "require product=alpha|beta";
        BufferedReader reader = new BufferedReader(new StringReader(validInfoData));

        IFlashingResourcesParser parser = new FlashingResourcesParser(reader);
        Collection<String> reqBoards = parser.getRequiredBoards();
        assertEquals(2, reqBoards.size());
        assertTrue(reqBoards.contains("alpha"));
        assertTrue(reqBoards.contains("beta"));
    }

    /**
     * Test {@link FlashingResourcesParser#getBuildRequirements(File)} when passed a file that
     * is not a zip.
     */
    public void testGetBuildRequirements_notAZip() throws IOException {
        File badFile = FileUtil.createTempFile("foo", ".zip");
        try {
            new FlashingResourcesParser(badFile);
            fail("TargetSetupError not thrown");
        } catch (TargetSetupError e) {
            // expected
        } finally {
            badFile.delete();
        }
    }
}
