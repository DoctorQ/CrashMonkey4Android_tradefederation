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
package com.android.tradefed.device;

import com.android.ddmlib.MultiLineReceiver;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Helper class for manipulating wifi services on device.
 */
public class WifiHelper implements IWifiHelper {

	private static final String NULL_IP_ADDR = "0.0.0.0";
	private static final String INSTRUMENTATION_CLASS = ".WifiUtil";
	public static final String INSTRUMENTATION_PKG = "com.android.tradefed.utils.wifi";
	static final String FULL_INSTRUMENTATION_NAME = String.format("%s/%s",
			INSTRUMENTATION_PKG, INSTRUMENTATION_CLASS);

	static final String CHECK_INSTRUMENTATION_CMD = String.format(
			"pm list instrumentation %s", INSTRUMENTATION_PKG);

	private static final String WIFIUTIL_APK_NAME = "WifiUtil.apk";

	/** the default time in ms to wait for a wifi state */
	private static final long DEFAULT_WIFI_STATE_TIMEOUT = 30 * 1000;

	private final ITestDevice mDevice;

	public WifiHelper(ITestDevice device) throws TargetSetupError,
			DeviceNotAvailableException {
		mDevice = device;
		ensureDeviceSetup();
	}

	/**
	 * Get the {@link RunUtil} instance to use.
	 * <p/>
	 * Exposed for unit testing.
	 */
	IRunUtil getRunUtil() {
		return RunUtil.getDefault();
	}

	void ensureDeviceSetup() throws TargetSetupError,
			DeviceNotAvailableException {
		final String inst = mDevice
				.executeShellCommand(CHECK_INSTRUMENTATION_CMD);
		if ((inst != null) && inst.contains(FULL_INSTRUMENTATION_NAME)) {
			// Good to go
			return;
		} else {
			// Attempt to install utility
			File apkTempFile = null;
			try {
				apkTempFile = extractWifiUtilApk();

				final String result = mDevice
						.installPackage(apkTempFile, false);
				if (result == null) {
					// Installed successfully; good to go.
					return;
				} else {
					throw new TargetSetupError(String.format(
							"Unable to install WifiUtil utility: %s", result));
				}
			} catch (IOException e) {
				throw new TargetSetupError(
						String.format("Failed to unpack WifiUtil utility: %s",
								e.getMessage()));
			} finally {
				FileUtil.deleteFile(apkTempFile);
			}
		}
	}

