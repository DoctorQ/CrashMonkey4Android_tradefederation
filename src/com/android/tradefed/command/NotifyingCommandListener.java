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

package com.android.tradefed.command;

/**
 * A helper {@link ICommandListener} that will notify listeners when {@link #commandStarted} has
 * been called the requested number of times.
 */
class NotifyingCommandListener implements ICommandListener {

    private int mNumCalls = 0;
    private int mExpectedCalls = 0;

    NotifyingCommandListener() {
    }

    /**
     * Set the number of expected {@link #commandStarted} calls.
     */
    public void setExpectedCalls(int expectedCalls) {
        mExpectedCalls = expectedCalls;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void commandStarted() {
        mNumCalls++;
        if (mNumCalls >= mExpectedCalls) {
            synchronized (this) {
                notify();
            }
        }
    }

    /**
     * Block indefinitely for the expected number of {@link #commandStarted} calls.
     *
     * @throws InterruptedException
     */
    public synchronized void waitForExpectedCalls() throws InterruptedException {
        wait();
    }

    /**
     * Block for given time for the expected number of {@link #commandStarted} calls.
     *
     * @throws InterruptedException
     */
    public synchronized void waitForExpectedCalls(long timeout) throws InterruptedException {
        wait(timeout);
    }

    /**
     * Return the number of {@link #commandStarted} calls that occurred.
     */
    public int getNumCalls() {
        return mNumCalls;
    }

    /**
     * Return the number of expected calls.
     */
    public int getNumExpectedCalls() {
        return mExpectedCalls;
    }
}
