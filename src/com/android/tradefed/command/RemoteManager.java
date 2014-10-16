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

import com.android.ddmlib.Log;
import com.android.ddmlib.Log.LogLevel;
import com.android.tradefed.device.IDeviceManager;
import com.android.tradefed.device.IDeviceManager.FreeDeviceState;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.ArrayUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.Map;

/**
 * Class that receives remote commands to add and remove devices from use via a socket.
 * <p/>
 * Currently accepts only one remote connection at one time, and processes incoming commands
 * serially.
 * <p/>
 * Usage:
 * <pre>
 * RemoteManager r = new RemoteManager(deviceMgr, scheduler);
 * r.start();
 * int port = r.getPort();
 * ... inform client of port to use. Shuts down when instructed by client or on #cancel()
 * </pre>
 */
public class RemoteManager extends Thread {

    // constants that define wire protocol between RemoteClient and RemoteManager
    static final String DELIM = ";";
    static final String FILTER = "filter";
    static final String UNFILTER = "unfilter";
    static final String ALL_DEVICES = "*";
    static final String CLOSE = "close";
    static final String ADD_COMMAND = "add_command";

    private ServerSocket mServerSocket = null;
    private boolean mCancel = false;
    private final IDeviceManager mDeviceManager;
    private final ICommandScheduler mScheduler;
    private Map<String, ITestDevice> mFilteredDeviceMap = new Hashtable<String, ITestDevice>();

    /**
     * Creates a {@link RemoteManager}.
     *
     * @param manager the {@link IDeviceManager} to use to allocate and free devices.
     * @param scheduler the {@link ICommandScheduler} to use to schedule commands.
     */
    public RemoteManager(IDeviceManager manager, ICommandScheduler scheduler) {
        mDeviceManager = manager;
        mScheduler = scheduler;
    }

    /**
     * The main thread body of the remote manager.
     * <p/>
     * Creates a server socket, and waits for client connections.
     */
    @Override
    public void run() {
        synchronized (this) {
            try {
                mServerSocket = new ServerSocket(0);
            } catch (IOException e) {
                CLog.e("Failed to open server socket: %s", e);
                return;
            } finally {
                // notify any listeners that the socket has been created
                notifyAll();
            }
        }
        try {
            processClientConnections(mServerSocket);
        } finally {
            freeAllDevices();
            closeSocket(mServerSocket);
        }
    }

    /**
     * Gets the socket port the remote manager is listening on, blocking for a short time if
     * necessary.
     * <p/>
     * {@link #start()} should be called before this method.
     * @return
     */
    public synchronized int getPort() {
        if (mServerSocket == null) {
            try {
                wait(10*1000);
            } catch (InterruptedException e) {
                // ignore
            }
        }
        if (mServerSocket == null) {
            return -1;
        }
        return mServerSocket.getLocalPort();
    }

    private void processClientConnections(ServerSocket serverSocket) {
        while (!mCancel) {
            Socket clientSocket = null;
            BufferedReader in = null;
            PrintWriter out = null;
            try {
                clientSocket = serverSocket.accept();
                in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                out = new PrintWriter(clientSocket.getOutputStream(), true);
                processClientCommands(in, out);
            } catch (IOException e) {
                CLog.e("Failed to accept connection: %s", e);
            } finally {
                closeReader(in);
                closeWriter(out);
                closeSocket(clientSocket);
            }
        }
    }

    private void processClientCommands(BufferedReader in, PrintWriter out) throws IOException {
        String line = null;
        while ((line = in.readLine()) != null && !mCancel) {
            boolean result = false;
            String[] commandSegments = line.split(DELIM);
            String cmdType = commandSegments[0];
            if (FILTER.equals(cmdType)) {
                result = processFilterCommand(commandSegments);
            } else if (UNFILTER.equals(cmdType)) {
                result = processUnfilterCommand(commandSegments);
            } else if (CLOSE.equals(cmdType)) {
                cancel();
                result = true;
            } else if (ADD_COMMAND.equals(cmdType)) {
                result = processAddCommand(commandSegments);
            }
            sendAck(result, out);
        }
    }

