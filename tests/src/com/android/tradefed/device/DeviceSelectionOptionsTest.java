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

    // DEVICE_SERIAL and DEVICE_ENV_SERIAL need to be different.
    private static final String DEVICE_SERIAL = "12345";
    private static final String DEVICE_ENV_SERIAL = "6789";
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

    /**
     * Test for {@link DeviceSelectionOptions#getSerials()}
     */
    public void testGetSerials() {
        DeviceSelectionOptions options = getDeviceSelectionOptionsWithEnvVar(DEVICE_ENV_SERIAL);
        // If no serial is available, the environment variable will be used instead.
        assertEquals(1, options.getSerials().size());
        assertTrue(options.getSerials().contains(DEVICE_ENV_SERIAL));
        assertFalse(options.getSerials().contains(DEVICE_SERIAL));
    }

    /**
     * Test that {@link DeviceSelectionOptions#getSerials()} does not override the values.
     */
    public void testGetSerialsDoesNotOverride() {
        DeviceSelectionOptions options = getDeviceSelectionOptionsWithEnvVar(DEVICE_ENV_SERIAL);
        options.addSerial(DEVICE_SERIAL);

        // Check that now we do not override the serial with the environment variable.
        assertEquals(1, options.getSerials().size());
        assertFalse(options.getSerials().contains(DEVICE_ENV_SERIAL));
        assertTrue(options.getSerials().contains(DEVICE_SERIAL));
    }

    /**
     * Test for {@link DeviceSelectionOptions#getSerials()} without the environment variable set.
     */
    public void testGetSerialsWithNoEnvValue() {
        DeviceSelectionOptions options = getDeviceSelectionOptionsWithEnvVar(null);
        // An empty list will cause it to fetch the
        assertTrue(options.getSerials().isEmpty());
        // If no serial is available and the environment variable is not set, nothing happens.
        assertEquals(0, options.getSerials().size());

        options.addSerial(DEVICE_SERIAL);
        // Check that now we do not override the serial.
        assertEquals(1, options.getSerials().size());
        assertFalse(options.getSerials().contains(DEVICE_ENV_SERIAL));
        assertTrue(options.getSerials().contains(DEVICE_SERIAL));
    }

    /**
     * Helper method to return an anonymous subclass of DeviceSelectionOptions with a given
     * environmental variable.
     *
     * @param value {@link String} of the environment variable ANDROID_SERIAL
     * @return {@link DeviceSelectionOptions} subclass with a given environmental variable.
     */
    private DeviceSelectionOptions getDeviceSelectionOptionsWithEnvVar(final String value) {
        return new DeviceSelectionOptions() {
            // We don't have the environment variable set, return null.
            @Override
            String fetchEnvironmentVariable(String name) {
                return value;
            }
        };
    }
}
