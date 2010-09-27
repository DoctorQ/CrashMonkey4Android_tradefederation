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
package com.android.tradefed.testtype;

import com.android.ddmlib.IShellOutputReceiver;
import com.android.ddmlib.Log;
import com.android.ddmlib.MultiLineReceiver;
import com.android.ddmlib.testrunner.ITestRunListener;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
* A {@link IShellOutputReceiver} that parses the stress test data output, collecting metrics on
* number of iterations complete and average time per iteration.
* <p/>
* Looks for the following output
* <p/>
* <code>
* pass 0
* ...
* ==== pass X
* Successfully completed X passes
* </code>
* <br/>
* where 'X' refers to the iteration number
*/
public class NativeStressTestParser extends MultiLineReceiver {

    private final static String LOG_TAG = "NativeStressTestParser";

    private final static Pattern ITERATION_PATTERN = Pattern.compile("^====\\s*pass\\s*(\\d+)");
    private final static Pattern RUN_COMPLETE_PATTERN = Pattern.compile(
            "^Successfully completed\\s*(\\d+)\\s*passes");

    // The metrics key names to report to listeners
    // TODO: these key names are temporary
    static final String AVG_ITERATION_TIME_KEY = "avg-iteration-time";
    static final String ITERATION_KEY = "iterations";

    private final String mTestRunName;
    private final Collection<ITestRunListener> mListeners;
    private int mCurrentIteration = 0;
    private boolean mRunStarted = false;
    private boolean mIsCanceled = false;
    private boolean mIsRunCompleted = false;
    private long mStartTime;

    /**
     * Creates a {@link NativeStressTestParser}.
     *
     * @param runName the run name to report
     * @param listeners the {@link ITestRunListener} to report results to
     */
    public NativeStressTestParser(String runName, Collection<ITestRunListener> listeners) {
        mTestRunName = runName;
        mListeners = listeners;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void processNewLines(String[] lines) {
        if (!mRunStarted) {
            reportRunStarted();
            mRunStarted = true;
        }
        for (String line : lines) {
            parseLine(line);
        }
    }

    private void parseLine(String line) {
        Matcher matcher = ITERATION_PATTERN.matcher(line);
        if (matcher.find()) {
            parseIterationValue(line, matcher.group(1));
        } else {
            matcher = RUN_COMPLETE_PATTERN.matcher(line);
            if (matcher.find()) {
                // TODO: enhance to capture exact # of iterations complete
                mIsRunCompleted = true;
            }
        }
    }

    private void parseIterationValue(String line, String iterationString) {
        try {
            mCurrentIteration = Integer.parseInt(iterationString);
        } catch (NumberFormatException e) {
            // this should never happen, since regular expression matches on digits
            Log.e(LOG_TAG, String.format("Unexpected iteration content %s", line));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void done() {
        final long elapsedTime = System.currentTimeMillis() - mStartTime;
        int iterationsComplete = mIsRunCompleted ? mCurrentIteration + 1 : mCurrentIteration;
        float avgIterationTime = iterationsComplete > 0 ? elapsedTime / iterationsComplete : 0;
        Map<String, String> metricMap = new HashMap<String, String>(1);
        Log.i(LOG_TAG, String.format("Stress test %s is finished. Num iterations %d, avg time %f",
                mTestRunName, iterationsComplete, avgIterationTime));
        metricMap.put(ITERATION_KEY, Integer.toString(iterationsComplete));
        metricMap.put(AVG_ITERATION_TIME_KEY, Float.toString(avgIterationTime));
        for (ITestRunListener listener : mListeners) {
            listener.testRunEnded(elapsedTime, metricMap);
        }
    }

    private void reportRunStarted() {
        mStartTime = System.currentTimeMillis();
        Log.d(LOG_TAG, String.format("Reporting start of stress test run %s", mTestRunName));
        for (ITestRunListener listener : mListeners) {
            listener.testRunStarted(mTestRunName, 0);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isCancelled() {
        return mIsCanceled;
    }
}
