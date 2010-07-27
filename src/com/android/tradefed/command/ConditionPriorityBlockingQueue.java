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
package com.android.tradefed.command;

import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A class with {@link PriorityBlockingQueue}-like operations that can retrieve objects that
 * match a certain condition.
 *
 * @see {@link PriorityBlockingQueue}
 */
public class ConditionPriorityBlockingQueue<T> {

    /**
     * An interface for determining if elements match some sort of condition.
     *
     * @param <T>
     */
    public static interface Matcher<T> {
        /**
         * Determine if given <var>element</var> meets required condition
         *
         * @param element the object to match
         * @return <code>true</code> if condition was met. <code>false</code> otherwise.
         */
        boolean matches(T element);
    }

    /**
     * A {@link Matcher} that matches any object.
     *
     * @param <T>
     */
    public static class AlwaysMatch<T> implements Matcher<T> {

        /**
         * {@inheritDoc}
         */
        public boolean matches(T element) {
            return true;
        }
    }

    private static class ConditionMatcherPair<T> {
        private final Matcher<T> mMatcher;
        private final Condition mCondition;

        ConditionMatcherPair(Matcher<T> m, Condition c) {
            mMatcher = m;
            mCondition = c;
        }
    }

    /** the list of current objects */
    private final LinkedList<T> mList;

    /** the global lock */
    private final ReentrantLock mLock = new ReentrantLock(true);
    /**
     * List of {@link Matcher}'s that are waiting for an object to be added to queue that meets
     * their criteria
     */
    private final List<ConditionMatcherPair<T>> mWaitingMatcherList;

    private final Comparator<T> mComparator;

    /**
     * Creates a {@link ConditionPriorityBlockingQueue}
     *
     * @param c the {@link Comparator} used to prioritize the queue.
     */
    public ConditionPriorityBlockingQueue(Comparator<T> c) {
        mComparator = c;
        mList = new LinkedList<T>();
        mWaitingMatcherList = new LinkedList<ConditionMatcherPair<T>>();
    }

    /**
     * Retrieves and removes the head of this queue.
     *
     * @return the head of this queue, or <code>null</code> if the queue is empty
     */
    public T poll() {
        return poll(new AlwaysMatch<T>());
    }

    /**
     * Retrieves and removes the minimum (as judged by the provided {@link Comparator} element T
     * in the queue where <var>matcher.matches(T)</var> is <code>true</code>.
     *
     * @return the minimum matched element or <code>null</code> if there are no matching elements
     */
    public T poll(Matcher<T> matcher) {
        mLock.lock();
        try {
            // reference to the current min object
            T minObject = null;
            int minObjIndex = -1;
            for (int i=0; i < mList.size(); i++ ) {
                T obj = mList.get(i);
                if (matcher.matches(obj) &&
                        (minObject == null ||
                         (mComparator.compare(obj, minObject) < 0) )) {
                    minObject = obj;
                    minObjIndex = i;
                }
            }
            if (minObjIndex != -1) {
                mList.remove(minObjIndex);
            }
            return minObject;
        } finally {
            mLock.unlock();
        }
    }

    /**
     * Retrieves and removes the head of this queue, waiting if necessary until an element becomes
     * available.
     *
     * @return the head of this queue
     * @throws InterruptedException if interrupted while waiting
     */
    public T take() throws InterruptedException {
        return take(new AlwaysMatch<T>());
    }

    /**
     * Retrieves and removes the first element T in the queue where <var>matcher.matches(T)</var> is
     * <code>true</code>, waiting if necessary until such an element becomes available.
     *
     * @return the matched element
     * @throws InterruptedException if interrupted while waiting
     */
    public T take(Matcher<T> matcher) throws InterruptedException {
        mLock.lockInterruptibly();
        try {
            T matchedObj = null;
            Condition myCondition = mLock.newCondition();
            ConditionMatcherPair<T> myMatcherPair = new ConditionMatcherPair<T>(matcher,
                    myCondition);
            mWaitingMatcherList.add(myMatcherPair);
            try {
                while ((matchedObj = poll(matcher)) == null) {
                    myCondition.await();
                }
            } catch (InterruptedException ie) {
                // TODO: do we need to propagate to non-interrupted thread?
                throw ie;
            } finally {
                mWaitingMatcherList.remove(myMatcherPair);
            }

            assert matchedObj != null;
            return matchedObj;
        } finally {
            mLock.unlock();
        }
    }

    /**
     * Inserts the specified element into this queue. As the queue is unbounded this method will
     * never block.
     *
     * @param addedElement the element to add
     * @return <code>true</code>
     * @throws ClassCastException if the specified element cannot be compared with elements
     *             currently in the priority queue according to the priority queue's ordering
     * @throws NullPointerException if the specified element is null
     */
    public boolean add(T addedElement) {
        mLock.lock();
        try {
            boolean ok = mList.add(addedElement);
            assert ok;

            for (ConditionMatcherPair<T> matcherPair : mWaitingMatcherList) {
                if (matcherPair.mMatcher.matches(addedElement)) {
                    matcherPair.mCondition.signal();
                    break;
                }
            }
            return true;
        } finally {
            mLock.unlock();
        }
    }

    /**
     * Removes all elements from this queue.
     */
    public void clear() {
        mList.clear();
    }
}
