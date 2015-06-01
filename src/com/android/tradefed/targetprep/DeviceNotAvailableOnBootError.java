package com.android.tradefed.targetprep;

import com.android.tradefed.device.DeviceNotAvailableException;

public class DeviceNotAvailableOnBootError extends DeviceNotAvailableException {

	/**
	 * 
	 */
	private static final long serialVersionUID = -973300806736949511L;

	public DeviceNotAvailableOnBootError() {
	}

	public DeviceNotAvailableOnBootError(String msg) {
		super(msg);
	}

	public DeviceNotAvailableOnBootError(String msg, Throwable cause) {
		super(msg, cause);
	}

}
