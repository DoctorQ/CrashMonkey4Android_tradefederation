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

package com.android.tradefed.targetsetup;

import java.util.Collection;

/**
 * Interface for providing required versions of auxiliary image files needed to flash a
 * device.
 * (e.g. bootloader, baseband, etc)
 */
public interface IFlashingResourcesParser {

    /**
     * Gets the required bootloader version specified in the device image zip.
     * @return the bootloader versions or <code>null</code> if not specified
     */
    public String getRequiredBootloaderVersion();

    /**
     * Gets the required baseband version specified in the device image zip.
     * @return the baseband version or <code>null</code> if not specified
     */
    public String getRequiredBasebandVersion();

    /**
     * Gets the required custom image version specified in the device image zip
     *
     * @param versionKey the expected identifier of the image's version information
     * @return the required version for given image or <code>null</code> if not specified
     */
    public String getRequiredImageVersion(String versionKey);

    /**
     * Gets the required board type(s) specified in the device image zip.
     * @return the board types or <code>null</code> if not specified
     */
    public Collection<String> getRequiredBoards();

}
