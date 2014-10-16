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

import com.android.tradefed.log.LogUtil.CLog;

import java.util.HashMap;
import java.util.Map;

/**
 * Simple container class used to store network Stats.
 */
public class BandwidthStats {
    private static final String RX_BYTES = "rx_bytes";
    private static final String TX_BYTES = "tx_bytes";
    private static final String RX_PACKETS = "rx_packets";
    private static final String TX_PACKETS = "tx_packets";
    private float mRxBytes = 0;
    private float mRxPackets = 0;
    private float mTxBytes = 0;
    private float mTxPackets = 0;

    public BandwidthStats (float rxBytes, float rxPackets, float txBytes, float txPackets) {
        mRxBytes = rxBytes;
        mRxPackets = rxPackets;
        mTxBytes = txBytes;
        mTxPackets = txPackets;
    }

    public BandwidthStats() {
    }

    /**
     * Append stats to current record.
     * @param bwStats {@link BandwidthStats} to append to current instance.
     */
    public void record(BandwidthStats bwStats) {
        mRxBytes += bwStats.getRxBytes();
        mRxPackets += bwStats.getRxPackets();
        mTxBytes += bwStats.getTxBytes();
        mTxPackets += bwStats.getTxPackets();
    }

    /**
     * Calculate the percent difference of another {@link BandwidthStats} and the current instance
     * @param bwStats {@link BandwidthStats}
     * @return {@link BandwidthStats} difference stats
     */
    public BandwidthStats calculatePercentDifference(BandwidthStats bwStats) {
        float rxBytesDiff = computePercentDifference(this.mRxBytes, bwStats.getRxBytes());
        float rxPacketsDiff = computePercentDifference(this.mRxPackets, bwStats.getRxPackets());
        float txBytesDiff = computePercentDifference(this.mTxBytes, bwStats.getTxBytes());
        float txPacketsDiff = computePercentDifference(this.mTxPackets, bwStats.getTxPackets());
        return new BandwidthStats(rxBytesDiff, rxPacketsDiff, txBytesDiff, txPacketsDiff);
    }

    public Map<String, String> formatToStringMap(String label) {
        Map<String, String> map = new HashMap<String, String>();
        map.put(label + RX_BYTES, Float.toString(mRxBytes));
        map.put(label + RX_PACKETS, Float.toString(mRxPackets));
        map.put(label + TX_BYTES, Float.toString(mTxBytes));
        map.put(label + TX_PACKETS, Float.toString(mTxPackets));
        return map;
    }

    /**
     * Compute percent difference between a and b.
     * @param a
     * @param b
     * @return % difference of a and b.
     */
    float computePercentDifference(float a, float b) {
        if (a == b) {
            return 0;
        }
        if (a == 0) {
            CLog.d("Invalid value for a: %f", a);
            return Float.MIN_VALUE;
        }
        return ( a - b) / a * 100;
    }

    public float getRxBytes() {
        return mRxBytes;
    }

    public void setRxBytes(float rxBytes) {
        this.mRxBytes = rxBytes;
    }

    public float getRxPackets() {
        return mRxPackets;
    }

    public void setRxPackets(float rxPackets) {
        this.mRxPackets = rxPackets;
    }

    public float getTxBytes() {
        return mTxBytes;
    }

    public void setTxBytes(float txBytes) {
        this.mTxBytes = txBytes;
    }

    public float getTxPackets() {
        return mTxPackets;
    }

    public void setTxPackets(float txPackets) {
        this.mTxPackets = txPackets;
    }
}
