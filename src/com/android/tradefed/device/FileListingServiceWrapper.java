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
package com.android.tradefed.device;

import com.android.ddmlib.FileListingService;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.FileListingService.FileEntry;
import com.android.ddmlib.FileListingService.IListingReceiver;

import java.util.Vector;

/**
 * A wrapper that directs {@link IFileListingService} calls to the 'real'
 * {@link FileListingService}.
 */
public class FileListingServiceWrapper implements IFileListingService {

    private final FileListingService mService;

    /**
     * A wrapper that directs {@link IFileListingService.IFileEntry} calls to the 'real'
     * {@link FileListingService.FileEntry}.
     */
    public static class FileEntryWrapper implements IFileEntry {
        private FileListingService.FileEntry mFileEntry = null;

        /**
         * Constructor.
         */
        public FileEntryWrapper(FileListingService.FileEntry fe) {
            if (fe == null) {
                throw new IllegalArgumentException("Null FileListingService.FileEntry passed in.");
            }
            mFileEntry = fe;
        }

        /**
         * {@inheritDoc}
         */
        public String getFullEscapedPath() {
            return mFileEntry.getFullEscapedPath();
        }

        /**
         * {@inheritDoc}
         */
        public IFileEntry findChild(String name) {
            return getIFileEntry(mFileEntry.findChild(name));
        }

        /**
         * Convenience method to get an IFileEntry, or null if the FileEntry was null
         */
        static IFileEntry getIFileEntry(FileListingService.FileEntry fe) {
            if (fe == null) {
                return null;
            }
            return new FileEntryWrapper(fe);
        }

        /**
         * {@inheritDoc}
         */
        public FileListingService.FileEntry getFileEntry() {
            return mFileEntry;
        }

        /**
         * {@inheritDoc}
         */
        public boolean isDirectory() {
            return mFileEntry.isDirectory();
        }

        /**
         * {@inheritDoc}
         */
        public boolean isAppFileName() {
            return mFileEntry.isAppFileName();
        }

        /**
         * {@inheritDoc}
         */
        public String getName() {
            return mFileEntry.getName();
        }
    }

    /**
     * Wrapper for {@link FileListingService.FileEntry#getFullEscapedPath()}.
     */
    private FileListingServiceWrapper(IDevice device) {
        if (device == null) {
            throw new IllegalArgumentException("Null device passed in.");
        }
        mService = device.getFileListingService();
    }

    /**
     * Gets an IFileListingService for the given device.
     */
    public static IFileListingService getFileListingServiceForDevice(IDevice device) {
        if (device == null) {
            return null;
        }
        return new FileListingServiceWrapper(device);
    }

    /**
     * {@inheritDoc}
     */
    public IFileEntry getRoot() {
        return FileEntryWrapper.getIFileEntry(mService.getRoot());
    }

    /**
     * {@inheritDoc}
     */
    public IFileEntry[] getChildren(IFileEntry fileEntry, boolean useCache,
            final IListingReceiver receiver) {
        Vector<IFileEntry> entriesVector = new Vector<IFileEntry>();
        FileEntry[] entries = mService.getChildren(fileEntry.getFileEntry(),
                useCache, receiver);
        for (FileEntry fe : entries) {
            entriesVector.add(FileEntryWrapper.getIFileEntry(fe));
        }
        return entriesVector.toArray(new IFileEntry[entriesVector.size()]);
    }

}
