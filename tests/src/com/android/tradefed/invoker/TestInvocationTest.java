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
package com.android.tradefed.invoker;

import com.android.ddmlib.IDevice;
import com.android.ddmlib.Log.LogLevel;
import com.android.tradefed.command.FatalHostError;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.IDeviceRecovery;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.ILeveledLogOutput;
import com.android.tradefed.log.LogRegistry;
import com.android.tradefed.log.StubLogRegistry;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.ITestSummaryListener;
import com.android.tradefed.result.InvocationStatus;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.TestSummary;
import com.android.tradefed.targetsetup.BuildError;
import com.android.tradefed.targetsetup.IBuildInfo;
import com.android.tradefed.targetsetup.IBuildProvider;
import com.android.tradefed.targetsetup.ITargetPreparer;
import com.android.tradefed.targetsetup.TargetSetupError;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;

import org.easymock.Capture;
import org.easymock.EasyMock;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import junit.framework.Test;
import junit.framework.TestCase;

/**
 * Unit tests for {@link TestInvocation}.
 */
public class TestInvocationTest extends TestCase {

    private static final Map<String, String> EMPTY_MAP = Collections.emptyMap();
    private static final TestSummary mSummary = new TestSummary("http://www.url.com/report.txt");

    /** The {@link TestInvocation} under test, with all dependencies mocked out */
    private TestInvocation mTestInvocation;

    // The mock objects.
    private IConfiguration mMockConfiguration;
    private ITestDevice mMockDevice;
    private ITargetPreparer mMockPreparer;
    private IBuildProvider mMockBuildProvider;
    private IBuildInfo mMockBuildInfo;
    private ITestInvocationListener mMockTestListener;
    private ITestSummaryListener mMockSummaryListener;
    private ILeveledLogOutput mMockLogger;
    private IDeviceRecovery mMockRecovery;
    private List<ITestInvocationListener> mListeners;
    private Capture<List<TestSummary>> mUriCapture;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mMockConfiguration = EasyMock.createMock(IConfiguration.class);
        mMockDevice = EasyMock.createMock(ITestDevice.class);
        mMockRecovery = EasyMock.createMock(IDeviceRecovery.class);
        mMockPreparer = EasyMock.createMock(ITargetPreparer.class);
        mMockBuildProvider = EasyMock.createMock(IBuildProvider.class);
        // Use strict mocks here since the order of Listener calls is important
        mMockTestListener = EasyMock.createStrictMock(ITestInvocationListener.class);
        mMockSummaryListener = EasyMock.createStrictMock(ITestSummaryListener.class);
        mMockBuildInfo = EasyMock.createMock(IBuildInfo.class);
        mMockLogger = EasyMock.createNiceMock(ILeveledLogOutput.class);

        EasyMock.expect(mMockConfiguration.getBuildProvider()).andReturn(mMockBuildProvider);
        EasyMock.expect(mMockConfiguration.getTargetPreparer()).andReturn(mMockPreparer);

        mListeners = new ArrayList<ITestInvocationListener>(1);
        mListeners.add(mMockTestListener);
        mListeners.add(mMockSummaryListener);
        EasyMock.expect(mMockConfiguration.getTestInvocationListeners()).andReturn(
                mListeners);

        EasyMock.expect(mMockConfiguration.getLogOutput()).andReturn(
                mMockLogger);
        EasyMock.expect(mMockConfiguration.getDeviceRecovery()).andReturn(
                mMockRecovery);
        EasyMock.expect(mMockLogger.getLogLevel()).andReturn(LogLevel.VERBOSE.getStringValue());
        mMockLogger.printLog((LogLevel)EasyMock.anyObject(),
            (String)EasyMock.anyObject(), (String)EasyMock.anyObject());
        EasyMock.expect(mMockDevice.getSerialNumber()).andStubReturn("serial");
        mMockDevice.setRecovery(mMockRecovery);

        EasyMock.expect(mMockBuildInfo.getBuildId()).andStubReturn(1);
        EasyMock.expect(mMockBuildInfo.getBuildAttributes()).andStubReturn(EMPTY_MAP);
        EasyMock.expect(mMockBuildInfo.getTestTarget()).andStubReturn("");
        mUriCapture = new Capture<List<TestSummary>>();

