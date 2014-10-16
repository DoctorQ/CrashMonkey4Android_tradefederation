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
package com.android.tradefed.util;

import java.io.File;
import java.io.IOException;

import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.FileUtil;

public class VersionParser {
    private static final String DEFAULT_VERSION_FILE_NAME = "tf_version.txt";

    public static String fetchVersion(File file) {
        if (file.exists()) {
            try {
                return FileUtil.readStringFromFile(file);
            } catch (IOException e) {
               CLog.e(e.toString());
               return null;
            }
        }
      CLog.w("File %s does not exist, unable to get version", file.getAbsolutePath());
      return null;
    }

    public static String fetchVersion() {
        File file = new File(DEFAULT_VERSION_FILE_NAME);
        if (!file.exists()) {
            // Try looking in the path where the jar is.
            File path = new File(VersionParser.class.getProtectionDomain().getCodeSource().
                    getLocation().getPath());
            file = new File(path.getParentFile(), DEFAULT_VERSION_FILE_NAME);
        }
        return fetchVersion(file);
    }
}
