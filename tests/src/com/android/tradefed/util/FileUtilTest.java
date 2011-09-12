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
package com.android.tradefed.util;

import junit.framework.TestCase;

import java.io.File;
import java.io.IOException;

/**
 * Unit tests for {@link FileUtil}
 */
public class FileUtilTest extends TestCase {
    public void testGetExtension() {
        assertEquals("", FileUtil.getExtension("filewithoutext"));
        assertEquals(".txt", FileUtil.getExtension("file.txt"));
        assertEquals(".txt", FileUtil.getExtension("foo.file.txt"));
    }

    /**
     * Test method for {@link FileUtil#createTempFileForRemote(String, File)}.
     */
    public void testCreateTempFileForRemote() throws IOException {
        String remoteFilePath = "path/userdata.img";
        File tmpFile = FileUtil.createTempFileForRemote(remoteFilePath, null);
        assertTrue(tmpFile.getAbsolutePath().contains("userdata"));
        assertTrue(tmpFile.getAbsolutePath().endsWith(".img"));
    }

    /**
     * Test method for {@link FileUtil#createTempFileForRemote(String, File)} for a nested path.
     */
    public void testCreateTempFileForRemote_nested() throws IOException {
        String remoteFilePath = "path/2path/userdata.img";
        File tmpFile = FileUtil.createTempFileForRemote(remoteFilePath, null);
        assertTrue(tmpFile.getAbsolutePath().contains("userdata"));
        assertTrue(tmpFile.getAbsolutePath().endsWith(".img"));
    }

    /**
     * Test {@link FileUtil#createTempFileForRemote(String, File)} for file with no extension
     */
    public void testCreateTempFileForRemote_noext() throws IOException {
        String remoteFilePath = "path/2path/userddddmg";
        File tmpFile = FileUtil.createTempFileForRemote(remoteFilePath, null);
        assertTrue(tmpFile.getAbsolutePath().contains("userddddmg"));
    }

    /**
     * Test {@link FileUtil#createTempFileForRemote(String, File)} for a too small prefix.
     */
    public void testCreateTempFileForRemote_short() throws IOException {
        String remoteFilePath = "path/2path/us.img";
        File tmpFile = FileUtil.createTempFileForRemote(remoteFilePath, null);
        assertTrue(tmpFile.getAbsolutePath().contains("usXXX"));
        assertTrue(tmpFile.getAbsolutePath().endsWith(".img"));
    }

    /**
     * Test {@link FileUtil#createTempFileForRemote(String, File)} for remoteFile in root path.
     */
    public void testCreateTempFileForRemote_singleFile() throws IOException {
        String remoteFilePath = "userdata.img";
        File tmpFile = FileUtil.createTempFileForRemote(remoteFilePath, null);
        assertTrue(tmpFile.getAbsolutePath().contains("userdata"));
        assertTrue(tmpFile.getAbsolutePath().endsWith(".img"));
    }
}
