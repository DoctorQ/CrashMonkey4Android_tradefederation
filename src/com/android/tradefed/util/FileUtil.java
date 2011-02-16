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

import com.android.ddmlib.Log;
import com.android.tradefed.command.FatalHostError;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * A helper class for file related operations
 */
public class FileUtil {

    private static final String LOG_TAG = "FileUtil";
    /**
     * The minimum allowed disk space in bytes. File creation methods will throw
     * {@link LowDiskSpaceException} if the usable disk space in desired partition is less than
     * this amount.
     */
    private static final long MIN_DISK_SPACE = 100 * 1024 * 1024;

    /**
     * Thrown if usable disk space is below minimum threshold.
     */
    @SuppressWarnings("serial")
    public static class LowDiskSpaceException extends FatalHostError {

        LowDiskSpaceException(String msg, Throwable cause) {
            super(msg, cause);
        }

        LowDiskSpaceException(String msg) {
            super(msg);
        }

    }

    /**
     * Performs a best effort attempt to make given file group readable and writable.
     * <p/>
     * If 'chmod' system command is not supported by underlying OS, will set file to writable by
     * all.
     *
     * @param file the {@link File} to make owner and group writable
     * @returns <code>true</code> if file was successfully made group writable, <code>false</code>
     *          otherwise
     */
    public static boolean setGroupReadWritable(File file) {
        Log.d(LOG_TAG, String.format("Attempting to make %s group writable",
                file.getAbsolutePath()));
        CommandResult result = RunUtil.getInstance().runTimedCmd(10*1000, "chmod", "ug+rw",
                file.getAbsolutePath());
        // TODO: make parent directories writable too ?
        if (result.getStatus().equals(CommandStatus.SUCCESS)) {
            return true;
        } else {
            return file.setWritable(true, false /* false == writable for all */) &&
                file.setReadable(true, false /* false == readable for all */);
        }
    }

    /**
     * Helper function to create a temp directory in the system default temporary file directory.
     *
     * @param prefix The prefix string to be used in generating the file's name; must be at least
     *            three characters long
     * @return the created directory
     * @throws IOException if file could not be created
     */
    public static File createTempDir(String prefix) throws IOException {
        return createTempDir(prefix, null);
    }

    /**
     * Helper function to create a temp directory.
     *
     * @param prefix The prefix string to be used in generating the file's name; must be at least
     *            three characters long
     * @param parentDir The parent directory in which the directory is to be created. If
     *            <code>null</code> the system default temp directory will be used.
     * @return the created directory
     * @throws IOException if file could not be created
     */
    public static File createTempDir(String prefix, File parentDir) throws IOException {
        // create a temp file with unique name, then make it a directory
        File tmpDir = File.createTempFile(prefix, "", parentDir);
        tmpDir.delete();
        if (!tmpDir.mkdirs()) {
            throw new IOException("unable to create directory");
        }
        return tmpDir;
    }

    /**
     * Helper wrapper function around {@link File#createTempFile(String, String)} that audits for
     * potential out of disk space scenario.
     *
     * @see {@link File#createTempFile(String, String)}
     * @throws LowDiskSpaceException if disk space on temporary partition is lower than minimum
     *             allowed
     */
    public static File createTempFile(String prefix, String suffix) throws IOException {
        File returnFile = File.createTempFile(prefix, suffix);
        verifyDiskSpace(returnFile);
        return returnFile;
    }

    /**
     * Helper wrapper function around {@link File#createTempFile(String, String, File parentDir)}
     * that audits for potential out of disk space scenario.
     *
     * @see {@link File#createTempFile(String, String, File)}
     * @throws LowDiskSpaceException if disk space on partition is lower than minimum allowed
     */
    public static File createTempFile(String prefix, String suffix, File parentDir)
            throws IOException {
        File returnFile = File.createTempFile(prefix, suffix, parentDir);
        verifyDiskSpace(returnFile);
        return returnFile;
    }

    /**
     * A helper method that copies a file's contents to a local file
     *
     * @param origFile the original file to be copied
     * @param destFile the destination file
     * @throws IOException if failed to copy file
     */
    public static void copyFile(File origFile, File destFile) throws IOException {
        writeToFile(new FileInputStream(origFile), destFile);
    }

    /**
     * Recursively copy folder contents.
     * <p/>
     * Only supports copying of files and directories - symlinks are not copied.
     *
     * @param sourceDir the folder that contains the files to copy
     * @param destDir the destination folder
     * @throws IOException
     */
    public static void recursiveCopy(File sourceDir, File destDir) throws IOException {
        for (File childFile : sourceDir.listFiles()) {
            File destChild = new File(destDir, childFile.getName());
            if (childFile.isDirectory()) {
                if (!destChild.mkdir()) {
                    throw new IOException(String.format("Could not create directory %s",
                            destChild.getAbsolutePath()));
                }
                recursiveCopy(childFile, destChild);
            } else if (childFile.isFile()) {
                copyFile(childFile, destChild);
            }
        }
    }

