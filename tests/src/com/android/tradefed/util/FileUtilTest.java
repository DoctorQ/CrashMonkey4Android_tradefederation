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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import junit.framework.TestCase;

/**
 * Unit tests for {@link FileUtil}
 */
public class FileUtilTest extends TestCase {

    /**
     * Simple test for {@link FileUtil#setGroupReadWritable(File)}.
     */
    public void testSetGroupReadWritable() throws IOException {
        File tmpFile = FileUtil.createTempFile("foo", "txt");
        try {
            tmpFile.setReadable(false);
            tmpFile.setWritable(false);
            FileUtil.setGroupReadWritable(tmpFile);
            assertTrue(tmpFile.canRead());
            assertTrue(tmpFile.canWrite());
        } finally {
            tmpFile.delete();
        }
    }

    /**
     * Simple test for {@link FileUtil#createTempDir(String)}.
     */
    public void testCreateTempDir() throws IOException {
        File tmpDir = FileUtil.createTempDir("foo");
        try {
            assertTrue(tmpDir.exists());
            assertTrue(tmpDir.isDirectory());
        } finally {
            tmpDir.delete();
        }
    }

    /**
     * Simple test for {@link FileUtil#createTempDir(String, File)}.
     */
    public void testCreateTempDir_parentFile() throws IOException {
        File tmpParentDir = FileUtil.createTempDir("foo");
       try {
            File childDir = FileUtil.createTempDir("foochild", tmpParentDir);
            assertTrue(childDir.exists());
            assertTrue(childDir.isDirectory());
            assertEquals(tmpParentDir.getAbsolutePath(), childDir.getParent());
        } finally {
            FileUtil.recursiveDelete(tmpParentDir);
        }
    }

    /**
     * Simple test for {@link FileUtil#createTempFile(String, String)}.
     */
    public void testCreateTempFile() throws IOException {
        File tmpFile = FileUtil.createTempFile("foo", ".txt");
        try {
            assertTrue(tmpFile.exists());
            assertTrue(tmpFile.isFile());
            assertTrue(tmpFile.getName().startsWith("foo"));
            assertTrue(tmpFile.getName().endsWith(".txt"));
        } finally {
            tmpFile.delete();
        }
    }

    /**
     * Simple test for {@link FileUtil#createTempFile(String, String, File)}.
     */
    public void testCreateTempFile_parentDir() throws IOException {
        File tmpParentDir = FileUtil.createTempDir("foo");

        try {
            File tmpFile = FileUtil.createTempFile("foo", ".txt", tmpParentDir);
            assertTrue(tmpFile.exists());
            assertTrue(tmpFile.isFile());
            assertTrue(tmpFile.getName().startsWith("foo"));
            assertTrue(tmpFile.getName().endsWith(".txt"));
            assertEquals(tmpParentDir.getAbsolutePath(), tmpFile.getParent());
        } finally {
            FileUtil.recursiveDelete(tmpParentDir);
        }

    }

    /**
     * Simple test method for {@link FileUtil#writeToFile(InputStream, File)}.
     */
    public void testWriteToFile() throws IOException {
        final String testContents = "this is the temp file test data";
        InputStream input = new ByteArrayInputStream(testContents.getBytes());
        File tmpFile = FileUtil.createTempFile("foo", ".txt");
        try {
            FileUtil.writeToFile(input, tmpFile);
            String readContents = StreamUtil.getStringFromStream(new FileInputStream(tmpFile));
            assertEquals(testContents, readContents);
        } finally {
            tmpFile.delete();
        }
    }

    public void testRecursiveDelete() throws IOException {
        File tmpParentDir = FileUtil.createTempDir("foo");
        File childDir = FileUtil.createTempDir("foochild", tmpParentDir);
        File subFile = FileUtil.createTempFile("foo", ".txt", childDir);
        FileUtil.recursiveDelete(tmpParentDir);
        assertFalse(subFile.exists());
        assertFalse(childDir.exists());
        assertFalse(tmpParentDir.exists());
    }

    public void testExtractZip() throws IOException {
        // TODO: implement this - maybe create a zip programmatically than extract it ?
    }

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
