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

import com.android.ddmlib.IDevice;
import com.android.tradefed.device.DeviceSelectionOptions;

import org.easymock.EasyMock;

import junit.framework.TestCase;

/**
 * Unit tests for {@link DeviceSelectionMatcher}
 */
public class DeviceSelectionMatcherTest extends TestCase {
    private IDevice mMockDevice;

    // DEVICE_TYPE and OTHER_DEVICE_TYPE should be different
    private static final String DEVICE_TYPE = "charm";
    private static final String OTHER_DEVICE_TYPE = "strange";
    private static final String DEVICE_SERIAL = "12345";
    /**
     * {@inheritDoc}
     */
    @Override
    protected void setUp() throws Exception {
        mMockDevice = EasyMock.createMock(IDevice.class);
        EasyMock.expect(mMockDevice.getSerialNumber()).andStubReturn(DEVICE_SERIAL);
        EasyMock.expect(mMockDevice.isEmulator()).andStubReturn(Boolean.FALSE);
    }

    public void testGetProductType_mismatchWithEmptyBoard() {
        DeviceSelectionOptions options = new DeviceSelectionOptions();
        options.addProductType(OTHER_DEVICE_TYPE);

        EasyMock.expect(mMockDevice.getProperty("ro.product.board")).andReturn("");
        EasyMock.expect(mMockDevice.getProperty("ro.product.device")).andReturn(DEVICE_TYPE);
        EasyMock.replay(mMockDevice);

        assertFalse(DeviceSelectionMatcher.matches(mMockDevice, options));
    }

    public void testGetProductType_mismatchWithProperBoard() {
        DeviceSelectionOptions options = new DeviceSelectionOptions();
        options.addProductType(OTHER_DEVICE_TYPE);

        EasyMock.expect(mMockDevice.getProperty("ro.product.board")).andReturn(DEVICE_TYPE);
        EasyMock.replay(mMockDevice);

        assertFalse(DeviceSelectionMatcher.matches(mMockDevice, options));
    }

    public void testGetProductType_matchWithEmptyBoard() {
        DeviceSelectionOptions options = new DeviceSelectionOptions();
        options.addProductType(DEVICE_TYPE);

        EasyMock.expect(mMockDevice.getProperty("ro.product.board")).andReturn("");
        EasyMock.expect(mMockDevice.getProperty("ro.product.device")).andReturn(DEVICE_TYPE);
        EasyMock.replay(mMockDevice);

        assertTrue(DeviceSelectionMatcher.matches(mMockDevice, options));
    }

    public void testGetProductType_matchWithProperBoard() {
        DeviceSelectionOptions options = new DeviceSelectionOptions();
        options.addProductType(DEVICE_TYPE);

        EasyMock.expect(mMockDevice.getProperty("ro.product.board")).andReturn(DEVICE_TYPE);
        EasyMock.replay(mMockDevice);

        assertTrue(DeviceSelectionMatcher.matches(mMockDevice, options));
    }

    /**
     * Test matching by property
     */
    public void testMatches_property() {
        DeviceSelectionOptions options = new DeviceSelectionOptions();
        options.addProperty("prop1=propvalue");

        EasyMock.expect(mMockDevice.getProperty("prop1")).andReturn("propvalue");
        EasyMock.replay(mMockDevice);

        assertTrue(DeviceSelectionMatcher.matches(mMockDevice, options));
    }

    /**
     * Test negative case for matching by property
     */
    public void testMatches_propertyNotMatch() {
        DeviceSelectionOptions options = new DeviceSelectionOptions();
        options.addProperty("prop1=propvalue");

        EasyMock.expect(mMockDevice.getProperty("prop1")).andReturn("wrongvalue");
        EasyMock.replay(mMockDevice);
        assertFalse(DeviceSelectionMatcher.matches(mMockDevice, options));
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
        assertTrue(DeviceSelectionMatcher.matches(mMockDevice, options));
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
        assertFalse(DeviceSelectionMatcher.matches(mMockDevice, options));
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
        assertTrue(DeviceSelectionMatcher.matches(emulatorDevice, options));
    }

    /**
     * Test that an emulator is not matched by default
     */
    public void testMatches_emulatorNotDefault() {
        DeviceSelectionOptions options = new DeviceSelectionOptions();
        IDevice emulatorDevice = new StubDevice("emulator", true);
        assertFalse(DeviceSelectionMatcher.matches(emulatorDevice, options));
    }

    /**
     * Test for matching with no device requested flag
     */
    public void testMatches_noDevice() {
        DeviceSelectionOptions options = new DeviceSelectionOptions();
        options.setNullDeviceRequested(true);
        IDevice stubDevice = new NullDevice("no device");
        assertTrue(DeviceSelectionMatcher.matches(stubDevice, options));
    }

    /**
     * Test that a real device is not matched if the 'no device requested' flag is set
     */
    public void testMatches_emulatorNot() {
        DeviceSelectionOptions options = new DeviceSelectionOptions();
        options.setNullDeviceRequested(true);
        EasyMock.replay(mMockDevice);
        assertFalse(DeviceSelectionMatcher.matches(mMockDevice, options));
    }
}

