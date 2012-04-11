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

import com.android.tradefed.util.brillopad.item.ProcrankItem;

import junit.framework.TestCase;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Unit tests for {@link ProcrankParser}
 */
public class ProcrankParserTest extends TestCase {
    public void testProcRankParser() {
        List<String> inputBlock = Arrays.asList(
                "  PID      Vss      Rss      Pss      Uss  cmdline",
                "  178   87136K   81684K   52829K   50012K  system_server",
                " 1313   78128K   77996K   48603K   45812K  com.google.android.apps.maps",
                " 3247   61652K   61492K   33122K   30972K  com.android.browser",
                "  334   55740K   55572K   29629K   28360K  com.android.launcher",
                " 2072   51348K   51172K   24263K   22812K  android.process.acore",
                " 1236   51440K   51312K   22911K   20608K  com.android.settings",
                "                 51312K   22911K   20608K  invalid.format",
                "                          ------   ------  ------",
                "                          203624K  163604K  TOTAL",
                "RAM: 731448K total, 415804K free, 9016K buffers, 108548K cached",
                "[procrank: 1.6s elapsed]");
        ProcrankParser parser = new ProcrankParser();
        Map<String, Integer> map;
        ProcrankItem procrank = parser.parse(inputBlock);

        // Ensures that only valid lines are parsed. Only 6 of the 11 lines under the header are
        // valid.
        assertEquals(6, procrank.size());

        // Make sure all expected rows are present, and do a diagonal check of values
        map = procrank.get("system_server");
        assertNotNull(map);
        assertEquals((Integer)178, map.get("PID"));

        map = procrank.get("com.google.android.apps.maps");
        assertNotNull(map);
        assertEquals((Integer)78128, map.get("Vss"));

        map = procrank.get("com.android.browser");
        assertNotNull(map);
        assertEquals((Integer)61492, map.get("Rss"));

        map = procrank.get("com.android.launcher");
        assertNotNull(map);
        assertEquals((Integer)29629, map.get("Pss"));

        map = procrank.get("android.process.acore");
        assertNotNull(map);
        assertEquals((Integer)22812, map.get("Uss"));

        map = procrank.get("com.android.settings");
        assertNotNull(map);
        assertEquals((Integer)1236, map.get("PID"));
    }
}