    private boolean processFilterCommand(final String[] commandSegments) {
        if (commandSegments.length < 2) {
            CLog.e("Invalid command received: %s", ArrayUtil.join(" ", (Object[])commandSegments));
            return false;
        }
        final String serial = commandSegments[1];
        ITestDevice allocatedDevice = mDeviceManager.forceAllocateDevice(serial);
        if (allocatedDevice != null) {
            Log.logAndDisplay(LogLevel.INFO, "RemoteManager",
                    String.format("Allocating device %s that is still in use by remote tradefed",
                            serial));
            mFilteredDeviceMap.put(serial, allocatedDevice);
            return true;
        } else {
            CLog.e("Failed to allocate remote device %s", serial);
            return false;
        }
    }

    private boolean processUnfilterCommand(final String[] commandSegments) {
        if (commandSegments.length < 2) {
            CLog.e("Invalid command received: %s", ArrayUtil.join(" ", (Object[])commandSegments));
            return false;
        }
        // TODO: consider making this synchronous, and sending ack back to client once allocated
        final String serial = commandSegments[1];
        if (ALL_DEVICES.equals(serial)) {
            freeAllDevices();
            return true;
        } else {
            ITestDevice d = mFilteredDeviceMap.remove(serial);
            if (d != null) {
                Log.logAndDisplay(LogLevel.INFO, "RemoteManager",
                        String.format("Freeing device %s no longer in use by remote tradefed",
                                serial));
                mDeviceManager.freeDevice(d, FreeDeviceState.AVAILABLE);
                return true;
            } else {
                CLog.w("Could not find device to free %s", serial);
            }
        }
        return false;
    }

    private boolean processAddCommand(final String[] commandSegments) {
        if (commandSegments.length < 3) {
            CLog.e("Invalid command received: %s", ArrayUtil.join(" ", (Object[])commandSegments));
            return false;
        }
        long totalTime = Long.parseLong(commandSegments[1]);
        String[] cmdArgs = Arrays.copyOfRange(commandSegments, 2, commandSegments.length);
        Log.logAndDisplay(LogLevel.INFO, "RemoteManager",
                String.format("Adding command '%s'", ArrayUtil.join(" ", (Object[])cmdArgs)));
        return mScheduler.addCommand(cmdArgs, totalTime);
    }

    private void freeAllDevices() {
        for (ITestDevice d : mFilteredDeviceMap.values()) {
            Log.logAndDisplay(LogLevel.INFO, "RemoteManager",
                    String.format("Freeing device %s no longer in use by remote tradefed",
                            d.getSerialNumber()));

            mDeviceManager.freeDevice(d, FreeDeviceState.AVAILABLE);
        }
        mFilteredDeviceMap.clear();
    }

    private void sendAck(boolean result, PrintWriter out) {
        out.println(result);
    }

    /**
     * Cancel the remote manager.
     */
    public synchronized void cancel() {
        if (!mCancel) {
            mCancel  = true;
            Log.logAndDisplay(LogLevel.INFO, "RemoteManager", "Closing remote manager");
        }
    }

    private void closeSocket(ServerSocket serverSocket) {
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                // ignore
            }
        }
    }

    private void closeSocket(Socket clientSocket) {
        if (clientSocket != null) {
            try {
                clientSocket.close();
            } catch (IOException e) {
                // ignore
                e.printStackTrace();
            }
        }
    }

    private void closeReader(BufferedReader in) {
        if (in != null) {
            try {
                in.close();
            } catch (IOException e) {
                // ignore
            }
        }
    }

    private void closeWriter(PrintWriter out) {
        if (out != null) {
            out.close();
        }
    }

    /**
     * @return <code>true</code> if a cancel has been requested
     */
    public boolean isCanceled() {
        return mCancel;
    }
}
