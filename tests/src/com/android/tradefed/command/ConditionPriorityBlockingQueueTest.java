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

import com.android.tradefed.command.ConditionPriorityBlockingQueue.Matcher;

import java.util.Comparator;

import junit.framework.TestCase;

/**
 * Unit tests for {@link ConditionPriorityBlockingQueue}.
 */
public class ConditionPriorityBlockingQueueTest extends TestCase {

    private ConditionPriorityBlockingQueue<Integer> mQueue;

    /**
     * {@inheritDoc}
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mQueue = new ConditionPriorityBlockingQueue<Integer>(new IntCompare());
    }

    /**
     * Test {@link ConditionPriorityBlockingQueue#poll()} when queue is empty.
     */
    public void testPoll_empty() {
        assertNull(mQueue.poll());
    }

    /**
     * Test {@link ConditionPriorityBlockingQueue#take()} when a single object is in queue.
     */
    public void testTake() throws InterruptedException {
        Integer one = new Integer(1);
        mQueue.add(one);
        assertEquals(one, mQueue.take());
        assertNull(mQueue.poll());
    }

    /**
     * Test {@link ConditionPriorityBlockingQueue#take()} when multiple objects are in queue, and
     * verify objects are returned in expected order.
     */
    public void testTake_priority() throws InterruptedException {
        Integer one = new Integer(1);
        Integer two = new Integer(2);
        mQueue.add(two);
        mQueue.add(one);
        assertEquals(one, mQueue.take());
        assertEquals(two, mQueue.take());
        assertNull(mQueue.poll());
    }

    /**
     * Same as {@link ConditionPriorityBlockingQueueTest#testTake_priority()}, but add the test
     * objects in inverse order.
     */
    public void testTake_priorityReverse() throws InterruptedException {
        Integer one = new Integer(1);
        Integer two = new Integer(2);
        mQueue.add(one);
        mQueue.add(two);
        assertEquals(one, mQueue.take());
        assertEquals(two, mQueue.take());
        assertNull(mQueue.poll());
    }

    /**
     * Test {@link ConditionPriorityBlockingQueue#take()} when object is not initially present.
     */
    public void testTake_delayedAdd() throws InterruptedException {
        final Integer one = new Integer(1);
        Thread delayedAdd = new Thread() {
            @Override
            public void run() {
                try {
                    sleep(200);
                } catch (InterruptedException e) {
                }
                mQueue.add(one);
            }
        };
        delayedAdd.start();
        assertEquals(one, mQueue.take());
        assertNull(mQueue.poll());
    }

    /**
     * Test {@link ConditionPriorityBlockingQueue#take(Matcher)} when object that matches is not
     * initially present.
     */
    public void testTake_matcher_delayedAdd() throws InterruptedException {
        final Integer one = new Integer(1);
        final Integer two = new Integer(2);
        mQueue.add(two);
        Thread delayedAdd = new Thread() {
            @Override
            public void run() {
                try {
                    sleep(200);
                } catch (InterruptedException e) {
                }
                mQueue.add(one);
            }
        };
        delayedAdd.start();
        assertEquals(one, mQueue.take(new OneMatcher()));
        assertNull(mQueue.poll(new OneMatcher()));
        assertEquals(two, mQueue.poll());
    }

    /**
     * Test {@link ConditionPriorityBlockingQueue#take(Matcher)} when multiple threads are waiting
     */
    public void testTake_multiple_matchers() throws InterruptedException {
        final Integer one = new Integer(1);
        final Integer second_one = new Integer(1);

        Thread waiter = new Thread() {
            @Override
            public void run() {
                try {
                    mQueue.take(new OneMatcher());
                } catch (InterruptedException e) {

                }
            }
        };
        waiter.start();
        Thread waiter2 = new Thread() {
            @Override
            public void run() {
                try {
                    mQueue.take(new OneMatcher());
                } catch (InterruptedException e) {
                }
            }
        };
        waiter2.start();

        Thread delayedAdd = new Thread() {
            @Override
            public void run() {
                try {
                    sleep(200);
                } catch (InterruptedException e) {
                }
                mQueue.add(one);
            }
        };
        delayedAdd.start();
        Thread delayedAdd2 = new Thread() {
            @Override
            public void run() {
                try {
                    sleep(300);
                } catch (InterruptedException e) {
                }
                mQueue.add(second_one);
            }
        };
        delayedAdd2.start();

        // wait for blocked threads to die. This test will deadlock if failed
        waiter.join();
        waiter2.join();

        assertNull(mQueue.poll());
    }

    /**
     * Test {@link ConditionPriorityBlockingQueue#poll(Matcher)} when queue is empty.
     */
    public void testPoll_condition_empty() {
        assertNull(mQueue.poll(new OneMatcher()));
    }

    /**
     * Test {@link ConditionPriorityBlockingQueue#poll(Matcher)} when object matches, and one
     * doesn't.
     */
    public void testPoll_condition() {
        Integer one = new Integer(1);
        Integer two = new Integer(2);
        mQueue.add(one);
        mQueue.add(two);
        assertEquals(one, mQueue.poll(new OneMatcher()));
        assertNull(mQueue.poll(new OneMatcher()));
    }

    /**
     * Same as {@link ConditionPriorityBlockingQueueTest#testPoll_condition()}, but objects are
     * added in inverse order.
     */
    public void testPoll_condition_reverse() {
        Integer one = new Integer(1);
        Integer two = new Integer(2);
        mQueue.add(two);
        mQueue.add(one);
        assertEquals(one, mQueue.poll(new OneMatcher()));
        assertNull(mQueue.poll(new OneMatcher()));
    }

    /**
     * A {@link Comparator} for {@link Integer}
     */
    private static class IntCompare implements Comparator<Integer> {

        public int compare(Integer o1, Integer o2) {
            if (o1 == o2) {
                return 0;
            } else if (o1 < o2) {
                return -1;
            } else {
                return 1;
            }
        }

    }

    private static class OneMatcher implements Matcher<Integer> {

        public boolean matches(Integer element) {
            return element == 1;
        }
    }
}
