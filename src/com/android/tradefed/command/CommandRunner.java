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

import com.android.ddmlib.DdmPreferences;
import com.android.ddmlib.Log;
import com.android.ddmlib.Log.LogLevel;
import com.android.tradefed.log.LogRegistry;

/**
 * An alternate TradeFederation entry point that will run command specified in command
 * line arguments and then quit.
 * <p/>
 * Intended for use with a debugger and other non-interactive modes of operation.
 * <p/>
 * Expected arguments: [commands options] <config to run>
 */
public class CommandRunner {
    private final ICommandScheduler mScheduler;

    CommandRunner() {
       mScheduler = new CommandScheduler();
    }

    /**
     * The main method to launch the console. Will keep running until shutdown command is issued.
     *
     * @param args
     */
    @SuppressWarnings("unchecked")
    public void run(String[] args) {
        initLogging();

        try {
            mScheduler.start();
            NotifyingCommandListener cmdListener = new NotifyingCommandListener();
            cmdListener.setExpectedCalls(1);
            if (mScheduler.addCommand(args, cmdListener)) {
                cmdListener.waitForExpectedCalls();
            }
            mScheduler.shutdown();
            mScheduler.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            cleanUp();
        }
    }

    /**
     * Initializes the ddmlib log.
     */
    void initLogging() {
        DdmPreferences.setLogLevel(LogLevel.VERBOSE.getStringValue());
        Log.setLogOutput(LogRegistry.getLogRegistry());
    }

    /**
     * Closes the logs and does any other necessary cleanup before the returning from the main
     * function.
     * <p/>
     * Exposed so unit tests can mock out.
     */
    void cleanUp() {
        LogRegistry.getLogRegistry().closeAndRemoveAllLogs();
    }

    public static void main(final String[] mainArgs) {
        CommandRunner console = new CommandRunner();
        console.run(mainArgs);
    }
}
