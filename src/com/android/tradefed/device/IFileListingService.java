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
import com.android.ddmlib.FileListingService.FileEntry;
import com.android.ddmlib.FileListingService.IListingReceiver;


/**
 * Interface definition for {@link com.android.ddmlib.FileListingService} methods used in this
 * package.
 * <p/>
 * Exposed so use of {@link com.android.ddmlib.FileListingService} can be mocked out in unit tests.
 */
public interface IFileListingService {

    public interface IFileEntry {
        /**
         * Wrapper for {@link FileListingService.FileEntry#getFullEscapedPath()}.
         */
        public String getFullEscapedPath();

        /**
         * Wrapper for {@link FileListingService.FileEntry#isDirectory()}.
         */
        public boolean isDirectory();

        /**
         * Wrapper for {@link FileListingService.FileEntry#findChild(String)}.
         */
        public IFileEntry findChild(String name);

        /**
         * Helper method to get the underlying FileListingService.FileEntry object this object wraps
         */
        public FileListingService.FileEntry getFileEntry();

        /**
         * Wrapper for {@link FileListingService.FileEntry#isAppFileName()}.
         */
        public boolean isAppFileName();

        /**
         * Wrapper for {@link FileListingService.FileEntry#getName()}.
         */
        public String getName();
    }

    /**
     * Wrapper for {@link FileListingService#getRoot()}.
     */
    public IFileEntry getRoot();

    /**
     * Wrapper for {@link FileListingService#getChildren(FileEntry, boolean, IListingReceiver)}.
     */
    public IFileEntry[] getChildren(IFileEntry fileEntry, boolean useCache,
            final IListingReceiver receiver);

}
