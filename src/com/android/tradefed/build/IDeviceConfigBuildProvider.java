package com.android.tradefed.build;

import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;

public interface IDeviceConfigBuildProvider extends IBuildProvider {
	
    public IBuildInfo getBuild(ITestDevice device, IConfiguration config) throws BuildRetrievalError, 
    		DeviceNotAvailableException;
}
