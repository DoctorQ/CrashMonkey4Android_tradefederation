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

import com.android.ddmlib.IDevice;

import junit.framework.TestCase;

import org.easymock.EasyMock;

/**
 * Unit tests for {@link DeviceSelectionOptions}
 */
public class DeviceSelectionOptionsTest extends TestCase {

    // DEVICE_SERIAL and DEVICE_ENV_SERIAL need to be different.
    private static final String DEVICE_SERIAL = "12345";
    private static final String DEVICE_ENV_SERIAL = "6789";

    private IDevice mMockDevice;

    // DEVICE_TYPE and OTHER_DEVICE_TYPE should be different
    private static final String DEVICE_TYPE = "charm";
    private static final String OTHER_DEVICE_TYPE = "strange";

    /**
     * {@inheritDoc}
     */
    @Override
    protected void setUp() throws Exception {
        mMockDevice = EasyMock.createMock(IDevice.class);
        EasyMock.expect(mMockDevice.getSerialNumber()).andStubReturn(DEVICE_SERIAL);
        EasyMock.expect(mMockDevice.isEmulator()).andStubReturn(Boolean.FALSE);
    }

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

    public void testGetProductType_mismatchWithEmptyBoard() {
        DeviceSelectionOptions options = new DeviceSelectionOptions();
        options.addProductType(OTHER_DEVICE_TYPE);

        EasyMock.expect(mMockDevice.getProperty("ro.hardware")).andReturn("");
        EasyMock.expect(mMockDevice.getProperty("ro.product.board")).andReturn("");
        EasyMock.expect(mMockDevice.getProperty("ro.product.device")).andReturn(DEVICE_TYPE);
        EasyMock.replay(mMockDevice);

        assertFalse(options.matches(mMockDevice));
    }

    public void testGetProductType_mismatchWithProperBoard() {
        DeviceSelectionOptions options = new DeviceSelectionOptions();
        options.addProductType(OTHER_DEVICE_TYPE);

        EasyMock.expect(mMockDevice.getProperty("ro.hardware")).andReturn("");
        EasyMock.expect(mMockDevice.getProperty("ro.product.board")).andReturn(DEVICE_TYPE);
        EasyMock.replay(mMockDevice);

        assertFalse(options.matches(mMockDevice));
    }

    public void testGetProductType_matchWithEmptyBoard() {
        DeviceSelectionOptions options = new DeviceSelectionOptions();
        options.addProductType(DEVICE_TYPE);

        EasyMock.expect(mMockDevice.getProperty("ro.hardware")).andReturn("");
        EasyMock.expect(mMockDevice.getProperty("ro.product.board")).andReturn("");
        EasyMock.expect(mMockDevice.getProperty("ro.product.device")).andReturn(DEVICE_TYPE)
                .times(2);
        EasyMock.replay(mMockDevice);

        assertTrue(options.matches(mMockDevice));
    }

    public void testGetProductType_matchWithProperBoard() {
        DeviceSelectionOptions options = new DeviceSelectionOptions();
        options.addProductType(DEVICE_TYPE);

        EasyMock.expect(mMockDevice.getProperty("ro.hardware")).andReturn("");
        EasyMock.expect(mMockDevice.getProperty("ro.product.board")).andReturn(DEVICE_TYPE);
        EasyMock.expect(mMockDevice.getProperty("ro.product.device")).andReturn(null);
        EasyMock.replay(mMockDevice);

        assertTrue(options.matches(mMockDevice));
    }

    /**
     * Test matching by property
     */
    public void testMatches_property() {
        DeviceSelectionOptions options = new DeviceSelectionOptions();
        options.addProperty("prop1=propvalue");

        EasyMock.expect(mMockDevice.getProperty("prop1")).andReturn("propvalue");
        EasyMock.replay(mMockDevice);

        assertTrue(options.matches(mMockDevice));
    }

    /**
     * Test negative case for matching by property
     */
    public void testMatches_propertyNotMatch() {
        DeviceSelectionOptions options = new DeviceSelectionOptions();
        options.addProperty("prop1=propvalue");

        EasyMock.expect(mMockDevice.getProperty("prop1")).andReturn("wrongvalue");
        EasyMock.replay(mMockDevice);
        assertFalse(options.matches(mMockDevice));
        EasyMock.verify(mMockDevice);
    }

    /**
     * Test for matching by multiple properties
     */
    public void testMatches_multipleProperty() {
        DeviceSelectionOptions options = new DeviceSelectionOptions();
        options.addProperty("prop1=propvalue");
        options.addProperty("prop2=propvalue2");

        EasyMock.expect(mMockDevice.getProperty("prop1")).andReturn("propvalue");
        EasyMock.expect(mMockDevice.getProperty("prop2")).andReturn("propvalue2");
        EasyMock.replay(mMockDevice);
        assertTrue(options.matches(mMockDevice));
        EasyMock.verify(mMockDevice);
    }

    /**
     * Test for matching by multiple properties, when one property does not match
     */
    public void testMatches_notMultipleProperty() {
        DeviceSelectionOptions options = new DeviceSelectionOptions();
        options.addProperty("prop1=propvalue");
        options.addProperty("prop2=propvalue2");

        EasyMock.expect(mMockDevice.getProperty("prop1")).andReturn("propvalue");
        EasyMock.expect(mMockDevice.getProperty("prop2")).andReturn("wrongpropvalue");
        EasyMock.replay(mMockDevice);
        assertFalse(options.matches(mMockDevice));
        // don't verify in this case, because order of property checks is not deterministic
        // EasyMock.verify(mMockDevice);
    }

    /**
     * Test for matching with an emulator
     */
    public void testMatches_emulator() {
        DeviceSelectionOptions options = new DeviceSelectionOptions();
        options.setEmulatorRequested(true);
        IDevice emulatorDevice = new StubDevice("emulator", true);
        assertTrue(options.matches(emulatorDevice));
    }

    /**
     * Test that an emulator is not matched by default
     */
    public void testMatches_emulatorNotDefault() {
        DeviceSelectionOptions options = new DeviceSelectionOptions();
        IDevice emulatorDevice = new StubDevice("emulator", true);
        assertFalse(options.matches(emulatorDevice));
    }

    /**
     * Test for matching with no device requested flag
     */
    public void testMatches_noDevice() {
        DeviceSelectionOptions options = new DeviceSelectionOptions();
        options.setNullDeviceRequested(true);
        IDevice stubDevice = new NullDevice("no device");
        assertTrue(options.matches(stubDevice));
    }

    /**
     * Test that a real device is not matched if the 'no device requested' flag is set
     */
    public void testMatches_emulatorNot() {
        DeviceSelectionOptions options = new DeviceSelectionOptions();
        options.setNullDeviceRequested(true);
        EasyMock.replay(mMockDevice);
        assertFalse(options.matches(mMockDevice));
    }
}