    /**
     * A helper method for writing string data to file
     *
     * @param inputString the input {@link String}
     * @param destFile the dest file to write to
     */
    public static void writeToFile(String inputString, File destFile) throws IOException {
        writeToFile(new ByteArrayInputStream(inputString.getBytes()), destFile);
    }

    /**
     * A helper method for writing stream data to file
     *
     * @param input the unbuffered input stream
     * @param destFile the dest file to write to
     */
    public static void writeToFile(InputStream input, File destFile) throws IOException {
        InputStream origStream = null;
        OutputStream destStream = null;
        try {
            origStream = new BufferedInputStream(input);
            destStream = new BufferedOutputStream(new FileOutputStream(destFile));
            int data = -1;
            while ((data = origStream.read()) != -1) {
                destStream.write(data);
            }
        } finally {
            if (origStream != null) {
                try {
                    origStream.close();
                } catch (IOException e) {
                    // ignore
                }
            }
            if (destStream != null) {
                try {
                    destStream.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }

    private static void verifyDiskSpace(File file) {
        // Based on empirical testing File.getUsableSpace is a low cost operation (~ 100 us for
        // local disk, ~ 100 ms for network disk). Therefore call it every time tmp file is
        // created
        if (file.getUsableSpace() < MIN_DISK_SPACE) {
            throw new LowDiskSpaceException(String.format(
                    "Available space on %s is less than %s bytes", file.getAbsolutePath(),
                    MIN_DISK_SPACE));
        }
    }

    /**
     * Recursively delete given directory and all its contents
     */
    public static void recursiveDelete(File rootDir) {
        if (rootDir.isDirectory()) {
            File[] childFiles = rootDir.listFiles();
            if (childFiles != null) {
                for (File child : childFiles) {
                    recursiveDelete(child);
                }
            }
        }
        rootDir.delete();
    }

    /**
     * Utility method to extract entire contents of zip file into given directory
     *
     * @param zipFile the {@link ZipFile} to extract
     * @param destDir the local dir to extract file to
     * @throws IOException if failed to extract file
     */
    public static void extractZip(ZipFile zipFile, File destDir) throws IOException {
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            File childFile = new File(destDir, entry.getName());
            if (entry.isDirectory()) {
                childFile.mkdirs();
            } else {
                FileUtil.writeToFile(zipFile.getInputStream(entry), childFile);
            }
        }
    }

    /**
     * Gets the extension for given file name.
     *
     * @param fileName
     * @return the extension or empty String if file has no extension
     */
    public static String getExtension(String fileName) {
        int index = fileName.lastIndexOf('.');
        if (index == -1) {
            return "";
        } else {
            return fileName.substring(index);
        }
    }

    /**
     * Gets the base name, without extension, of given file name.
     * <p/>
     * e.g. getBaseName("file.txt") will return "file"
     *
     * @param fileName
     * @return the base name
     */
    public static String getBaseName(String fileName) {
        int index = fileName.lastIndexOf('.');
        if (index == -1) {
            return fileName;
        } else {
            return fileName.substring(0, index);
        }
    }

    /**
     * Utility method to do byte-wise content comparison of two files.
     *
     * @return <code>true</code> if file contents are identical
     */
    public static boolean compareFileContents(File file1, File file2) throws IOException {
        BufferedInputStream stream1 = null;
        BufferedInputStream stream2 = null;

        try {
            stream1 = new BufferedInputStream(new FileInputStream(file1));
            stream2 = new BufferedInputStream(new FileInputStream(file2));
            boolean eof = false;
            while (!eof) {
                int byte1 = stream1.read();
                int byte2 = stream2.read();
                if (byte1 != byte2) {
                    return false;
                }
                eof = byte1 == -1;
            }
            return true;
        } finally {
            if (stream1 != null) {
                stream1.close();
            }
            if (stream2 != null) {
                stream2.close();
            }
        }
    }

    /**
     * Helper method which constructs a unique file on temporary disk, whose name corresponds as
     * closely as possible to the file name given by the remote file path
     *
     * @param remoteFilePath the '/' separated remote path to construct the name from
     * @param parentDir the parent directory to create the file in. <code>null</code> to use the
     * default temporary directory
     */
    public static File createTempFileForRemote(String remoteFilePath, File parentDir)
            throws IOException {
        String[] segments = remoteFilePath.split("/");
        // take last segment as base name
        String remoteFileName = segments[segments.length-1];
        String prefix = getBaseName(remoteFileName);
        if (prefix.length() < 3) {
            // prefix must be at least 3 characters long
            prefix = prefix + "XXX";
        }
        String fileExt = getExtension(remoteFileName);

        // create a unique file name. Add a underscore to prefix so file name is more readable
        // e.g. myfile_57588758.img rather than myfile57588758.img
        File tmpFile =  FileUtil.createTempFile(prefix + "_", fileExt, parentDir);
        return tmpFile;
    }


}
