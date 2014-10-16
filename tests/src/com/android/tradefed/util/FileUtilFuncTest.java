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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipFile;

/**
 * Functional tests for {@link FileUtil}
 */
public class FileUtilFuncTest extends TestCase {
    private static final String PERMS_NONE = "---------";
    private static final String PERMS_GRWX = "rwxrwx---";
    private static final String DPERMS_NONE = "d" + PERMS_NONE;
    private static final String DPERMS_GRWX = "d" + PERMS_GRWX;

    private Set<File> mTempFiles = new HashSet<File>();

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        for (File file : mTempFiles) {
            if (file != null && file.exists()) {
                if (file.isDirectory()) {
                    FileUtil.recursiveDelete(file);
                } else {
                    file.delete();
                }
            }
        }
    }

    /**
     * Make sure that {@link FileUtil#mkdirsRWX} works when there are multiple levels of directories
     */
    public void testMkdirsRWX_multiLevel() throws IOException {
        final int subdirCount = 5;
        File tmpParentDir = createTempDir("foo");
        // create a hierarchy of directories to be created
        File[] subdirs = new File[subdirCount];
        subdirs[0] = new File(tmpParentDir, "patient0");
        for (int i = 1; i < subdirCount; i++) {
            subdirs[i] = new File(subdirs[i - 1], String.format("subdir%d", i));
        }
        assertFalse(subdirs[0].exists());
        FileUtil.mkdirsRWX(subdirs[subdirs.length - 1]);

        for (int i = 0; i < subdirCount; i++) {
            assertTrue(subdirs[i].exists());
            assertUnixPerms(subdirs[i], DPERMS_GRWX);
        }
    }

    /**
     * Make sure that {@link FileUtil#mkdirsRWX} works in the basic case
     */
    public void testMkdirsRWX_singleLevel() throws IOException {
        File tmpParentDir = createTempDir("foo");
        File subdir = new File(tmpParentDir, "subdirectory");
        assertFalse(subdir.exists());
        FileUtil.mkdirsRWX(subdir);
        assertTrue(subdir.exists());
        assertUnixPerms(subdir, DPERMS_GRWX);
    }

    /**
     * Make sure that {@link FileUtil#mkdirsRWX} works when the directory to be touched already
     * exists
     */
    public void testMkdirsRWX_preExisting() throws IOException {
        File tmpParentDir = createTempDir("foo");
        File subdir = new File(tmpParentDir, "subdirectory");
        subdir.mkdir();
        subdir.setExecutable(false, false);
        subdir.setReadable(false, false);
        subdir.setWritable(false, false);

        assertUnixPerms(subdir, DPERMS_NONE);
        FileUtil.mkdirsRWX(subdir);
        assertTrue(subdir.exists());
        assertUnixPerms(subdir, DPERMS_GRWX);
    }

    /**
     * Simple test for {@link FileUtil#chmodGroupRW(File)}.
     */
    public void testChmodGroupRW() throws IOException {
        File tmpFile = createTempFile("foo", "txt");
        tmpFile.setReadable(false);
        tmpFile.setWritable(false);
        FileUtil.chmodGroupRW(tmpFile);
        assertTrue(tmpFile.canRead());
        assertTrue(tmpFile.canWrite());
    }

    /**
     * Simple test for {@link FileUtil#createTempDir(String)}.
     */
    public void testCreateTempDir() throws IOException {
        File tmpDir = createTempDir("foo");
        assertTrue(tmpDir.exists());
        assertTrue(tmpDir.isDirectory());
    }

    /**
     * Simple test for {@link FileUtil#createTempDir(String, File)}.
     */
    public void testCreateTempDir_parentFile() throws IOException {
        File tmpParentDir = createTempDir("foo");
        File childDir = createTempDir("foochild", tmpParentDir);
        assertTrue(childDir.exists());
        assertTrue(childDir.isDirectory());
        assertEquals(tmpParentDir.getAbsolutePath(), childDir.getParent());
    }

    /**
     * Simple test for {@link FileUtil#createTempFile(String, String)}.
     */
    public void testCreateTempFile() throws IOException {
        File tmpFile = createTempFile("foo", ".txt");
        assertTrue(tmpFile.exists());
        assertTrue(tmpFile.isFile());
        assertTrue(tmpFile.getName().startsWith("foo"));
        assertTrue(tmpFile.getName().endsWith(".txt"));
    }

    /**
     * Simple test for {@link FileUtil#createTempFile(String, String, File)}.
     */
    public void testCreateTempFile_parentDir() throws IOException {
        File tmpParentDir = createTempDir("foo");

        File tmpFile = createTempFile("foo", ".txt", tmpParentDir);
        assertTrue(tmpFile.exists());
        assertTrue(tmpFile.isFile());
        assertTrue(tmpFile.getName().startsWith("foo"));
        assertTrue(tmpFile.getName().endsWith(".txt"));
        assertEquals(tmpParentDir.getAbsolutePath(), tmpFile.getParent());
    }

    /**
     * Simple test method for {@link FileUtil#writeToFile(InputStream, File)}.
     */
    public void testWriteToFile() throws IOException {
        final String testContents = "this is the temp file test data";
        InputStream input = new ByteArrayInputStream(testContents.getBytes());
        File tmpFile = createTempFile("foo", ".txt");
        FileUtil.writeToFile(input, tmpFile);
        String readContents = StreamUtil.getStringFromStream(new FileInputStream(tmpFile));
        assertEquals(testContents, readContents);
    }

    public void testRecursiveDelete() throws IOException {
        File tmpParentDir = createTempDir("foo");
        File childDir = createTempDir("foochild", tmpParentDir);
        File subFile = createTempFile("foo", ".txt", childDir);
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
        File tmpParentDir = createTempDir("foo");
        File zipFile = null;
        File extractedDir = createTempDir("extract-foo");
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
            if (zipFile != null) {
                zipFile.delete();
            }
        }
    }

    /**
     * Test creating then extracting a a single file from zip file
     *
     * @throws IOException
     */
    public void testCreateAndExtractFileFromZip() throws IOException {
        File tmpParentDir = createTempDir("foo");
        File zipFile = null;
        File extractedSubFile = null;
        try {
            File childDir = new File(tmpParentDir, "foochild");
            assertTrue(childDir.mkdir());
            File subFile = new File(childDir, "foo.txt");
            FileUtil.writeToFile("contents", subFile);
            zipFile = FileUtil.createZip(tmpParentDir);

            extractedSubFile = FileUtil.extractFileFromZip(new ZipFile(zipFile),
                    tmpParentDir.getName() + "/foochild/foo.txt");
            assertNotNull(extractedSubFile);
            assertTrue(FileUtil.compareFileContents(subFile, extractedSubFile));
        } finally {
            FileUtil.deleteFile(zipFile);
            FileUtil.deleteFile(extractedSubFile);
        }
    }

    public void testRecursiveCopy() throws IOException {
        File tmpParentDir = createTempDir("foo");
        File childDir = createTempDir("foochild", tmpParentDir);
        File subFile = createTempFile("foo", ".txt", childDir);
        FileUtil.writeToFile("foo", subFile);
        File destDir = createTempDir("dest");
        FileUtil.recursiveCopy(tmpParentDir, destDir);
        File subFileCopy = new File(destDir, String.format("%s%s%s", childDir.getName(),
                    File.separator, subFile.getName()));
        assertTrue(subFileCopy.exists());
        assertTrue(FileUtil.compareFileContents(subFile, subFileCopy));
    }

    public void testFindDirsUnder() throws IOException {
        File absRootDir = createTempDir("rootDir");
        File relRootDir = new File(absRootDir.getName());
        File absSubDir1 = createTempDir("subdir1", absRootDir);
        File relSubDir1 = new File(relRootDir.getName(), absSubDir1.getName());
        File absSubDir2 = createTempDir("subdir2", absRootDir);
        File relSubDir2 = new File(relRootDir.getName(), absSubDir2.getName());
        File aFile = createTempFile("aFile", ".txt", absSubDir2);

        HashSet<File> expected = new HashSet<File>();
        Collections.addAll(expected, relRootDir, relSubDir1, relSubDir2);
        assertEquals(expected, FileUtil.findDirsUnder(absRootDir, null));
        expected.clear();
        File fakeRoot = new File("fakeRoot");
        Collections.addAll(expected,
                    new File(fakeRoot, relRootDir.getPath()),
                    new File(fakeRoot, relSubDir1.getPath()),
                    new File(fakeRoot, relSubDir2.getPath()));
        assertEquals("Failed to apply a new relative parent", expected,
                    FileUtil.findDirsUnder(absRootDir, fakeRoot));
        assertEquals("found something when passing null as a root dir", 0,
                    FileUtil.findDirsUnder(null, null).size());
        try {
            FileUtil.findDirsUnder(aFile, null);
            fail("should have thrown an excpetion when passing in something that's not a dir");
        } catch (IllegalArgumentException e) {
            assertTrue(true);
        }
    }

    /**
     * Test method for {@link FileUtil#createTempFileForRemote(String, File)}.
     */
    public void testCreateTempFileForRemote() throws IOException {
        String remoteFilePath = "path/userdata.img";
        File tmpFile = FileUtil.createTempFileForRemote(remoteFilePath, null);
        try {
            assertTrue(tmpFile.getAbsolutePath().contains("userdata"));
            assertTrue(tmpFile.getAbsolutePath().endsWith(".img"));
        } finally {
            FileUtil.deleteFile(tmpFile);
        }
    }

    /**
     * Test method for {@link FileUtil#createTempFileForRemote(String, File)} for a nested path.
     */
    public void testCreateTempFileForRemote_nested() throws IOException {
        String remoteFilePath = "path/2path/userdata.img";
        File tmpFile = FileUtil.createTempFileForRemote(remoteFilePath, null);
        try {
            assertTrue(tmpFile.getAbsolutePath().contains("userdata"));
            assertTrue(tmpFile.getAbsolutePath().endsWith(".img"));
        } finally {
            FileUtil.deleteFile(tmpFile);
        }
    }

    /**
     * Test {@link FileUtil#createTempFileForRemote(String, File)} for file with no extension
     */
    public void testCreateTempFileForRemote_noext() throws IOException {
        String remoteFilePath = "path/2path/userddddmg";
        File tmpFile = FileUtil.createTempFileForRemote(remoteFilePath, null);
        try {
            assertTrue(tmpFile.getAbsolutePath().contains("userddddmg"));
        } finally {
            FileUtil.deleteFile(tmpFile);
        }
    }

    /**
     * Test {@link FileUtil#createTempFileForRemote(String, File)} for a too small prefix.
     */
    public void testCreateTempFileForRemote_short() throws IOException {
        String remoteFilePath = "path/2path/us.img";
        File tmpFile = FileUtil.createTempFileForRemote(remoteFilePath, null);
        try {
            assertTrue(tmpFile.getAbsolutePath().contains("usXXX"));
            assertTrue(tmpFile.getAbsolutePath().endsWith(".img"));
        } finally {
            FileUtil.deleteFile(tmpFile);
        }
    }

    /**
     * Test {@link FileUtil#createTempFileForRemote(String, File)} for remoteFile in root path.
     */
    public void testCreateTempFileForRemote_singleFile() throws IOException {
        String remoteFilePath = "userdata.img";
        File tmpFile = FileUtil.createTempFileForRemote(remoteFilePath, null);
        try {
            assertTrue(tmpFile.getAbsolutePath().contains("userdata"));
            assertTrue(tmpFile.getAbsolutePath().endsWith(".img"));
        } finally {
            FileUtil.deleteFile(tmpFile);
        }
    }


    // Assertions
    private String assertUnixPerms(File file, String expPerms) {
        String perms = ls(file.getPath());
        assertTrue(String.format("Expected file %s perms to be '%s' but they were '%s'.", file,
                expPerms, perms), perms.startsWith(expPerms));
        return perms;
    }

    // Helpers
    private String ls(String path) {
        CommandResult result = RunUtil.getDefault().runTimedCmd(10 * 1000, "ls", "-ld", path);
        return result.getStdout();
    }

    private File createTempDir(String prefix) throws IOException {
        return createTempDir(prefix, null);
    }

    private File createTempDir(String prefix, File parentDir) throws IOException {
        File tempDir = FileUtil.createTempDir(prefix, parentDir);
        mTempFiles.add(tempDir);
        return tempDir;
    }

    private File createTempFile(String prefix, String suffix) throws IOException {
        File tempFile = FileUtil.createTempFile(prefix, suffix);
        mTempFiles.add(tempFile);
        return tempFile;
    }

    private File createTempFile(String prefix, String suffix, File parentDir) throws IOException {
        File tempFile = FileUtil.createTempFile(prefix, suffix, parentDir);
        mTempFiles.add(tempFile);
        return tempFile;
    }
}
