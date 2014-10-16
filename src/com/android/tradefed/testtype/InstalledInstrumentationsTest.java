/*
 * Copyright (C) 2012 The Android Open Source Project
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
package com.android.tradefed.testtype;

import com.android.ddmlib.IShellOutputReceiver;
import com.android.ddmlib.MultiLineReceiver;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.Option.Importance;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.BugreportCollector;

import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.testtype.testdefs.XmlDefsTest;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Runs all instrumentation found on current device.
 */
@OptionClass(alias = "installed-instrumentation")
public class InstalledInstrumentationsTest implements IDeviceTest, IResumableTest {

    /** the metric key name for the test coverage target value */
    // TODO: move this to a more generic location
    public static final String COVERAGE_TARGET_KEY = XmlDefsTest.COVERAGE_TARGET_KEY;
    private static final Pattern LIST_INSTR_PATTERN =
            Pattern.compile("instrumentation:(.+)/(.+) \\(target=(.+)\\)");

    private ITestDevice mDevice;

    @Option(name = "timeout",
            description = "Fail any test that takes longer than the specified number of "
            + "milliseconds.")
    private int mTestTimeout = 10 * 60 * 1000;  // default to 10 minutes

    @Option(name = "size",
            description = "Restrict tests to a specific test size. " +
            "One of 'small', 'medium', 'large'",
            importance = Importance.IF_UNSET)
    private String mTestSize = null;

    @Option(name = "runner",
            description = "Restrict tests executed to a specific instrumentation class runner. " +
    "Installed instrumentations that do not have this runner will be skipped.")
    private String mRunner = null;

    @Option(name = "rerun",
            description = "Rerun unexecuted tests individually on same device if test run " +
            "fails to complete.")
    private boolean mIsRerunMode = true;

    @Option(name = "resume",
            description = "Schedule unexecuted tests for resumption on another device " +
            "if first device becomes unavailable.")
    private boolean mIsResumeMode = false;

    @Option(name = "send-coverage",
            description = "Send coverage target info to test listeners.")
    private boolean mSendCoverage = false;

    @Option(name = "bugreport-on-failure", description = "Sets which failed testcase events " +
            "cause a bugreport to be collected. a bugreport after failed testcases.  Note that " +
            "there is _no feedback mechanism_ between the test runner and the bugreport " +
            "collector, so use the EACH setting with due caution.")
    private BugreportCollector.Freq mBugreportFrequency = null;

    @Option(name = "class",
            description = "Only run tests in specified class")
    private String mTestClass = null;

    private List<InstrumentationTest> mTests = null;

    /**
     * {@inheritDoc}
     */
    @Override
    public ITestDevice getDevice() {
        return mDevice;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setDevice(ITestDevice device) {
        mDevice = device;
    }

    /**
     * Set the send coverage flag.
     * <p/>
     * Exposed for unit testing.
     */
    void setSendCoverage(boolean sendCoverage) {
        mSendCoverage = sendCoverage;
    }

    /**
     * Gets the list of {@link InstrumentationTest}s contained within.
     * <p/>
     * Exposed for unit testing.
     */
    List<InstrumentationTest> getTests() {
        return mTests;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        if (getDevice() == null) {
            throw new IllegalArgumentException("Device has not been set");
        }
        buildTests();
        doRun(listener);
    }

    /**
     * Build the list of tests to run from the device, if not done already. Note: Can be called
     * multiple times in case of resumed runs.

     * @throws DeviceNotAvailableException
     */
    private void buildTests() throws DeviceNotAvailableException {
        if (mTests == null) {
            ListInstrumentationParser parser = new ListInstrumentationParser(mRunner);
            getDevice().executeShellCommand("pm list instrumentation", parser);
            if (parser.getParsedTests().isEmpty()) {
                throw new IllegalArgumentException(String.format(
                        "No instrumentations were found on device %s",
                        getDevice().getSerialNumber()));
            }
            mTests = parser.getParsedTests();
        }
    }

    /**
     * Run the previously built tests.
     *
     * @param listener the {@link ITestInvocationListener}
     * @throws DeviceNotAvailableException
     */
    private void doRun(ITestInvocationListener listener) throws DeviceNotAvailableException {
        while (!mTests.isEmpty()) {
            InstrumentationTest test = mTests.get(0);

            CLog.d("Running test %s on %s", test.getPackageName(), getDevice().getSerialNumber());

            if (mSendCoverage && test.getCoverageTarget() != null) {
                sendCoverage(test.getPackageName(), test.getCoverageTarget(), listener);
            }
            test.setDevice(getDevice());
            test.setClassName(mTestClass);
            test.run(listener);
            // test completed, remove from list
            mTests.remove(0);
        }
    }

    /**
     * Forwards the tests coverage target info as a test metric.
     *
     * @param packageName
     * @param coverageTarget
     * @param listener
     */
    private void sendCoverage(String packageName, String coverageTarget,
            ITestInvocationListener listener) {
        Map<String, String> coverageMetric = new HashMap<String, String>(1);
        coverageMetric.put(COVERAGE_TARGET_KEY, coverageTarget);
        listener.testRunStarted(packageName, 0);
        listener.testRunEnded(0, coverageMetric);
    }

    int getTestTimeout() {
        return mTestTimeout;
    }

    String getTestSize() {
        return mTestSize;
    }

    /**
     * Creates the {@link InstrumentationTest} to use. Exposed for unit testing.
     */
    InstrumentationTest createInstrumentationTest() {
        return new InstrumentationTest();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isResumable() {
        // hack to not resume if tests were never run
        // TODO: fix this properly in TestInvocation
        if (mTests == null) {
            return false;
        }
        return mIsResumeMode;
    }

    /**
     * A {@link IShellOutputReceiver} that parses the output of a 'pm list instrumentation' query
     */
    private class ListInstrumentationParser extends MultiLineReceiver {

        private List<InstrumentationTest> mTests = new LinkedList<InstrumentationTest>();
        private String mRunnerFilter;

        /**
         * @param mRunner
         */
        public ListInstrumentationParser(String runner) {
            mRunnerFilter = runner;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isCancelled() {
            return false;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void processNewLines(String[] lines) {
            for (String line : lines) {

                Matcher m = LIST_INSTR_PATTERN.matcher(line);
                if (m.find()) {
                    String runner = m.group(2);
                    if (mRunnerFilter == null || mRunnerFilter.equals(runner)) {
                        InstrumentationTest t = createInstrumentationTest();
                        t.setPackageName(m.group(1));
                        t.setRunnerName(runner);
                        t.setCoverageTarget(m.group(3));
                        t.setRerunMode(mIsRerunMode);
                        t.setResumeMode(mIsResumeMode);
                        t.setTestSize(getTestSize());
                        t.setTestTimeout(getTestTimeout());
                        t.setBugreportFrequency(mBugreportFrequency);
                        mTests.add(t);
                    }
                }
            }
        }

        public List<InstrumentationTest> getParsedTests() {
            return mTests;
        }
    }
}
