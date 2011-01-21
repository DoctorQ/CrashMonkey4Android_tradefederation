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
package com.android.tradefed.device;

import junit.framework.TestCase;

/**
 * Unit tests for {@link DeviceSelectionOptions}
 */
public class DeviceSelectionOptionsTest extends TestCase {

    /**
     * Test for {@link DeviceSelectionOptions#getProperties()}.
     */
    public void testGetProperties() {
        DeviceSelectionOptions options = new DeviceSelectionOptions();
        options.addProperty("foo=bar");
        assertEquals("bar", options.getProperties().get("foo"));
    }

    /**
     * Test for {@link DeviceSelectionOptions#getProperties()} with an invalid property.
     */
    public void testGetProperties_invalid() {
        DeviceSelectionOptions options = new DeviceSelectionOptions();
        options.addProperty("invalid");
        assertEquals(0, options.getProperties().size());
    }
}
