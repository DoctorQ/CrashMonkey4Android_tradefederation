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

import java.util.HashMap;
import java.util.Map;

/**
 * No-op empty implementation of a {@link IBuildInfo}.
 */
public class StubBuildInfo implements IBuildInfo {

    /**
     * {@inheritDoc}
     */
    public int getBuildId() {
        return 0;
    }

    public String getTestTarget() {
        return "stub";
    }

    /**
     * {@inheritDoc}
     */
    public Map<String, String> getBuildAttributes() {
        return new HashMap<String, String>();
    }

    /**
     * {@inheritDoc}
     */
    public String getBuildName() {
        return "stub";
    }
}
