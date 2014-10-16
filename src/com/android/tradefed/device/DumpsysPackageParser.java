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

import com.android.tradefed.log.LogUtil.CLog;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for 'adb shell dumpsys package p' output.
 */
class DumpsysPackageParser {

    /** the text that marks the beginning of the hidden system packages section in output */
    private static final String HIDDEN_SYSTEM_PACKAGES_PREFIX = "Hidden system packages:";

    /** the text marking the start of a single package's output */
    private static final String PACKAGE_START = "Package\\s\\[";

    /** package name + flags regex for 4.2 platforms and below where pkgFlags is a hex number */
    private static final Pattern PKG_DATA_PATTERN = Pattern.compile(
            "^([\\w\\.]+)\\].*pkgFlags=0x([0-9a-fA-F]+)", Pattern.DOTALL);

    /** package name + flags regex for platforms newer than 4.2 where pkgFlags is a string */
    private static final Pattern PKG_DATA_STRING_PATTERN = Pattern.compile(
            "^([\\w\\.]+)\\].*pkgFlags=\\[([\\w\\s]+)\\]", Pattern.DOTALL);

    /** package name regex for hidden system packages */
    private static final Pattern HIDDEN_PKG_PATTERN = Pattern.compile("^([\\w\\.]+)\\]",
            Pattern.DOTALL);

    // numeric flag constants. Copied from
    // frameworks/base/core/java/android/content/pm/ApplicationInfo.java
    private static final int FLAG_UPDATED_SYSTEM_APP = 1 << 7;
    private static final int FLAG_SYSTEM = 1 << 0;

    // string flag constants. Used for newer platforms
    private static final String FLAG_UPDATED_SYSTEM_APP_TEXT = " UPDATED_SYSTEM_APP ";
    private static final String FLAG_SYSTEM_TEXT = " SYSTEM ";

    static class PackageInfo {

        final String packageName;
        final boolean isSystemApp;
        boolean isUpdatedSystemApp;

        PackageInfo(String pkgName, boolean systemApp, boolean updatedSystemApp) {
            packageName = pkgName;
            isSystemApp = systemApp;
            isUpdatedSystemApp = updatedSystemApp;
        }
    }

    @SuppressWarnings("serial")
    static class ParseException extends IOException {
        ParseException(String msg) {
            super(msg);
        }

        ParseException(String msg, Throwable t) {
            super(msg, t);
        }

    }

    /**
     * Parse package data from 'dumpsys package p' output from device.
     *
     * @param data the {@link String} data
     * @return a (@link DumpsysPackageParser} containing parsed data
     * @throws ParseException
     */
    public static DumpsysPackageParser parse(String data) throws ParseException {
        DumpsysPackageParser p = new DumpsysPackageParser();
        p.doParse(data);
        return p;
    }

    private Map<String, PackageInfo> mPkgInfoMap = new HashMap<String, PackageInfo>();

    /**
     * Perform the parsing of the entire 'dumpsys package p' output.
     */
    void doParse(String data) throws ParseException {
        if (data.length() == 0) {
            return;
        }
        // first find the hidden system package section, which is expected at end of output
        int hiddenPkgIndex = data.lastIndexOf(HIDDEN_SYSTEM_PACKAGES_PREFIX);
        if (hiddenPkgIndex == -1) {
            // didn't find hidden system package data. Mark index at end of data so remaining logic
            // just works
            hiddenPkgIndex = data.length() - 1;
        }
        String packageData = data.substring(0, hiddenPkgIndex);
        String hiddenPkgData = data.substring(hiddenPkgIndex, data.length() - 1);

        parsePackagesData(packageData);
        parseHiddenSystemPackages(hiddenPkgData);
    }

    /**
     * Parse the set of package data output
     */
    private void parsePackagesData(String data) throws ParseException {
        String[] pkgTexts = data.split(PACKAGE_START);
        for (String pkgText : pkgTexts) {
            PackageInfo p = parsePackageData(pkgText);
            if (p != null) {
                mPkgInfoMap.put(p.packageName, p);
            }
        }
    }

    /**
     * Parse a single package's output
     */
    PackageInfo parsePackageData(String pkgText) throws ParseException {
        // first try to parse the output using 'classic', numeric flag format
        PackageInfo p = parsePackageDataNumericFlags(pkgText);
        if (p == null) {
            // that didn't work. Maybe its the new improved flavor, with flags in string form
            p = parsePackageDataStringFlags(pkgText);
        }
        return p;
    }

    /**
     * Attempt to parse a {@link PackageInfo} from a single package's output assuming the numeric
     * package flag regex.
     *
     * @return the {@link PackageInfo} or <code>null</code>
     */
    private PackageInfo parsePackageDataNumericFlags(String pkgText) throws ParseException {
        Matcher matcher = PKG_DATA_PATTERN.matcher(pkgText);
        if (matcher.find()) {
            String name = matcher.group(1);
            int flags = parseHexInt(matcher.group(2));
            boolean isSystem = (flags & FLAG_SYSTEM) != 0;
            // note: FLAG_UPDATED_SYSTEM_APP never seems to be set. Rely on parsing hidden system
            // packages
            boolean isUpdatedSystem = (flags & FLAG_UPDATED_SYSTEM_APP) != 0;
            return new PackageInfo(name, isSystem, isUpdatedSystem);
        }
        return null;
    }

    /**
     * Attempt to parse a {@link PackageInfo} from a single package's output assuming the string
     * package flag regex.
     *
     * @return the {@link PackageInfo} or <code>null</code>
     */
    private PackageInfo parsePackageDataStringFlags(String pkgText) {
        Matcher matcher = PKG_DATA_STRING_PATTERN.matcher(pkgText);
        if (matcher.find()) {
            String name = matcher.group(1);
            String flagString = matcher.group(2);
            boolean isSystem = flagString.contains(FLAG_SYSTEM_TEXT);
            boolean isUpdatedSystem = flagString.contains(FLAG_UPDATED_SYSTEM_APP_TEXT);
            return new PackageInfo(name, isSystem, isUpdatedSystem);
        }
        return null;
    }

    /**
     * Convert given hexadecimal string to an int.
     *
     * @throws ParseException if failed to parse
     */
    private int parseHexInt(String s) throws ParseException {
        try {
            return Integer.parseInt(s, 16);
        } catch (NumberFormatException e) {
            throw new ParseException(String.format("Unexpected flags value %s", s), e);
        }
    }

    /**
     * Parse the entire hidden system packages text.
     */
    private void parseHiddenSystemPackages(String s) {
        String[] pkgTexts = s.split(PACKAGE_START);
        for (String pkgText : pkgTexts) {
            Matcher matcher = HIDDEN_PKG_PATTERN.matcher(pkgText);
            if (matcher.find()) {
                String name = matcher.group(1);
                PackageInfo p = mPkgInfoMap.get(name);
                if (p != null) {
                    p.isUpdatedSystemApp = true;
                } else {
                    CLog.w("Failed to find package info for hidden system package %s", name);
                }
            }
        }
    }

    /**
     * @return the parsed {@link PackageInfo}.
     */
    public Collection<PackageInfo> getPackages() {
        return mPkgInfoMap.values();
    }
}
