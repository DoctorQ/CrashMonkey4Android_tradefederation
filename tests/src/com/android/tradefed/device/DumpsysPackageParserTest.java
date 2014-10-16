/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.tradefed.device;

import com.android.tradefed.device.DumpsysPackageParser.PackageInfo;
import com.android.tradefed.device.DumpsysPackageParser.ParseException;

import junit.framework.TestCase;

import java.util.Collection;

/**
 * Unit tests for {@link DumpsysPackageParser}.
 */
public class DumpsysPackageParserTest extends TestCase {

    /**
     * Verifies parse correctly handles 'dumpsys package p' output from 4.2 and below.
     */
    public void testParse_classic() throws Exception {
        final String froyoPkgTxt = "com.android.soundrecorder] (462f6b38):\r\n"
                + "targetSdk=8\r\n"
                + "pkgFlags=0x1 installStatus=1 enabled=0\r\n";

        DumpsysPackageParser p = new DumpsysPackageParser();
        PackageInfo pkg = p.parsePackageData(froyoPkgTxt);
        assertNotNull("failed to parse package data", pkg);
        assertEquals("com.android.soundrecorder", pkg.packageName);
        assertTrue(pkg.isSystemApp);
        assertFalse(pkg.isUpdatedSystemApp);
    }

    /**
     * Verifies parse correctly handles new textual 'dumpsys package p' output from newer releases.
     */
    public void testParse_future() throws Exception {
        final String pkgTxt = "com.android.soundrecorder] (462f6b38):\r\n"
                + "targetSdk=8\r\n"
                + "pkgFlags=[ SYSTEM HAS_CODE ALLOW_CLEAR_USER_DATA ALLOW_BACKUP ]"
                + "installed=true\r\n";

        DumpsysPackageParser p = new DumpsysPackageParser();
        PackageInfo pkg = p.parsePackageData(pkgTxt);
        assertNotNull("failed to parse package data", pkg);
        assertEquals("com.android.soundrecorder", pkg.packageName);
        assertTrue(pkg.isSystemApp);
        assertFalse(pkg.isUpdatedSystemApp);
    }

    /**
     * Verifies parse correctly handles 'dumpsys package p' output with hidden system package info
     */
    public void testParse_hidden() throws Exception {
        final String pkgsTxt = "Package [com.android.soundrecorder] (462f6b38):\r\n"
                + "targetSdk=8\r\n"
                + "pkgFlags=[ SYSTEM HAS_CODE ALLOW_CLEAR_USER_DATA ALLOW_BACKUP ]"
                + "installed=true\r\n"
                + "Hidden system packages:\r\n"
                + "  Package [com.android.soundrecorder] (686868):\r\n";

        DumpsysPackageParser parser = DumpsysPackageParser.parse(pkgsTxt);
        Collection<PackageInfo> pkgs = parser.getPackages();
        assertEquals("failed to parse package data", 1, pkgs.size());
        PackageInfo pkg = pkgs.iterator().next();
        assertEquals("com.android.soundrecorder", pkg.packageName);
        assertTrue(pkg.isSystemApp);
        assertTrue(pkg.isUpdatedSystemApp);
    }

    /**
     * Verifies parse handles empty input
     */
    public void testParse_empty() throws ParseException {
        DumpsysPackageParser parser = DumpsysPackageParser.parse("");
        assertEquals(0,  parser.getPackages().size());
    }
}