        // create the BaseTestInvocation to test
        mTestInvocation = new TestInvocation() {
            @Override
            LogRegistry getLogRegistry() {
                // use a StubLogRegistry to prevent registration of mock loggers
                return new StubLogRegistry();
            }
        };
    }

    @Override
    protected void tearDown() throws Exception {
      super.tearDown();

    }

    /**
     * Test the normal case invoke scenario with a {@link IRemoteTest}.
     * <p/>
     * Verifies that all external interfaces get notified as expected.
     */
    public void testInvoke_RemoteTest() throws Exception {
        IRemoteTest test = EasyMock.createMock(IRemoteTest.class);
        setupMockSuccessListeners();

        test.run(mListeners);
        setupNormalInvoke(test);
        mTestInvocation.invoke(mMockDevice, mMockConfiguration);
        verifyMocks(test);
        verifySummaryListener();
    }

    /**
     * Test the normal case invoke scenario with an {@link ITestSummaryListener} masquerading as
     * an {@link ITestInvocationListener}.
     * <p/>
     * Verifies that all external interfaces get notified as expected.
     * TODO: For results_reporters to work as both ITestInvocationListener and ITestSummaryListener,
     * TODO: this test _must_ pass.  Currently, it does not, so that mode of usage is not supported.
     */
    public void DISABLED_testInvoke_twoSummary() throws Exception {
        mListeners.clear();
        mMockTestListener =
                (ITestInvocationListener)EasyMock.createStrictMock(ITestSummaryListener.class);
        mListeners.add(mMockTestListener);
        mListeners.add(mMockSummaryListener);

        IRemoteTest test = EasyMock.createMock(IRemoteTest.class);
        setupMockSuccessListeners();

        test.run(mListeners);
        setupNormalInvoke(test);
        mTestInvocation.invoke(mMockDevice, mMockConfiguration);
        verifyMocks(test);
        verifySummaryListener();
    }

    /**
     * Test the invoke scenario where build retrieve fails.
     * <p/>
     * An invocation will not be started in this scenario.
     */
    public void testInvoke_buildFailed() throws TargetSetupError, ConfigurationException,
            DeviceNotAvailableException  {
        TargetSetupError exception = new TargetSetupError("error");
        EasyMock.expect(mMockBuildProvider.getBuild()).andThrow(exception);
        Test test = EasyMock.createMock(Test.class);
        EasyMock.expect(mMockConfiguration.getTests()).andReturn(createTestList(test));
        mMockLogger.closeLog();
        replayMocks(test);
        // TODO: add verification for sending an error/logging error ?
        mTestInvocation.invoke(mMockDevice, mMockConfiguration);
        verifyMocks(test);
    }

    /**
     * Test the invoke scenario where there is no build to test.
     */
    public void testInvoke_noBuild() throws TargetSetupError, ConfigurationException,
            DeviceNotAvailableException  {
        EasyMock.expect(mMockBuildProvider.getBuild()).andReturn(null);
        Test test = EasyMock.createMock(Test.class);
        EasyMock.expect(mMockConfiguration.getTests()).andReturn(createTestList(test));
        replayMocks(test);
        mTestInvocation.invoke(mMockDevice, mMockConfiguration);
        verifyMocks(test);
    }

    /**
     * Test the invoke scenario where config retrieve fails.
     */
    public void testInvoke_configFailed() throws ConfigurationException,
            DeviceNotAvailableException  {
        EasyMock.expect(mMockConfiguration.getTests()).andThrow(new ConfigurationException("fail"));
        EasyMock.expect(mMockLogger.getLogLevel()).andReturn(LogLevel.VERBOSE.getStringValue());
        mMockLogger.printLog((LogLevel)EasyMock.anyObject(),
            (String)EasyMock.anyObject(), (String)EasyMock.anyObject());
        mMockLogger.closeLog();
        Test test = EasyMock.createMock(Test.class);
        replayMocks(test);
        // TODO: add verification for sending an error/logging error ?
        mTestInvocation.invoke(mMockDevice, mMockConfiguration);
        verifyMocks(test);
    }

    /**
     * Test the {@link TestInvocation#invoke(IDevice, IConfiguration)} scenario where the
     * test is a {@link IDeviceTest}
     */
    public void testInvoke_deviceTest() throws Exception {
         DeviceConfigTest mockDeviceTest = EasyMock.createMock(DeviceConfigTest.class);
         EasyMock.expect(mMockConfiguration.getTests()).andReturn(createTestList(
                 mockDeviceTest));
         mockDeviceTest.setDevice(mMockDevice);
         mockDeviceTest.run(mListeners);
         setupMockSuccessListeners();
         setupNormalInvoke(mockDeviceTest);
         mTestInvocation.invoke(mMockDevice, mMockConfiguration);
         verifyMocks(mockDeviceTest);
         verifySummaryListener();
    }

    /**
     * Test the invoke scenario where test run throws {@link IllegalArgumentException}
     *
     * @throws Exception if unexpected error occurs
     */
    public void testInvoke_testFail() throws Exception {
        IllegalArgumentException exception = new IllegalArgumentException();
        IRemoteTest test = EasyMock.createMock(IRemoteTest.class);
        test.run(mListeners);
        EasyMock.expectLastCall().andThrow(exception);
        setupMockFailureListeners(exception);
        mMockBuildProvider.buildNotTested(mMockBuildInfo);
        EasyMock.expect(mMockLogger.getLogLevel()).andReturn(LogLevel.VERBOSE.getStringValue());
        mMockLogger.printLog((LogLevel)EasyMock.anyObject(),
            (String)EasyMock.anyObject(), (String)EasyMock.anyObject());
        setupNormalInvoke(test);
        try {
            mTestInvocation.invoke(mMockDevice, mMockConfiguration);
            fail("IllegalArgumentException was not rethrown");
        } catch (IllegalArgumentException e) {
            // expected
        }
        verifyMocks(test);
        verifySummaryListener();
    }

    /**
     * Test the invoke scenario where test run throws {@link FatalHostError}
     *
     * @throws Exception if unexpected error occurs
     */
    public void testInvoke_fatalError() throws Exception {
        FatalHostError exception = new FatalHostError("error");
        IRemoteTest test = EasyMock.createMock(IRemoteTest.class);
        test.run(mListeners);
        EasyMock.expectLastCall().andThrow(exception);
        setupMockFailureListeners(exception);
        mMockBuildProvider.buildNotTested(mMockBuildInfo);
        EasyMock.expect(mMockLogger.getLogLevel()).andReturn(LogLevel.VERBOSE.getStringValue());
        mMockLogger.printLog((LogLevel)EasyMock.anyObject(),
            (String)EasyMock.anyObject(), (String)EasyMock.anyObject());
        setupNormalInvoke(test);
        try {
            mTestInvocation.invoke(mMockDevice, mMockConfiguration);
            fail("FatalHostError was not rethrown");
        } catch (FatalHostError e)  {
            // expected
        }
        verifyMocks(test);
        verifySummaryListener();
    }

    /**
     * Test the invoke scenario where test run throws {@link DeviceNotAvailable}
     *
     * @throws Exception if unexpected error occurs
     */
    public void testInvoke_deviceNotAvail() throws Exception {
        DeviceNotAvailableException exception = new DeviceNotAvailableException();
        IRemoteTest test = EasyMock.createMock(IRemoteTest.class);
        test.run(mListeners);
        EasyMock.expectLastCall().andThrow(exception);
        setupMockFailureListeners(exception);
        mMockBuildProvider.buildNotTested(mMockBuildInfo);
        EasyMock.expect(mMockLogger.getLogLevel()).andReturn(LogLevel.VERBOSE.getStringValue());
        mMockLogger.printLog((LogLevel)EasyMock.anyObject(),
            (String)EasyMock.anyObject(), (String)EasyMock.anyObject());
        setupNormalInvoke(test);
        try {
            mTestInvocation.invoke(mMockDevice, mMockConfiguration);
            fail("DeviceNotAvailableException not thrown");
        } catch (DeviceNotAvailableException e) {
            // expected
        }
        verifyMocks(test);
        verifySummaryListener();
    }

    /**
     * Test the invoke scenario where preparer throws {@link BuildError}
     *
     * @throws Exception if unexpected error occurs
     */
    public void testInvoke_buildError() throws Exception {
        BuildError exception = new BuildError("error");
        IRemoteTest test = EasyMock.createMock(IRemoteTest.class);
        EasyMock.expect(mMockConfiguration.getTests()).andReturn(createTestList(test));
        EasyMock.expect(mMockBuildProvider.getBuild()).andReturn(mMockBuildInfo);
        mMockBuildInfo.addBuildAttribute((String)EasyMock.anyObject(),
                (String)EasyMock.anyObject());
        mMockPreparer.setUp(mMockDevice, mMockBuildInfo);
        EasyMock.expectLastCall().andThrow(exception);
        setupMockFailureListeners(exception);

        EasyMock.expect(mMockDevice.getLogcat()).andReturn(new ByteArrayInputStream(new byte[0]))
                .times(mListeners.size());
        EasyMock.expect(mMockLogger.getLog()).andReturn(new ByteArrayInputStream(new byte[0]))
                .times(mListeners.size());

        mMockLogger.closeLog();
        mMockBuildInfo.cleanUp();
        EasyMock.expect(mMockLogger.getLogLevel()).andReturn(LogLevel.VERBOSE.getStringValue());
        mMockLogger.printLog((LogLevel)EasyMock.anyObject(),
            (String)EasyMock.anyObject(), (String)EasyMock.anyObject());
        replayMocks(test);
        mTestInvocation.invoke(mMockDevice, mMockConfiguration);
        verifyMocks(test);
        verifySummaryListener();
    }

    /**
     * Set up expected conditions for normal run up to the part where tests are run.
     *
     * @param test the {@link Test} to use.
     */
    private void setupNormalInvoke(Test test) throws Exception {
        EasyMock.expect(mMockConfiguration.getTests()).andReturn(createTestList(test));
        EasyMock.expect(mMockBuildProvider.getBuild()).andReturn(mMockBuildInfo);
        mMockBuildInfo.addBuildAttribute((String)EasyMock.anyObject(),
                (String)EasyMock.anyObject());
        mMockPreparer.setUp(mMockDevice, mMockBuildInfo);

        EasyMock.expect(mMockDevice.getLogcat()).andReturn(new ByteArrayInputStream(new byte[0]))
                .times(mListeners.size());

        EasyMock.expect(mMockLogger.getLog()).andReturn(new ByteArrayInputStream(new byte[0]))
                .times(mListeners.size());

        mMockLogger.closeLog();
        mMockBuildInfo.cleanUp();
        replayMocks(test);
    }

    /**
     * Set up expected conditions for the test InvocationListener and SummaryListener
     * <p/>
     * The order of calls for a single listener should be:
     * <ol>
     *   <li>invocationStarted</li>
     *   <li>invocationFailed (if run failed)</li>
     *   <li>testLog(DEVICE_LOG_NAME, ...)</li>
     *   <li>testLog(TRADEFED_LOG_NAME, ...)</li>
     *   <li>putSummary (for an ITestSummaryListener)</li>
     *   <li>invocationEnded</li>
     *   <li>getSummary (for an ITestInvocationListener)</li>
     * </ol>
     * However note that, across all listeners, any getSummary call will precede all putSummary
     * calls.
     */
    private void setupMockListeners(InvocationStatus status, Exception exception) {
        // invocationStarted
        mMockTestListener.invocationStarted(mMockBuildInfo);
        mMockSummaryListener.invocationStarted(mMockBuildInfo);

        // invocationFailed
        if (!status.equals(InvocationStatus.SUCCESS)) {
                mMockTestListener.invocationFailed(EasyMock.eq(exception));
                mMockSummaryListener.invocationFailed(EasyMock.eq(exception));
        }

        // testLog (mMockTestListener)
        mMockTestListener.testLog(EasyMock.eq(TestInvocation.DEVICE_LOG_NAME),
                EasyMock.eq(LogDataType.TEXT), (InputStream)EasyMock.anyObject());
        mMockTestListener.testLog(EasyMock.eq(TestInvocation.TRADEFED_LOG_NAME),
                EasyMock.eq(LogDataType.TEXT), (InputStream)EasyMock.anyObject());

        // testLog (mMockSummaryListener)
        mMockSummaryListener.testLog(EasyMock.eq(TestInvocation.DEVICE_LOG_NAME),
                EasyMock.eq(LogDataType.TEXT), (InputStream)EasyMock.anyObject());
        mMockSummaryListener.testLog(EasyMock.eq(TestInvocation.TRADEFED_LOG_NAME),
                EasyMock.eq(LogDataType.TEXT), (InputStream)EasyMock.anyObject());

        // invocationEnded, getSummary (mMockTestListener)
        mMockTestListener.invocationEnded(EasyMock.anyLong());
        EasyMock.expect(mMockTestListener.getSummary()).andReturn(mSummary);

        // putSummary, invocationEnded (mMockSummaryListener)
        mMockSummaryListener.putSummary(EasyMock.capture(mUriCapture));
        mMockSummaryListener.invocationEnded(EasyMock.anyLong());
    }

    private void setupMockSuccessListeners() {
        setupMockListeners(InvocationStatus.SUCCESS, null);
    }

    private void setupMockFailureListeners(Exception exception) {
        setupMockListeners(InvocationStatus.FAILED, exception);
    }

    private void verifySummaryListener() {
        // Check that we captured the expected uris List
        List<TestSummary> summaries = mUriCapture.getValue();
        assertEquals(1, summaries.size());
        assertEquals(mSummary, summaries.get(0));
    }

    /**
     * Verify all mock objects received expected calls
     */
    private void verifyMocks(Test mockTest) {
        // note: intentionally exclude configuration and logger from verification - don't care
        // what methods are called
        EasyMock.verify(mockTest, mMockTestListener, mMockSummaryListener, mMockPreparer,
                mMockBuildProvider, mMockBuildInfo);
    }

    /**
     * Switch all mock objects into replay mode.
     */
    private void replayMocks(Test mockTest) {
        EasyMock.replay(mockTest, mMockTestListener, mMockSummaryListener, mMockConfiguration,
                mMockPreparer, mMockBuildProvider, mMockLogger, mMockDevice, mMockBuildInfo);
    }

    /**
     * Helper method to create a list of tests containing given test.
     */
    private List<Test> createTestList(Test test) {
        List<Test> tests = new ArrayList<Test>(1);
        tests.add(test);
        return tests;
    }

    /**
     * Interface for testing device config pass through.
     */
    private interface DeviceConfigTest extends IRemoteTest, IDeviceTest {

    }
}
