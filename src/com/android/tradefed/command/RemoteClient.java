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

import com.android.tradefed.util.ArrayUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;

/**
 * Class for sending remote commands to another TF process via sockets.
 */
public class RemoteClient {

    private final Socket mSocket;
    private final PrintWriter mWriter;
    private final BufferedReader mReader;

    /**
     * @param port
     * @throws IOException
     * @throws UnknownHostException
     */
    RemoteClient(int port) throws UnknownHostException, IOException {
        String hostName = InetAddress.getLocalHost().getHostName();
        mSocket = new Socket(hostName, port);
        mWriter = new PrintWriter(mSocket.getOutputStream(), true);
        mReader = new BufferedReader(new InputStreamReader(mSocket.getInputStream()));
    }

    private synchronized boolean sendCommand(String... cmd) throws IOException {
        // TODO: use a more standard data protocol - such as Json
        mWriter.println(ArrayUtil.join(RemoteManager.DELIM, (Object[])cmd));
        String response = mReader.readLine();
        return response != null && Boolean.parseBoolean(response);
    }

    public static RemoteClient connect(int port) throws UnknownHostException, IOException {
        return new RemoteClient(port);
    }

    /**
     * Send a 'add this device to global ignore filter' command
     * @param serial
     * @throws IOException
     */
    public boolean sendFilterDevice(String serial) throws IOException {
        return sendCommand(RemoteManager.FILTER, serial);
    }

    /**
     * Send a 'remove this device from global ignore filter' command
     * @param serial
     * @throws IOException
     */
    public boolean sendUnfilterDevice(String serial) throws IOException {
        return sendCommand(RemoteManager.UNFILTER, serial);
    }

    /**
     * Send a 'add command' command.
     *
     * @param commandArgs
     */
    public boolean sendAddCommand(long totalTime, String... commandArgs) throws IOException {
        String[] fullList = ArrayUtil.buildArray(new String[] {RemoteManager.ADD_COMMAND,
                Long.toString(totalTime)}, commandArgs);
        return sendCommand(fullList);
    }

    /**
     * Send a 'close connection' command
     *
     * @throws IOException
     */
    public boolean sendClose() throws IOException {
        return sendCommand(RemoteManager.CLOSE);
    }

    /**
     * Close the connection to the {@link RemoteManager}.
     */
    public synchronized void close() {
        if (mSocket != null) {
             try {
                mSocket.close();
            } catch (IOException e) {
                // ignore
            }
        }
        if (mWriter != null) {
            mWriter.close();
        }
    }
}

