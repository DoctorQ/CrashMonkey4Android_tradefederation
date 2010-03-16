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

import com.android.ddmlib.Client;
import com.android.ddmlib.FileListingService;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.IShellOutputReceiver;
import com.android.ddmlib.MultiLineReceiver;
import com.android.ddmlib.RawImage;
import com.android.ddmlib.SyncService;
import com.android.ddmlib.log.LogReceiver;
import com.android.ddmlib.testrunner.IRemoteAndroidTestRunner;
import com.android.ddmlib.testrunner.ITestRunListener;

import org.easymock.EasyMock;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import junit.framework.TestCase;

/**
 * Unit tests for {@link TestDevice}.
 */
public class TestDeviceTest extends TestCase {

    private IDevice mMockIDevice;
    private IShellOutputReceiver mMockReceiver;
    private TestDevice mTestDevice;
    private IDeviceRecovery mMockRecovery;

    /**
     * {@inheritDoc}
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mMockIDevice = EasyMock.createMock(IDevice.class);
        mMockReceiver = EasyMock.createMock(IShellOutputReceiver.class);
        mMockRecovery = EasyMock.createMock(IDeviceRecovery.class);
        mTestDevice = new TestDevice(mMockIDevice, mMockRecovery);
    }

    /**
     * Simple normal case test for
     * {@link TestDevice#executeShellCommand(String, IShellOutputReceiver)}.
     * <p/>
     * Verify that the shell command is routed to the IDevice.
     */
    public void testExecuteShellCommand_receiver() throws IOException, DeviceNotAvailableException {
        final String testCommand = "simple command";
        // expect shell command to be called
        mMockIDevice.executeShellCommand(testCommand, mMockReceiver);
        EasyMock.replay(mMockIDevice);
        mTestDevice.executeShellCommand(testCommand, mMockReceiver);
    }

    /**
     * Simple normal case test for
     * {@link TestDevice#executeShellCommand(String)}.
     * <p/>
     * Verify that the shell command is routed to the IDevice, and shell output is collected.
     */
    public void testExecuteShellCommand() throws IOException, DeviceNotAvailableException {
        final String testCommand = "simple command";
        final String expectedOutput = "this is the output\r\n in two lines\r\n";

        // expect shell command to be called, with any receiver
        mMockIDevice.executeShellCommand(EasyMock.eq(testCommand), (IShellOutputReceiver)
                EasyMock.anyObject());
        EasyMock.expectLastCall().andDelegateTo(
              new MockDevice() {
                  @Override
                  public void executeShellCommand(String cmd, IShellOutputReceiver receiver) {
                      byte[] inputData = expectedOutput.getBytes();
                      receiver.addOutput(inputData, 0, inputData.length);
                  }
              });
        EasyMock.replay(mMockIDevice);
        assertEquals(expectedOutput, mTestDevice.executeShellCommand(testCommand));
    }

    /**
     * Test {@link TestDevice#executeShellCommand(String, IShellOutputReceiver)} behavior when
     * {@link IDevice} throws IOException and recovery immediately fails.
     * <p/>
     * Verify that a DeviceNotAvailableException is thrown.
     */
    public void testExecuteShellCommand_recoveryFail() throws Exception {
        final String testCommand = "simple command";
        // expect shell command to be called
        mMockIDevice.executeShellCommand(testCommand, mMockReceiver);
        EasyMock.expectLastCall().andThrow(new IOException());
        EasyMock.expect(mMockIDevice.getSerialNumber()).andReturn("foo").anyTimes();
        mMockRecovery.recoverDevice(mTestDevice);
        EasyMock.expectLastCall().andThrow(new DeviceNotAvailableException());
        EasyMock.replay(mMockIDevice);
        EasyMock.replay(mMockRecovery);
        try {
            mTestDevice.executeShellCommand(testCommand, mMockReceiver);
            fail("DeviceNotAvailableException not thrown");
        } catch (DeviceNotAvailableException e) {
            // expected
        }
    }

    /**
     * Test {@link TestDevice#executeShellCommand(String, IShellOutputReceiver)} behavior when
     * {@link IDevice} throws IOException and recovery succeeds.
     * <p/>
     * Verify that command is re-tried.
     */
    public void testExecuteShellCommand_recoveryRetry() throws Exception {
        final String testCommand = "simple command";
        // expect shell command to be called
        mMockIDevice.executeShellCommand(testCommand, mMockReceiver);
        EasyMock.expectLastCall().andThrow(new IOException());
        EasyMock.expect(mMockIDevice.getSerialNumber()).andReturn("foo").anyTimes();
        mMockRecovery.recoverDevice(mTestDevice);
        EasyMock.expectLastCall();
        mMockIDevice.executeShellCommand(testCommand, mMockReceiver);
        EasyMock.expectLastCall();
        EasyMock.replay(mMockIDevice);
        EasyMock.replay(mMockRecovery);
        mTestDevice.executeShellCommand(testCommand, mMockReceiver);
    }

    /**
     * Test {@link TestDevice#executeShellCommand(String, IShellOutputReceiver)} behavior when
     * {@link IDevice} repeatedly throws IOException and recovery succeeds.
     * <p/>
     * Verify that DeviceNotAvailableException is thrown.
     */
    public void testExecuteShellCommand_recoveryAttempts() throws Exception {
        final String testCommand = "simple command";
        // expect shell command to be called
        mMockIDevice.executeShellCommand(testCommand, mMockReceiver);
        EasyMock.expectLastCall().andThrow(new IOException()).anyTimes();
        EasyMock.expect(mMockIDevice.getSerialNumber()).andReturn("foo").anyTimes();
        mMockRecovery.recoverDevice(mTestDevice);
        EasyMock.expectLastCall().anyTimes();
        EasyMock.replay(mMockIDevice);
        EasyMock.replay(mMockRecovery);
        try {
            mTestDevice.executeShellCommand(testCommand, mMockReceiver);
            fail("DeviceNotAvailableException not thrown");
        } catch (DeviceNotAvailableException e) {
            // expected
        }
    }

