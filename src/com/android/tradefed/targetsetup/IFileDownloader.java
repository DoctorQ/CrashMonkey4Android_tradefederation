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
package com.android.tradefed.targetsetup;

import java.io.File;

/**
 * Interface for downloading a remote file.
 */
public interface IFileDownloader {

    /**
     * Downloads a remote file to a temporary file on local disk.
     *
     * @param remoteFilePath the remote path to the file to download, relative to a implementation
     * specific root.
     * @return the temporary local downloaded {@link File}.
     * @throws TargetSetupError if file could not be downloaded
     */
    public File downloadFile(String remoteFilePath) throws TargetSetupError;

    /**
     * Alternate form of {@link downloadFile}, that allows caller to specify the destination
     * file the remote contents  should be placed in.
     *
     * @param remoteFilePath the remote path to the file to download, relative to a implementation
     * specific root.
     * @param destFile the file  to place the downloaded contents into. Should not exist.
     * @throws TargetSetupError if file could not be downloaded
     */
    public void downloadFile(String relativeRemotePath, File destFile) throws TargetSetupError;
}
