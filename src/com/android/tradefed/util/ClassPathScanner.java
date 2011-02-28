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

package com.android.tradefed.util;

import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;

/**
 * Finds classes within a given jar.
 *
 * <p>Adapted from vogar.target.ClassPathScanner</p>
 */
public class ClassPathScanner {

    private static final String DOT_CLASS = ".class";
    private String[] mClassPath;

    ClassPathScanner() {
        mClassPath = getClassPath();
    }

    /**
     * Gets the names of all classes contained in given jar file
     */
    public Set<String> getClassNamesFromJar(File plainFile) throws IOException {
        Set<String> classNames = new HashSet<String>();
        JarFile jarFile = new JarFile(plainFile);
        for (Enumeration<? extends ZipEntry> e = jarFile.entries(); e.hasMoreElements(); ) {
            String entryName = e.nextElement().getName();
            if (entryName.endsWith(DOT_CLASS)) {
                String className = entryName.substring(0, entryName.length() - DOT_CLASS.length());
                className = className.replace('/', '.');
                classNames.add(className);
            }
        }
        return classNames;
    }

    /**
     * Gets the class path from the System Property "java.class.path" and splits
     * it up into the individual elements.
     */
    public static String[] getClassPath() {
        String classPath = System.getProperty("java.class.path");
        return classPath.split(Pattern.quote(File.pathSeparator));
    }
}
