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
import java.util.zip.ZipFile;

import junit.framework.TestCase;

/**
 * Functional tests for {@link FileUtil}
 */
public class FileUtilFuncTest extends TestCase {
    private static final String PERMS_NONE = "---------";
    private static final String PERMS_GRWX = "rwxrwx---";
    private static final String DPERMS_NONE = "d" + PERMS_NONE;
    private static final String DPERMS_GRWX = "d" + PERMS_GRWX;

    private String ls(String path) {
        CommandResult result = RunUtil.getDefault().runTimedCmd(10*1000, "ls", "-ld", path);
        return result.getStdout();
    }

    private String assertUnixPerms(File file, String expPerms) {
        String perms = ls(file.getPath());
        assertTrue(String.format("Expected file %s perms to be '%s' but they were '%s'.", file,
                expPerms, perms), perms.startsWith(expPerms));
        return perms;
    }

    /**
     * Make sure that {@link FileUtil#mkdirsRWX} works when there are multiple levels of directories
     */
    public void testMkdirsRWX_multiLevel() throws IOException {
        final int subdirCount = 5;
        File tmpParentDir = FileUtil.createTempDir("foo");
        try {
            // create a hierarchy of directories to be created
            File[] subdirs = new File[subdirCount];
            subdirs[0] = new File(tmpParentDir, "patient0");
            for (int i = 1; i < subdirCount; i++) {
                subdirs[i] = new File(subdirs[i-1], String.format("subdir%d", i));
            }
            assertFalse(subdirs[0].exists());
            FileUtil.mkdirsRWX(subdirs[subdirs.length - 1]);

            for (int i = 0; i < subdirCount; i++) {
                assertTrue(subdirs[i].exists());
                assertUnixPerms(subdirs[i], DPERMS_GRWX);
            }
        } finally {
            FileUtil.recursiveDelete(tmpParentDir);
        }
    }

    /**
     * Make sure that {@link FileUtil#mkdirsRWX} works in the basic case
     */
    public void testMkdirsRWX_singleLevel() throws IOException {
        File tmpParentDir = FileUtil.createTempDir("foo");
        try {
            File subdir = new File(tmpParentDir, "subdirectory");
            assertFalse(subdir.exists());
            FileUtil.mkdirsRWX(subdir);
            assertTrue(subdir.exists());
            assertUnixPerms(subdir, DPERMS_GRWX);
        } finally {
            FileUtil.recursiveDelete(tmpParentDir);
        }
    }

    /**
     * Make sure that {@link FileUtil#mkdirsRWX} works when the directory to be touched already
     * exists
     */
    public void testMkdirsRWX_preExisting() throws IOException {
        File tmpParentDir = FileUtil.createTempDir("foo");
        try {
            File subdir = new File(tmpParentDir, "subdirectory");
            subdir.mkdir();
            subdir.setExecutable(false, false);
            subdir.setReadable(false, false);
            subdir.setWritable(false, false);

            assertUnixPerms(subdir, DPERMS_NONE);
            FileUtil.mkdirsRWX(subdir);
            assertTrue(subdir.exists());
            assertUnixPerms(subdir, DPERMS_GRWX);
        } finally {
            FileUtil.recursiveDelete(tmpParentDir);
        }
    }

    /**
     * Simple test for {@link FileUtil#chmodGroupRW(File)}.
     */
    public void testChmodGroupRW() throws IOException {
        File tmpFile = FileUtil.createTempFile("foo", "txt");
        try {
            tmpFile.setReadable(false);
            tmpFile.setWritable(false);
            FileUtil.chmodGroupRW(tmpFile);
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

    /**
     * Test creating then extracting a zip file
     *
     * @throws IOException
     */
    public void testCreateAndExtractZip() throws IOException {
        File tmpParentDir = FileUtil.createTempDir("foo");
        File zipFile = null;
        File extractedDir = FileUtil.createTempDir("extract-foo");
        try {
            File childDir = new File(tmpParentDir, "foochild");
            assertTrue(childDir.mkdir());
            File subFile = new File(childDir, "foo.txt");
            FileUtil.writeToFile("contents", subFile);
            zipFile = FileUtil.createZip(tmpParentDir);
            FileUtil.extractZip(new ZipFile(zipFile), extractedDir);

            // assert all contents of original zipped dir are extracted
            File extractedParentDir = new File(extractedDir, tmpParentDir.getName());
            File extractedChildDir = new File(extractedParentDir, childDir.getName());
            File extractedSubFile = new File(extractedChildDir, subFile.getName());
            assertTrue(extractedParentDir.exists());
            assertTrue(extractedChildDir.exists());
            assertTrue(extractedSubFile.exists());
            assertTrue(FileUtil.compareFileContents(subFile, extractedSubFile));
        } finally {
            FileUtil.recursiveDelete(tmpParentDir);
            FileUtil.recursiveDelete(extractedDir);
            if (zipFile != null) {
                zipFile.delete();
            }
        }
    }

   public void testRecursiveCopy() throws IOException {
        File tmpParentDir = FileUtil.createTempDir("foo");
        File childDir = FileUtil.createTempDir("foochild", tmpParentDir);
        File subFile = FileUtil.createTempFile("foo", ".txt", childDir);
        FileUtil.writeToFile("foo", subFile);
        File destDir = FileUtil.createTempDir("dest");
        try {
            FileUtil.recursiveCopy(tmpParentDir, destDir);
            File subFileCopy = new File(destDir, String.format("%s%s%s", childDir.getName(),
                    File.separator, subFile.getName()));
            assertTrue(subFileCopy.exists());
            assertTrue(FileUtil.compareFileContents(subFile, subFileCopy));
        } finally {
            FileUtil.recursiveDelete(tmpParentDir);
            FileUtil.recursiveDelete(destDir);
        }
    }
}
