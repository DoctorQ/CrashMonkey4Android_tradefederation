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

import com.android.ddmlib.Log;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Set;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * A class for loading all JUnit3 tests in a jar file
 */
public class TestLoader {

    private static final String LOG_TAG = "TestLoader";

    /**
     * Creates a {@link Test} containing all the {@link TestCase} found in given jar
     * @param jarFile
     * @return the {@link Test} containing all tests
     */
    public Test loadTests(File jarFile) {
        ClassPathScanner scanner = new ClassPathScanner();
        try {
            Set<String> classNames = scanner.getClassNamesFromJar(jarFile);
            URI jarUri = jarFile.toURI();
            ClassLoader jarClassLoader = new URLClassLoader(new URL[] {jarUri.toURL()}) ;
            return loadTests(classNames, jarClassLoader);
        } catch (IOException e) {
            Log.e(LOG_TAG, String.format("IOException when loading test classes from jar %s",
                    jarFile.getAbsolutePath()));
            Log.e(LOG_TAG, e);
        }
        return null;
    }

    private Test loadTests(Set<String> classNames, ClassLoader classLoader) {
        TestSuite testSuite = new TestSuite();
        for (String className : classNames) {
            try {
                Class<?> testClass = Class.forName(className, true, classLoader);
                if (TestCase.class.isAssignableFrom(testClass)) {
                    testSuite.addTestSuite(testClass);
                }
            }   catch (ClassNotFoundException e) {
                // ignore for now
            }
        }
        return testSuite;
    }
}
