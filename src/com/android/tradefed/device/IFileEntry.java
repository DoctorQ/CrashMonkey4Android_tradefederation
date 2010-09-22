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

import java.util.Collection;

/**
* Interface definition that provides simpler, mockable contract to
* {@link com.android.ddmlib.FileEntry} methods.
* <p/>
* TODO: move this into ddmlib
*/
public interface IFileEntry {

    /**
     * Wrapper for {@link FileListingService.FileEntry#getFullEscapedPath()}.
     */
    public String getFullEscapedPath();

    /**
     * Wrapper for {@link FileListingService.FileEntry#getFullPath()}.
     */
    public String getFullPath();

    /**
     * Wrapper for {@link FileListingService.FileEntry#isDirectory()}.
     */
    public boolean isDirectory();

    /**
     * Finds a child {@link IFileEntry} with given name.
     * <p/>
     * Basically a wrapper for {@link FileListingService.FileEntry#findChild(String)} that
     * will also first search the cached children for file with given name, and if not found,
     * refresh the cached child file list and attempt again.
     *
     * @throws DeviceNotAvailableException
     */
    public IFileEntry findChild(String name) throws DeviceNotAvailableException;

    /**
     * Wrapper for {@link FileListingService.FileEntry#isAppFileName()}.
     */
    public boolean isAppFileName();

    /**
     * Wrapper for {@link FileListingService.FileEntry#getName()}.
     */
    public String getName();

    /**
     * Wrapper for {@link FileListingService.FileEntry#getTime()}.
     */
    public String getTime();

    /**
     * Wrapper for {@link FileListingService.FileEntry#getDate()}.
     */
    public String getDate();

    /**
     * Returns the children of a {@link IFileEntry}.
     * <p/>
     * Basically a synchronous wrapper for
     * {@link FileListingService#getChildren(FileEntry, boolean, FileListingService.IListingReceiver)}
     *
     * @param useCache <code>true</code> if the cached children should be returned if available.
     *            <code>false</code> if a new ls command should be forced.
     * @return list of sub files
     * @throws DeviceNotAvailableException
     */
    public Collection<IFileEntry> getChildren(boolean useCache) throws DeviceNotAvailableException;

    /**
     * Return reference to the ddmlib {@link FileEntry}.
     */
    public FileEntry getFileEntry();

}
