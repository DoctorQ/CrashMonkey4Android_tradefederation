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

import org.easymock.EasyMock;

import junit.framework.TestCase;

/**
 * Unit tests for {@link Console}.
 */
public class ConsoleTest extends TestCase {

    private ICommandScheduler mMockScheduler;
    private Console mConsole;

    /**
     * {@inheritDoc}
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mMockScheduler = EasyMock.createMock(ICommandScheduler.class);
        mConsole = new Console(mMockScheduler) {
            @Override
            void initLogging() {
                // do nothing
            }
        };
     }

    /**
     * Test running console with help specified
     */
    public void testRun_help() {
        EasyMock.replay(mMockScheduler);
        mConsole.run(new String[] {"--help"});
        // TODO: verify help is outputted
        // expect scheduler not to be started
        EasyMock.verify(mMockScheduler);
    }

    /**
     * Test running console with invalid args specified
     */
    public void testRun_invalidArgs() {
        EasyMock.replay(mMockScheduler);
        mConsole.run(new String[] {"--invalid"});
        // TODO: verify help is outputted
        // expect scheduler not to be started
        EasyMock.verify(mMockScheduler);
    }

    /**
     * Test normal console run.
     */
    public void testRun() throws InterruptedException {
        mMockScheduler.start();
        mMockScheduler.join();
        EasyMock.replay(mMockScheduler);
        mConsole.run(new String[] {});
        EasyMock.verify(mMockScheduler);
    }

}
