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
package com.android.tradefed.result;

import com.android.ddmlib.Log;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

/**
 * A pass-through {@link ITestInvocationListener} that collects bugreports when configurable events
 * occur and then calls {@link ITestInvocationListener#testLog} on its children after each
 * bugreport is collected.
 * <p />
 * Behaviors: (FIXME: finish this)
 * <ul>
 *   <li>Capture after each if any testcases failed</li>
 *   <li>Capture after each testcase</li>
 *   <li>Capture after each failed testcase</li>
 *   <li>Capture </li>
 * </ul>
 */
public class BugreportCollector extends CollectingTestListener implements ITestInvocationListener {
    /** A predefined predicate which fires after each failed testcase */
    public static final Predicate AFTER_FAILED_TESTCASES =
            p(Relation.AFTER, Freq.EACH, Noun.FAILED_TESTCASE);
    /** A predefined predicate which fires as the first invocation begins */
    public static final Predicate AT_START =
            p(Relation.AT_START_OF, Freq.EACH, Noun.INVOCATION);
    // FIXME: add other useful predefined predicates

    public static interface SubPredicate {}

    public static enum Noun implements SubPredicate {
        // FIXME: find a reasonable way to detect runtime restarts
        // FIXME: try to make sure there aren't multiple ways to specify a single condition
        TESTCASE,
        FAILED_TESTCASE,
        TESTRUN,
        FAILED_TESTRUN,
        INVOCATION,
        FAILED_INVOCATION;
    }

    public static enum Relation implements SubPredicate {
        AFTER,
        AT_START_OF;
    }

    public static enum Freq implements SubPredicate {
        EACH,
        FIRST;
    }

    public static enum Filter implements SubPredicate {
        WITH_FAILING,
        WITH_PASSING,
        WITH_ANY;
    }

    /**
     * A full predicate describing when to capture a bugreport.  Has the following required elements
     * and [optional elements]:
     * RelationP TimingP Noun [FilterP Noun]
     */
    public static class Predicate {
        List<SubPredicate> mSubPredicates = new ArrayList<SubPredicate>(3);
        List<SubPredicate> mFilterSubPredicates = null;

        public Predicate(Relation rp, Freq fp, Noun n) throws IllegalArgumentException {
            assertValidPredicate(rp, fp, n);

            mSubPredicates.add(rp);
            mSubPredicates.add(fp);
            mSubPredicates.add(n);
        }

        public Predicate(Relation rp, Freq fp, Noun fpN, Filter filterP, Noun filterPN)
                throws IllegalArgumentException {
            if (false) {
                throw new IllegalArgumentException("fixme");
            }

            mSubPredicates.add(rp);
            mSubPredicates.add(fp);
            mSubPredicates.add(fpN);
            mFilterSubPredicates = new ArrayList<SubPredicate>(2);
            mFilterSubPredicates.add(filterP);
            mFilterSubPredicates.add(filterPN);
        }

        public static void assertValidPredicate(Relation rp, Freq fp, Noun n)
                throws IllegalArgumentException {
            if (rp == Relation.AT_START_OF) {
                // It doesn't make sense to say AT_START_OF FAILED_(x) since we'll only know that it
                // failed in the AFTER case.
                if (n == Noun.FAILED_TESTCASE || n == Noun.FAILED_TESTRUN ||
                        n == Noun.FAILED_INVOCATION) {
                    throw new IllegalArgumentException(String.format(
                            "Illegal predicate: %s %s isn't valid since we can only check " +
                            "failure on the AFTER event.", fp, n));
                }
            }
            if (n == Noun.INVOCATION || n == Noun.FAILED_INVOCATION) {
                // Block "FIRST INVOCATION" for disambiguation, since there will only ever be one
                // invocation
                if (fp == Freq.FIRST) {
                    throw new IllegalArgumentException(String.format(
                            "Illegal predicate: Since there is only one invocation, please use " +
                            "%s %s rather than %s %s for disambiguation.", Freq.EACH, n, fp, n));
                }
            }
        }

        protected List<SubPredicate> getPredicate() {
            return mSubPredicates;
        }

        protected List<SubPredicate> getFilterPredicate() {
            return mFilterSubPredicates;
        }

        public boolean partialMatch(Predicate otherP) {
            return mSubPredicates.equals(otherP.getPredicate());
        }

