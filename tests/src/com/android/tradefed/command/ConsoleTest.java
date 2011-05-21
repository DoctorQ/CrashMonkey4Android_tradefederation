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

import com.android.tradefed.command.Console.CaptureList;
import com.android.tradefed.util.RegexTrie;

import org.easymock.EasyMock;

import java.util.Arrays;

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
        mMockScheduler = EasyMock.createStrictMock(ICommandScheduler.class);
        mConsole = new Console(mMockScheduler) {
            @Override
            void initLogging() {
                // do nothing
            }

            @Override
            void cleanUp() {
                // do nothing
            }
        };
     }

    /**
     * Test normal console run.
     */
    public void testRun() throws InterruptedException {
        mMockScheduler.start();
        mMockScheduler.join();
        EasyMock.replay(mMockScheduler);
        // This should force the console to drop into non-interactive mode
        mConsole.setTerminal(null);
        mConsole.run(new String[] {});
        EasyMock.verify(mMockScheduler);
    }

    /**
     * Make sure that "run command foo config.xml" works properly.
     */
    public void testRunCommand() throws Exception {
        String[] command = new String[] {"run", "command", "--arg", "value", "config.xml"};
        String[] expected = new String[] {"--arg", "value", "config.xml"};
        CaptureList captures = new CaptureList();
        RegexTrie<Runnable> trie = mConsole.getCommandTrie();

        mMockScheduler.addCommand(EasyMock.aryEq(expected));
        EasyMock.replay(mMockScheduler);

        Runnable runnable = trie.retrieve(captures, command);
        assertNotNull(String.format("Console didn't match input %s", Arrays.toString(command)),
                runnable);
        mConsole.executeCmdRunnable(runnable, captures);
        EasyMock.verify(mMockScheduler);
    }

    /**
     * Make sure that the "run foo config.xml" shortcut works properly.
     */
    public void testRunCommand_shortcut() throws Exception {
        String[] command = new String[] {"run", "--arg", "value", "config.xml"};
        String[] expected = new String[] {"--arg", "value", "config.xml"};
        CaptureList captures = new CaptureList();
        RegexTrie<Runnable> trie = mConsole.getCommandTrie();

        mMockScheduler.addCommand(EasyMock.aryEq(expected));
        EasyMock.replay(mMockScheduler);

        Runnable runnable = trie.retrieve(captures, command);
        assertNotNull(String.format("Console didn't match input %s", Arrays.toString(command)),
                runnable);
        mConsole.executeCmdRunnable(runnable, captures);
        EasyMock.verify(mMockScheduler);
    }

    /**
     * Make sure that the command "run command command foo config.xml" properly considers the second
     * "command" to be the first token of the command to be executed.
     */
    public void testRunCommand_startsWithCommand() throws Exception {
        String[] command = new String[] {"run", "command", "command", "--arg", "value", "config.xml"};
        String[] expected = new String[] {"command", "--arg", "value", "config.xml"};
        CaptureList captures = new CaptureList();
        RegexTrie<Runnable> trie = mConsole.getCommandTrie();

        mMockScheduler.addCommand(EasyMock.aryEq(expected));
        EasyMock.replay(mMockScheduler);

        Runnable runnable = trie.retrieve(captures, command);
        assertNotNull(String.format("Console didn't match input %s", Arrays.toString(command)),
                runnable);
        mConsole.executeCmdRunnable(runnable, captures);
        EasyMock.verify(mMockScheduler);
    }
}

