/*
 * Copyright (C) 2012 The Android Open Source Project
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

import com.android.tradefed.log.LogUtil.CLog;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Class that extracts info from apk by parsing output of 'aapt dump badging'.
 * <p/>
 * aapt must be on PATH
 */
public class AaptParser {

    private String mPackageName;

    // @VisibleForTesting
    AaptParser() {
    }

    void parse(String aaptOut) {
        Pattern p = Pattern.compile("package: name='(.*?)'");
        Matcher m = p.matcher(aaptOut);
        if (m.find()) {
            mPackageName = m.group(1);
        } else {
            CLog.e("Failed to parse package name from 'aapt dump badging'");
        }
    }

    /**
     * Parse info from the apk.
     *
     * @param apkFile the apk file
     * @return the {@link AaptParser} or <code>null</code> if failed to extract the information
     */
    public static AaptParser parse(File apkFile) {
        CommandResult result = RunUtil.getDefault().runTimedCmd(5000, "aapt", "dump", "badging",
                apkFile.getAbsolutePath());
        if (result.getStatus() == CommandStatus.SUCCESS) {
            AaptParser p = new AaptParser();
            p.parse(result.getStdout());
            return p;
        }
        CLog.e("Failed to run aapt on %s", apkFile.getAbsoluteFile());
        return null;
    }

    public String getPackageName() {
        return mPackageName;
    }

}
