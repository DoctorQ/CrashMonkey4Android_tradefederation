/*
 * Copyright (C) 2012 The Android Open Source Project
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
package com.android.tradefed.build;

import junit.framework.TestCase;

import org.easymock.EasyMock;

/**
 * Unit tests for {@link KernelBuildInfo}.
 */
public class KernelDeviceBuildInfoTest extends TestCase {

    private IKernelDeviceBuildInfo mBuildInfo;
    IDeviceBuildInfo mDeviceBuild;
    IKernelBuildInfo mKernelBuild;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mDeviceBuild = EasyMock.createMock(IDeviceBuildInfo.class);
        EasyMock.expect(mDeviceBuild.getBuildId()).andStubReturn("device");
        mKernelBuild = EasyMock.createMock(IKernelBuildInfo.class);
        EasyMock.expect(mKernelBuild.getBuildId()).andStubReturn("kernel");

        // Need to replay the mocks to create mBuildInfo and then reset after since the constructor
        // calls getBuildId()
        EasyMock.replay(mDeviceBuild, mKernelBuild);
        mBuildInfo = new KernelDeviceBuildInfo(mDeviceBuild, mKernelBuild, "build", "target");
        EasyMock.reset(mDeviceBuild, mKernelBuild);

        EasyMock.expect(mKernelBuild.getBuildId()).andStubReturn("kernel");
        EasyMock.expect(mDeviceBuild.getBuildId()).andStubReturn("device");
    }

    /**
     * Test method to test correctness of combining build ids.
     */
    public void testBuildId() throws Exception {
        assertEquals("kernel_device", mBuildInfo.getBuildId());
    }

    /**
     * Test method for {@link DeviceBuildInfo#clone()}.
     */
    public void testClone() throws Exception {
        EasyMock.expect(mDeviceBuild.clone()).andReturn(mDeviceBuild);
        EasyMock.expect(mKernelBuild.clone()).andReturn(mKernelBuild);
        EasyMock.replay(mDeviceBuild, mKernelBuild);

        IKernelDeviceBuildInfo copy = (IKernelDeviceBuildInfo) mBuildInfo.clone();

        assertEquals(mBuildInfo.getBuildBranch(), copy.getBuildBranch());
        assertEquals(mBuildInfo.getBuildFlavor(), copy.getBuildFlavor());
        assertEquals(mBuildInfo.getBuildId(), copy.getBuildId());
        assertEquals(mBuildInfo.getBuildTargetName(), copy.getBuildTargetName());
        assertEquals(mBuildInfo.getTestTag(), copy.getTestTag());

        EasyMock.verify(mDeviceBuild, mKernelBuild);
    }

    public void testCleanUp() {
        mDeviceBuild.cleanUp();
        EasyMock.expectLastCall();
        mKernelBuild.cleanUp();
        EasyMock.expectLastCall();
        EasyMock.replay(mDeviceBuild, mKernelBuild);

        mBuildInfo.cleanUp();

        EasyMock.verify(mDeviceBuild, mKernelBuild);
    }
}