    /**
     * Test {@link TestDevice#runInstrumentationTests(IRemoteAndroidTestRunner, Collection)}
     * success case.
     */
    public void testRunInstrumentationTests() throws Exception {
        IRemoteAndroidTestRunner mockRunner = EasyMock.createMock(IRemoteAndroidTestRunner.class);
        Collection<ITestRunListener> listeners = new ArrayList<ITestRunListener>(0);
        // expect runner.run command to be called
        mockRunner.run(listeners);
        EasyMock.replay(mockRunner);
        mTestDevice.runInstrumentationTests(mockRunner, listeners);
    }

    /**
     * Test {@link TestDevice#runInstrumentationTests(IRemoteAndroidTestRunner, Collection)}
     * when recovery fails.
     */
    public void testRunInstrumentationTests_recoveryFails() throws Exception {
        IRemoteAndroidTestRunner mockRunner = EasyMock.createMock(IRemoteAndroidTestRunner.class);
        Collection<ITestRunListener> listeners = new ArrayList<ITestRunListener>(1);
        ITestRunListener listener = EasyMock.createMock(ITestRunListener.class);
        listeners.add(listener);
        mockRunner.run(listeners);
        EasyMock.expectLastCall().andThrow(new IOException());
        EasyMock.expect(mockRunner.getPackageName()).andReturn("foo");
        EasyMock.expect(mMockIDevice.getSerialNumber()).andReturn("foo").anyTimes();
        listener.testRunFailed((String)EasyMock.anyObject());
        mMockRecovery.recoverDevice(mTestDevice);
        EasyMock.expectLastCall().andThrow(new DeviceNotAvailableException());
        EasyMock.replay(listener);
        EasyMock.replay(mockRunner);
        EasyMock.replay(mMockIDevice);
        EasyMock.replay(mMockRecovery);
        try {
            mTestDevice.runInstrumentationTests(mockRunner, listeners);
            fail("DeviceNotAvailableException not thrown");
        } catch (DeviceNotAvailableException e) {
            // expected
        }
    }

    /**
     * Test {@link TestDevice#runInstrumentationTests(IRemoteAndroidTestRunner, Collection)}
     * when recovery succeeds.
     */
    public void testRunInstrumentationTests_recoverySucceeds() throws Exception {
        IRemoteAndroidTestRunner mockRunner = EasyMock.createMock(IRemoteAndroidTestRunner.class);
        Collection<ITestRunListener> listeners = new ArrayList<ITestRunListener>(1);
        ITestRunListener listener = EasyMock.createMock(ITestRunListener.class);
        listeners.add(listener);
        mockRunner.run(listeners);
        EasyMock.expectLastCall().andThrow(new IOException());
        EasyMock.expect(mockRunner.getPackageName()).andReturn("foo");
        EasyMock.expect(mMockIDevice.getSerialNumber()).andReturn("foo").anyTimes();
        listener.testRunFailed((String)EasyMock.anyObject());
        mMockRecovery.recoverDevice(mTestDevice);
        EasyMock.replay(listener);
        EasyMock.replay(mockRunner);
        EasyMock.replay(mMockIDevice);
        EasyMock.replay(mMockRecovery);
        mTestDevice.runInstrumentationTests(mockRunner, listeners);
    }

    /**
     * Concrete mock implementation of {@link IDevice}.
     * <p/>
     * Needed in order to handle the EasyMock andDelegateTo operation.
     */
    private static class MockDevice implements IDevice {

        public boolean createForward(int localPort, int remotePort) {
            return false;
        }

        public void executeShellCommand(String command, IShellOutputReceiver receiver)
                throws IOException {
        }

        public String getAvdName() {
            return null;
        }

        public Client getClient(String applicationName) {
            return null;
        }

        public String getClientName(int pid) {
            return null;
        }

        public Client[] getClients() {
            return null;
        }

        public FileListingService getFileListingService() {
            return null;
        }

        public Map<String, String> getProperties() {
            return null;
        }

        public String getProperty(String name) {
            return null;
        }

        public int getPropertyCount() {
            return 0;
        }

        public String getMountPoint(String name) {
            return null;
        }

        public RawImage getScreenshot() throws IOException {
            return null;
        }

        public String getSerialNumber() {
            return null;
        }

        public DeviceState getState() {
            return null;
        }

        public SyncService getSyncService() throws IOException {
            return null;
        }

        public boolean hasClients() {
            return false;
        }

        public String installPackage(String packageFilePath, boolean reinstall) throws IOException {
            return null;
        }

        public String installRemotePackage(String remoteFilePath, boolean reinstall)
                throws IOException {
            return null;
        }

        public boolean isBootLoader() {
            return false;
        }

        public boolean isEmulator() {
            return false;
        }

        public boolean isOffline() {
            return false;
        }

        public boolean isOnline() {
            return false;
        }

        public boolean removeForward(int localPort, int remotePort) {
            return false;
        }

        public void removeRemotePackage(String remoteFilePath) throws IOException {
        }

        public void runEventLogService(LogReceiver receiver) throws IOException {
        }

        public void runLogService(String logname, LogReceiver receiver) throws IOException {
        }

        public String syncPackageToDevice(String localFilePath) throws IOException {
            return null;
        }

        public String uninstallPackage(String packageName) throws IOException {
            return null;
        }
    }
}