	/**
	 * Helper method to extract the wifi util apk from the classpath
	 */
	public static File extractWifiUtilApk() throws IOException {
		File apkTempFile;
		apkTempFile = FileUtil.createTempFile(WIFIUTIL_APK_NAME, ".apk");
		InputStream apkStream = WifiHelper.class.getResourceAsStream(String
				.format("/apks/wifiutil/%s", WIFIUTIL_APK_NAME));
		FileUtil.writeToFile(apkStream, apkTempFile);
		return apkTempFile;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean enableWifi() throws DeviceNotAvailableException {
		return asBool(runWifiUtil("enableWifi"));
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean disableWifi() throws DeviceNotAvailableException {
		return asBool(runWifiUtil("disableWifi"));
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean waitForWifiState(WifiState... expectedStates)
			throws DeviceNotAvailableException {
		return waitForWifiState(DEFAULT_WIFI_STATE_TIMEOUT, expectedStates);
	}

	/**
	 * Waits the given time until one of the expected wifi states occurs.
	 *
	 * @param expectedStates
	 *            one or more wifi states to expect
	 * @param timeout
	 *            max time in ms to wait
	 * @return <code>true</code> if the one of the expected states occurred.
	 *         <code>false</code> if none of the states occurred before timeout
	 *         is reached
	 * @throws DeviceNotAvailableException
	 */
	boolean waitForWifiState(long timeout, WifiState... expectedStates)
			throws DeviceNotAvailableException {
		long startTime = System.currentTimeMillis();
		while (System.currentTimeMillis() < (startTime + timeout)) {
			String state = runWifiUtil("getSupplicantState");
			for (WifiState expectedState : expectedStates) {
				if (expectedState.name().equals(state)) {
					return true;
				}
			}
			getRunUtil().sleep(getPollTime());
		}
		return false;
	}

	/**
	 * Gets the time to sleep between poll attempts
	 */
	long getPollTime() {
		return 1 * 1000;
	}

	/**
	 * Remove the network identified by an integer network id.
	 *
	 * @param networkId
	 *            the network id identifying its profile in wpa_supplicant
	 *            configuration
	 * @throws DeviceNotAvailableException
	 */
	void removeNetwork(int networkId) throws DeviceNotAvailableException {
		runWifiUtil("removeNetwork", "id", Integer.toString(networkId));
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean addOpenNetwork(String ssid)
			throws DeviceNotAvailableException {
		int id = asInt(runWifiUtil("addOpenNetwork", "ssid", ssid));
		if (id < 0) {
			return false;
		}
		if (!asBool(runWifiUtil("associateNetwork", "id", Integer.toString(id)))) {
			return false;
		}
		if (!asBool(runWifiUtil("saveConfiguration"))) {
			return false;
		}
		return true;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean addWpaPskNetwork(String ssid, String psk)
			throws DeviceNotAvailableException {
		int id = asInt(runWifiUtil("addWpaPskNetwork", "ssid", ssid, "psk", psk));
		if (id < 0) {
			return false;
		}
		if (!asBool(runWifiUtil("associateNetwork", "id", Integer.toString(id)))) {
			return false;
		}
		if (!asBool(runWifiUtil("saveConfiguration"))) {
			return false;
		}
		return true;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean waitForIp(long timeout) throws DeviceNotAvailableException {
		long startTime = System.currentTimeMillis();

		while (System.currentTimeMillis() < (startTime + timeout)) {
			if (hasValidIp()) {
				return true;
			}
			getRunUtil().sleep(getPollTime());
		}
		return false;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean hasValidIp() throws DeviceNotAvailableException {
		final String ip = getIpAddress();
		return ip != null && !ip.isEmpty() && !NULL_IP_ADDR.equals(ip);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getIpAddress() throws DeviceNotAvailableException {
		return runWifiUtil("getIpAddress");
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getSSID() throws DeviceNotAvailableException {
		return runWifiUtil("getSSID");
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void removeAllNetworks() throws DeviceNotAvailableException {
		runWifiUtil("removeAllNetworks");
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isWifiEnabled() throws DeviceNotAvailableException {
		return asBool(runWifiUtil("isWifiEnabled"));
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean waitForWifiEnabled() throws DeviceNotAvailableException {
		return waitForWifiEnabled(DEFAULT_WIFI_STATE_TIMEOUT);
	}

	@Override
	public boolean waitForWifiEnabled(long timeout)
			throws DeviceNotAvailableException {
		long startTime = System.currentTimeMillis();

		while (System.currentTimeMillis() < (startTime + timeout)) {
			if (isWifiEnabled()) {
				return true;
			}
			getRunUtil().sleep(getPollTime());
		}
		return false;
	}

	/**
	 * Run a WifiUtil command and return the result
	 *
	 * @param method
	 *            the WifiUtil method to call
	 * @param args
	 *            a flat list of [arg-name, value] pairs to pass
	 * @return The value of the result field in the output, or <code>null</code>
	 *         if result could not be parsed
	 */
	private String runWifiUtil(String method, String... args)
			throws DeviceNotAvailableException {
		final String cmd = buildWifiUtilCmd(method, args);
		CLog.d(String.format("向wifi app发送命令 %s", cmd));
		WifiUtilOutput parser = new WifiUtilOutput();
		mDevice.executeShellCommand(cmd, parser);
		return parser.getResult();
	}

	/**
	 * Build and return a WifiUtil command for the specified method and args
	 *
	 * @param method
	 *            the WifiUtil method to call
	 * @param args
	 *            a flat list of [arg-name, value] pairs to pass
	 * @return the command to be executed on the device shell
	 */
	static String buildWifiUtilCmd(String method, String... args) {
		Map<String, String> argMap = new HashMap<String, String>();
		argMap.put("method", method);
		if ((args.length & 0x1) == 0x1) {
			throw new IllegalArgumentException(
					"args should have even length, consisting of key and value pairs");
		}
		for (int i = 0; i < args.length; i += 2) {
			argMap.put(args[i], args[i + 1]);
		}
		return buildWifiUtilCmdFromMap(argMap);
	}

	/**
	 * Build and return a WifiUtil command for the specified args
	 *
	 * @param args
	 *            A Map of (arg-name, value) pairs to pass as "-e" arguments to
	 *            the `am` command
	 * @return the commadn to be executed on the device shell
	 */
	static String buildWifiUtilCmdFromMap(Map<String, String> args) {
		StringBuilder sb = new StringBuilder("am instrument");

		for (Map.Entry<String, String> arg : args.entrySet()) {
			sb.append(" -e ");
			sb.append(arg.getKey());
			sb.append(" ");
			sb.append(quote(arg.getValue()));
		}

		sb.append(" -w ");
		sb.append(INSTRUMENTATION_PKG);
		sb.append("/");
		sb.append(INSTRUMENTATION_CLASS);
		
		return sb.toString();
	}

	/**
	 * Helper function to convert a String to an Integer
	 */
	private static int asInt(String str) {
		if (str == null) {
			return -1;
		}
		try {
			return Integer.parseInt(str);
		} catch (NumberFormatException e) {
			return -1;
		}
	}

	/**
	 * Helper function to convert a String to a boolean. Maps "true" to true,
	 * and everything else to false.
	 */
	private static boolean asBool(String str) {
		return "true".equals(str);
	}

	/**
	 * Helper function to wrap the specified String in double-quotes to prevent
	 * shell interpretation
	 */
	private static String quote(String str) {
		return String.format("\"%s\"", str);
	}

	/**
	 * Processes the output of a WifiUtil invocation
	 */
	private static class WifiUtilOutput extends MultiLineReceiver {
		private static final Pattern RESULT_PAT = Pattern
				.compile("INSTRUMENTATION_RESULT: result=(.*)");

		private String mResult = null;

		/**
		 * {@inheritDoc}
		 */
		@Override
		public void processNewLines(String[] lines) {
			for (String line : lines) {
				Matcher resultMatcher = RESULT_PAT.matcher(line);
				if (resultMatcher.matches()) {
					mResult = resultMatcher.group(1);
				}
			}
		}

		/**
		 * Return the result flag parsed from instrmentation output.
		 * <code>null</code> is returned if result output was not present.
		 */
		String getResult() {
			return mResult;
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public boolean isCancelled() {
			return false;
		}
	}
}