        public boolean fullMatch(Predicate otherP) {
            if (partialMatch(otherP)) {
                if (mFilterSubPredicates == null) {
                    return otherP.getFilterPredicate() == null;
                } else {
                    return mFilterSubPredicates.equals(otherP.getFilterPredicate());
                }
            }
            return false;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            ListIterator<SubPredicate> iter = mSubPredicates.listIterator();
            while (iter.hasNext()) {
                SubPredicate p = iter.next();
                sb.append(p.toString());
                if (iter.hasNext()) {
                    sb.append("_");
                }
            }

            return sb.toString();
        }

        @Override
        public boolean equals(Object other) {
            if (other instanceof Predicate) {
                Predicate otherP = (Predicate) other;
                if (otherP == null) {
                    return false;
                }

                return fullMatch(otherP);
            } else {
                return false;
            }
        }

        @Override
        public int hashCode() {
            return mSubPredicates.hashCode();
        }
    }


    // Now that the Predicate framework is done, actually start on the BugreportCollector class
    private static final String LOG_TAG = "BugreportCollector";
    private ITestInvocationListener mListener;
    private ITestDevice mTestDevice;
    private List<Predicate> mPredicates = new LinkedList<Predicate>();
    private boolean mAsynchronous = false;
    private boolean mCapturedBugreport = false;
    // FIXME: Add support for minimum wait time between successive bugreports
    // FIXME: get rid of reset() method

    // Caching for counts that CollectingTestListener doesn't store
    private int mNumFailedRuns = 0;

    public BugreportCollector(ITestInvocationListener listener, ITestDevice testDevice) {
        if (listener == null) {
            throw new NullPointerException("listener must be non-null.");
        }
        if (testDevice == null) {
            throw new NullPointerException("device must be non-null.");
        }
        mListener = listener;
        mTestDevice = testDevice;
    }

    public void addPredicate(Predicate p) {
        mPredicates.add(p);
    }

    /**
     * Block until the collector is not collecting any bugreports.  If the collector isn't actively
     * collecting a bugreport, return immediately
     */
    public void blockUntilIdle() {
        // FIXME
        return;
    }

    /**
     * Set whether bugreport collection should collect the bugreport in a different thread
     * ({@code asynchronous = true}), or block the caller until the bugreport is captured
     * ({@code asynchronous = false}).
     */
    public void setAsynchronous(boolean asynchronous) {
        // FIXME do something
        mAsynchronous = asynchronous;
    }

    /**
     * Actually capture a bugreport and pass it to our child listener.
     */
    void grabBugreport(String logDesc) {
        Log.v(LOG_TAG, String.format("Would grab bugreport for %s.", logDesc));
        String logName = String.format("bug-%s.%d.txt", logDesc, System.currentTimeMillis());
        InputStreamSource bugreport = mTestDevice.getBugreport();
        Log.v(LOG_TAG, String.format("ISS is %s, device is %s, listener is %s, logname is %s",
                bugreport, mTestDevice, mListener, logName));
        mListener.testLog(logName, LogDataType.TEXT, bugreport);
        bugreport.cancel();
    }

    Predicate getPredicate(Predicate predicate) {
        for (Predicate p : mPredicates) {
            if (p.partialMatch(predicate)) {
                return p;
            }
        }
        return null;
    }

    Predicate search(Relation relation, Collection<Freq> freqs, Noun noun) {
        for (Predicate pred : mPredicates) {
            for (Freq freq : freqs) {
                Log.v(LOG_TAG, String.format("Search checking predicate %s",
                        p(relation, freq, noun)));
                if (pred.partialMatch(p(relation, freq, noun))) {
                    return pred;
                }
            }
        }
        return null;
    }

