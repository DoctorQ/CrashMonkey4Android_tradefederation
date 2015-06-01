package com.android.tradefed.device;

import java.util.HashMap;
import java.util.Map;

import com.android.tradefed.config.Option;

public class DeviceLabelMapper implements IDeviceLabelMapper {

	@Option(name = "device-label", description="device label")
	private Map<String,String> mSerialToDeviceLabel = new HashMap<String, String>();
	
	public DeviceLabelMapper() {
	}

	@Override
	public String getDeviceLabel(String serial) {
		if(mSerialToDeviceLabel.containsKey(serial)) {
			return mSerialToDeviceLabel.get(serial);
		}
		return null;
	}

}
