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
package com.android.framework.tests;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.StreamUtil;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class BandwidthUtils {
    private static final String IFACE_STAT_FOLDER = "/proc/net/xt_qtaguid/iface_stat";
    private static final String IFACE_STAT_ACTIVE = "/proc/net/xt_qtaguid/iface_stat/%s/active";
    private static final String RX_BYTES = "rx_bytes";
    private static final String TX_BYTES = "tx_bytes";
    private static final String RX_PACKETS = "rx_packets";
    private static final String TX_PACKETS = "tx_packets";
    private Map<String, Stat> mIfaceStats = new HashMap<String, Stat>();
    private Map<String, Stat> mUidStats = new HashMap<String, Stat>();
    private Map<String, Stat> mIfaceSnapStats = new HashMap<String, Stat>();
    private Map<String, Stat> mIfaceDevStats = new HashMap<String, Stat>();
    private Map<String, Stat> mStatsDiff = new HashMap<String, Stat>();
    private Set<String> mIfaceKnown = new HashSet<String>();
    private Set<String> mIfaceActive = new HashSet<String>();
    ITestDevice mTestDevice = null;

    BandwidthUtils(ITestDevice device) {
        mTestDevice = device;
    }

    /**
     * Simple container class used to store network stats.
     */
    class Stat {
        private float mRxBytes = 0;
        private float mRxPackets = 0;
        private float mTxBytes = 0;
        private float mTxPackets = 0;

        /**
         * Append stats to current record.
         * @param rxBytes
         * @param rxPackets
         * @param txBytes
         * @param txPackets
         */
        void record(float rxBytes, float rxPackets, float txBytes, float txPackets) {
            mRxBytes += rxBytes;
            mRxPackets += rxPackets;
            mTxBytes += txBytes;
            mTxPackets += txPackets;
        }
    }

    /**
     * Calculates the percent difference between the qtaguid and iface stats.
     * @return Map of all stats and computed differences
     * @throws DeviceNotAvailableException
     */
    Map<String, String> calculateStats() throws DeviceNotAvailableException {
        Map<String, String> result = new HashMap<String, String>();
        parseIfaceStats();
        mIfaceSnapStats.putAll(mIfaceStats);
        addNetDevStats();
        parseUidStats();
        calculateStatDifferences();
        Set<String> uid = mUidStats.keySet();
        addToStringMap("_snap_", mIfaceSnapStats, uid, result);
        addToStringMap("_dev_", mIfaceDevStats, uid, result);
        addToStringMap("_IFACE_", mIfaceStats, uid, result);
        addToStringMap("_UID_", mUidStats, uid, result);
        addToStringMap("_%_", mStatsDiff, uid, result);
        return result;
    }

    /**
     * Calculate the percent difference between qtaguid and iface stats.
     */
    private void calculateStatDifferences() {
        for (String iface: mUidStats.keySet()) {
            if (!mIfaceStats.containsKey(iface)) {
                CLog.w("Missing %s data in iface stats", iface);
                continue;
            }
            Stat ifaceStat = mIfaceStats.get(iface);
            Stat uidStat = mUidStats.get(iface);
            computeStatError(iface, ifaceStat, uidStat);
        }
    }

    /**
     * Parses the uid stats from /proc/net/xt_qtaguid/stats
     * @throws DeviceNotAvailableException
     */
    private void parseUidStats() throws DeviceNotAvailableException {
        File statsFile = mTestDevice.pullFile("/proc/net/xt_qtaguid/stats");
        FileInputStream fStream = null;
        try {
            fStream = new FileInputStream(statsFile);
            String tmp = StreamUtil.getStringFromStream(fStream);
            String[] lines = tmp.split("\n");
            for (String line : lines) {
                if (line.contains("idx")) {
                    continue;
                }
                String[] parts = line.trim().split(" ");
                String iface = parts[1];
                String tag = parts[2];
                int rb = Integer.parseInt(parts[5]);
                int rp = Integer.parseInt(parts[6]);
                int tb = Integer.parseInt(parts[7]);
                int tp = Integer.parseInt(parts[8]);
                if ("0x0".equals(tag)) {
                    recordStat(mUidStats, iface, rb, rp, tb, tp);
                }
            }
        } catch (IOException e) {
            CLog.d("Failed to read file %s: %s", statsFile.toString(), e.getMessage());
        } finally {
            StreamUtil.closeStream(fStream);
        }
    }

    /**
     * Add stats, if the iface is currently active or it is an unknown iface found in /proc/net/dev.
     * @throws DeviceNotAvailableException
     */
    private void addNetDevStats() throws DeviceNotAvailableException {
        File file = mTestDevice.pullFile("/proc/net/dev");
        FileInputStream fStream = null;
        try {
            fStream = new FileInputStream(file);
            String tmp = StreamUtil.getStringFromStream(fStream);
            String[] lines = tmp.split("\n");
            for (String line : lines) {
                if (line.contains("Receive") || line.contains("multicast")) {
                    continue;
                }
                String[] parts = line.trim().split("[ :]+");
                String iface = parts[0].replace(":", "").trim();
                int rb = Integer.parseInt(parts[1]);
                int rp = Integer.parseInt(parts[2]);
                int tb = Integer.parseInt(parts[9]);
                int tp = Integer.parseInt(parts[10]);
                recordStat(mIfaceDevStats, iface, rb, rp, tb, tp);
                if (mIfaceActive.contains(iface) || !mIfaceKnown.contains(iface)) {
                    recordStat(mIfaceStats, iface, rb, rp, tb, tp);
                }
            }
        } catch (IOException e) {
            CLog.d("Failed to read file %s: %s", file.toString(), e.getMessage());
        } finally {
            StreamUtil.closeStream(fStream);
        }
    }

    /**
     * Parses the iface stats from /proc/net/xt_qtaguid/iface_stat/&lt;iface&gt;/...
     * @throws DeviceNotAvailableException
     */
    private void parseIfaceStats() throws DeviceNotAvailableException {
        String[] ifaces = listIface(mTestDevice);
        for (String iface : ifaces) {
            iface = iface.trim();
            if (iface.isEmpty()) {
                continue;
            }
            // Determine if a given iface is active or not.
            String activeFile = String.format(IFACE_STAT_ACTIVE, iface);
            String active = readFirstLineInFile(mTestDevice, activeFile);
            if (active != null) {
                if (active.contains("1")) {
                    mIfaceKnown.add(iface);
                    mIfaceActive.add(iface);
                } else if (active.contains("0")) {
                    mIfaceKnown.add(iface);
                } else {
                    CLog.w("Invalid value found in %s: %s", activeFile, active);
                    continue;
                }
            } else {
                CLog.w("Failed to find active file %s", activeFile);
                continue;
            }
            String rxBytesFile = String.format("%s/%s/%s", IFACE_STAT_FOLDER, iface, RX_BYTES);
            String txBytesFile = String.format("%s/%s/%s", IFACE_STAT_FOLDER, iface, TX_BYTES);
            String rxPacketsFile = String.format("%s/%s/%s", IFACE_STAT_FOLDER, iface, RX_PACKETS);
            String txPacketsFile = String.format("%s/%s/%s", IFACE_STAT_FOLDER, iface, TX_PACKETS);
            float rb = readFloatFromFile(mTestDevice, rxBytesFile);
            float tb = readFloatFromFile(mTestDevice, txBytesFile);
            float rp = readFloatFromFile(mTestDevice, rxPacketsFile);
            float tp = readFloatFromFile(mTestDevice, txPacketsFile);
            recordStat(mIfaceStats, iface, rb, rp, tb, tp);
        }
    }

    /**
     * Add a given stats map to the final stat map
     * @param label {@link String} of the label to distinguish these stats
     * @param statMap to add to the final result map
     * @param filter the iface that we want to filter on, if null we output everything.
     * @param totalResult the final result map
     */
    private void addToStringMap(String label, Map<String, Stat> statMap, Set<String> filter,
            Map<String, String> totalResult) {
        for (Entry<String, Stat> entry : statMap.entrySet()) {

            String iface = entry.getKey();
            Stat stat = entry.getValue();
            if (filter != null && !filter.contains(iface)) {
                continue;
            }
            totalResult.put(iface + label + RX_BYTES, Float.toString(stat.mRxBytes));
            totalResult.put(iface + label + RX_PACKETS, Float.toString(stat.mRxPackets));
            totalResult.put(iface + label + TX_BYTES, Float.toString(stat.mTxBytes));
            totalResult.put(iface + label + TX_PACKETS, Float.toString(stat.mTxPackets));
        }
    }

    /**
     * Compute percent difference between a and b.
     * @param a
     * @param b
     * @return % difference of a and b.
     */
    private float computePercentDifference(float a, float b) {
        if (a == 0) {
            CLog.d("Invalid value for a: %f", a);
            return Float.MIN_VALUE;
        }
        return ( a - b) / a * 100;
    }

    /**
     * Compute percent difference for a given iface and record it into the difference map.
     * @param iface {@link String} label of the iface to compute
     * @param a {@link Stat}
     * @param b {@link Stat}
     */
    private void computeStatError(String iface, Stat a, Stat b) {
        float rbDiff = computePercentDifference(a.mRxBytes, b.mRxBytes);
        float rpDiff = computePercentDifference(a.mRxPackets, b.mRxPackets);
        float tbDiff = computePercentDifference(a.mTxBytes, b.mTxBytes);
        float tpDiff = computePercentDifference(a.mTxPackets, b.mTxPackets);
        recordStat(mStatsDiff, iface, rbDiff, rpDiff, tbDiff, tpDiff);
    }

    /**
     * Record/Append a given stat to a given stat map
     * @param statsMap to record to
     * @param iface {@link String} label of the iface
     * @param rxBytes
     * @param rxPackets
     * @param txBytes
     * @param txPackets
     */
    private void recordStat(Map<String, Stat> statsMap, String iface, float rxBytes,
            float rxPackets, float txBytes, float txPackets) {
        Stat stat = null;
        if (statsMap.containsKey(iface)) {
            stat = statsMap.get(iface);
        } else {
            stat = new Stat();
        }
        stat.record(rxBytes, rxPackets, txBytes, txPackets);
        statsMap.put(iface, stat);
    }

    /**
     * Get all the ifaces on device
     * @param device {@link ITestDevice}
     * @return String[] of ifaces found
     * @throws DeviceNotAvailableException
     */
    private String[] listIface(ITestDevice device) throws DeviceNotAvailableException {
        String command = "ls /proc/net/xt_qtaguid/iface_stat/";
        String result = device.executeShellCommand(command);
        return result.split("\n");
    }

    /**
     * Read the first line of a given file and parse it as a float.
     * @param device {@link ITestDevice} to read the file from
     * @param filename {@link String} remote path to read
     * @return float value of the first line read or 0 if not found
     * @throws DeviceNotAvailableException
     */
    private float readFloatFromFile(ITestDevice device, String filename)
            throws DeviceNotAvailableException {
        String line = readFirstLineInFile(device, filename);
        if (line != null) {
            try {
                float temp = Float.parseFloat(line);
                return temp;
            } catch (NumberFormatException e) {
                CLog.d("Failed to parse %s from file %s: %s", line, filename, e.getMessage());
            }
        }
        CLog.w("Did not find a line to parse.");
        return 0;
    }

    /**
     * Read the first line of a given file on the device.
     * @param device {@link ITestDevice} to read the file from
     * @param filename {@link String} remote path to read
     * @return {@link String} the first line read or null if not found
     * @throws DeviceNotAvailableException
     */
    private String readFirstLineInFile(ITestDevice device, String filename)
            throws DeviceNotAvailableException {
        File file = device.pullFile(filename);
        FileInputStream fStream = null;
        try {
            fStream = new FileInputStream(file);
            return StreamUtil.getStringFromStream(fStream);
        } catch (IOException e) {
            CLog.d("Failed to read file %s: %s", file.toString(), e.getMessage());
        } finally {
            StreamUtil.closeStream(fStream);
        }
        return null;
    }
}