    boolean check(Relation relation, Noun noun) {
        // Expect to get something like "AFTER", "TESTCASE"

        // All freqs that could match _right now_.  Should be added in decreasing order of
        // specificity (so the most specific option has the ability to match first)
        List<Freq> applicableFreqs = new ArrayList<Freq>(2 /* total # freqs in enum */);
        applicableFreqs.add(Freq.EACH);

        TestRunResult curResult = getCurrentRunResults();
        switch (relation) {
            case AFTER:
                switch (noun) {
                    case TESTCASE:
                        if (curResult.getNumTests() == 1) {
                            applicableFreqs.add(Freq.FIRST);
                        }
                        break;

                    case FAILED_TESTCASE:
                        if (curResult.getNumFailedTests() + curResult.getNumErrorTests() == 1) {
                            applicableFreqs.add(Freq.FIRST);
                        }
                        break;

                    case TESTRUN:
                        if (getRunResults().size() == 1) {
                            applicableFreqs.add(Freq.FIRST);
                        }
                        break;

                    case FAILED_TESTRUN:
                        if (mNumFailedRuns == 1) {
                            applicableFreqs.add(Freq.FIRST);
                        }
                        break;
                }
                break;  // case AFTER

            case AT_START_OF:
                switch (noun) {
                    case TESTCASE:
                        if (curResult.getNumTests() == 1) {
                            applicableFreqs.add(Freq.FIRST);
                        }
                        break;

                    case TESTRUN:
                        if (getRunResults().size() == 1) {
                            applicableFreqs.add(Freq.FIRST);
                        }
                        break;
                }
                break;  // case AT_START_OF
        }

        Predicate storedP = search(relation, applicableFreqs, noun);
        if (storedP != null) {
            Log.v(LOG_TAG, String.format("Found storedP %s for relation %s and noun %s", storedP,
                    relation, noun));
            Log.v(LOG_TAG, "Grabbing bugreport.");
            grabBugreport(storedP.toString());
            mCapturedBugreport = true;
            return true;
        } else {
            return false;
        }
    }

    void reset() {
        mCapturedBugreport = false;
    }

    /**
     * Convenience method to build a predicate from subpredicates
     */
    private static Predicate p(Relation rp, Freq fp, Noun n) throws IllegalArgumentException {
        return new Predicate(rp, fp, n);
    }

    /**
     * Convenience method to build a predicate from subpredicates
     */
    private static Predicate p(Relation rp, Freq fp, Noun fpN, Filter filterP, Noun filterPN)
            throws IllegalArgumentException {
        return new Predicate(rp, fp, fpN, filterP, filterPN);
    }


    // Methods from the {@link ITestRunListener} interface
    /**
     * {@inheritDoc}
     */
    public void testEnded(TestIdentifier test, Map<String, String> testMetrics) {
        super.testEnded(test, testMetrics);
        check(Relation.AFTER, Noun.TESTCASE);
        reset();
    }

    /**
     * {@inheritDoc}
     */
    public void testFailed(TestFailure status, TestIdentifier test, String trace) {
        super.testFailed(status, test, trace);
        check(Relation.AFTER, Noun.FAILED_TESTCASE);
        reset();
    }

    /**
     * {@inheritDoc}
     */
    public void testRunEnded(long elapsedTime, Map<String, String> runMetrics) {
        super.testRunEnded(elapsedTime, runMetrics);
        check(Relation.AFTER, Noun.TESTRUN);
    }

    /**
     * {@inheritDoc}
     */
    public void testRunFailed(String errorMessage) {
        super.testRunFailed(errorMessage);
        check(Relation.AFTER, Noun.FAILED_TESTRUN);
    }

    /**
     * {@inheritDoc}
     */
    public void testRunStarted(String runName, int testCount) {
        super.testRunStarted(runName, testCount);
        check(Relation.AT_START_OF, Noun.TESTRUN);
    }

    /**
     * {@inheritDoc}
     */
    public void testRunStopped(long elapsedTime) {
        super.testRunStopped(elapsedTime);
        // FIXME: figure out how to expose this
    }

    /**
     * {@inheritDoc}
     */
    public void testStarted(TestIdentifier test) {
        super.testStarted(test);
        check(Relation.AT_START_OF, Noun.TESTCASE);
    }


    // Methods from the {@link ITestInvocationListener} interface
    /**
     * {@inheritDoc}
     */
    @Override
    public void invocationStarted(IBuildInfo buildInfo) {
        super.invocationStarted(buildInfo);
        check(Relation.AT_START_OF, Noun.INVOCATION);
    }

    // No need to override testLog(...)

    /**
     * {@inheritDoc}
     */
    @Override
    public void invocationEnded(long elapsedTime) {
        super.invocationEnded(elapsedTime);
        check(Relation.AFTER, Noun.INVOCATION);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void invocationFailed(Throwable cause) {
        super.invocationFailed(cause);
        check(Relation.AFTER, Noun.FAILED_INVOCATION);
    }

    // No need to override getSummary()

}

