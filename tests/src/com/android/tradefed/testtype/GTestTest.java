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
package com.android.tradefed.testtype;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ICancelableReceiver;
import com.android.tradefed.device.IFileListingService;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.IFileListingService.IFileEntry;
import com.android.tradefed.result.ITestInvocationListener;

import junit.framework.TestCase;

import org.easymock.EasyMock;

import java.util.Vector;


/**
 * Unit tests for {@link GTestTest}.
 */
public class GTestTest extends TestCase {
    private ITestInvocationListener mMockInvocationListener = null;
    private IFileEntry mMockRootFileEntry = null;
    private ICancelableReceiver mMockCancelableReceiver = null;
    private IFileListingService mMockFileListingService = null;
    private ITestDevice mMockITestDevice = null;

    /**
     * Helper to initialize the various EasyMocks we'll need.
     */
    private void initializeMocks() {
        mMockInvocationListener = EasyMock.createMock(ITestInvocationListener.class);
        mMockRootFileEntry = EasyMock.createMock(IFileEntry.class);
        mMockCancelableReceiver = EasyMock.createMock(ICancelableReceiver.class);
        mMockFileListingService = EasyMock.createMock(IFileListingService.class);
        mMockITestDevice = EasyMock.createMock(ITestDevice.class);
    }

    /**
     * Helper that replays all mocks.
     */
    private void replayMocks() {
      EasyMock.replay(mMockInvocationListener, mMockRootFileEntry, mMockFileListingService,
              mMockITestDevice, mMockCancelableReceiver);
    }

    /**
     * Helper that verifies all mocks.
     */
    private void verifyMocks() {
      EasyMock.verify(mMockRootFileEntry, mMockFileListingService, mMockITestDevice,
              mMockCancelableReceiver);
    }

    /**
     * Helper that returns an array of a given number of IFileEntry's.
     *
     * @param numberOfEntries The number of IFileEntry's to create in the array
     */
    private IFileEntry[] createArrayOfFileEntries(int numberOfEntries) {
        Vector<IFileEntry> returnArray = new Vector<IFileEntry>();
        for (int i=0; i<numberOfEntries; ++i) {
            returnArray.add(EasyMock.createMock(IFileEntry.class));
        }
        return returnArray.toArray(new IFileEntry[returnArray.size()]);
    }

    /**
     * Test the run method retrieves the correct location for native tests.
     */
    public void testRun() throws DeviceNotAvailableException {
        final String[] nativeTestPath = {"data", "nativetest"};

        initializeMocks();

        IFileEntry[] iFileEntries = {mMockRootFileEntry};

        EasyMock.expect(mMockFileListingService.getRoot()).andReturn(mMockRootFileEntry);
        for (String s : nativeTestPath) {
            EasyMock.expect(mMockFileListingService.getChildren(mMockRootFileEntry, false,
                    null)).andReturn(iFileEntries);
            EasyMock.expect(mMockRootFileEntry.findChild(EasyMock.matches(s))).andReturn(
                    mMockRootFileEntry);
        }

        replayMocks();
        GTest gtest = new GTest() {
            @Override
            protected IFileListingService getFileListingService() {
                return mMockFileListingService;
            }
            // Stubbed - test this method separately
            @Override
            protected boolean doRunAllTestsInSubdirectory(IFileEntry rootEntry,
                    ITestDevice testDevice, ICancelableReceiver outputReceiver) {
              return false;
            }
        };
        gtest.setDevice(mMockITestDevice);
        gtest.run(mMockInvocationListener);
        verifyMocks();
    }

    /**
     * Test the doRunAllTestsInSubdirectory method for files only.
     */
    public void testDoRunAllTestsInSubdirectory_FilesOnly() throws DeviceNotAvailableException {
        initializeMocks();

        String[] childrensPath = {
                "path/to/first/file",
                "path/to/second/file",
                "thirdPath",
                "path/to/fourth",
                "path/to/apkFile.apk"};

        IFileEntry[] children = createArrayOfFileEntries(childrensPath.length);
        GTest gtest = new GTest() {
            @Override
            protected IFileListingService getFileListingService() {
                return mMockFileListingService;
            }
        };

        EasyMock.expect(mMockFileListingService.getChildren(mMockRootFileEntry, true, null)
                ).andReturn(children);

        for (int i=0; i<children.length; ++i) {
            EasyMock.expect(children[i].getFullEscapedPath()).andReturn(childrensPath[i]);
            EasyMock.expect(children[i].isDirectory()).andReturn(false);
        }

        for (int i=0; i<children.length; ++i) {
            if (childrensPath[i].endsWith(".apk")) {
                EasyMock.expect(children[i].isAppFileName()).andReturn(true);
            }
            else {
              EasyMock.expect(children[i].isAppFileName()).andReturn(false);
              mMockITestDevice.executeShellCommand(EasyMock.startsWith(childrensPath[i]),
                      EasyMock.same(mMockCancelableReceiver));
            }
        }

        for (IFileEntry file : children) {
            EasyMock.replay(file);
        }
        replayMocks();

        gtest.doRunAllTestsInSubdirectory(mMockRootFileEntry, mMockITestDevice,
                mMockCancelableReceiver);

        for (IFileEntry file : children) {
            EasyMock.verify(file);
        }
        verifyMocks();
    }

    /**
     * Test the doRunAllTestsInSubdirectory for a 1-level subdirectory and files.
     */
    public void testDoRunAllTestsInSubdirectory_Directories() throws DeviceNotAvailableException  {
        initializeMocks();

        String[] children1Paths = {
                "path/to/directory2"};

        String[] children2Paths = {
                "pathto/file1",
                "pathto/file2",
                "pathto/file3"};

        // Setup the file hierarchy
        IFileEntry[] directory1Children = createArrayOfFileEntries(children1Paths.length);
        IFileEntry[] directory2Children = createArrayOfFileEntries(children2Paths.length);

        GTest gtest = new GTest() {
            @Override
            protected IFileListingService getFileListingService() {
                return mMockFileListingService;
            }
        };

        EasyMock.expect(mMockFileListingService.getChildren(mMockRootFileEntry, true, null)
                ).andReturn(directory1Children);

        // Processing Directory 1's children (just 1 subdir, Directory 2):
        for (int i=0; i<directory1Children.length; ++i) {
            EasyMock.expect(directory1Children[i].getFullEscapedPath()).andReturn(
                    children1Paths[i]);
            EasyMock.expect(directory1Children[i].isDirectory()).andReturn(true);
        }

        // Processing Directory 2's children (3 files)
        IFileEntry directory2FileEntry = directory1Children[0];
        EasyMock.expect(mMockFileListingService.getChildren(directory2FileEntry, true, null)
            ).andReturn(directory2Children);

        for (int i=0; i<directory2Children.length; ++i) {
            EasyMock.expect(directory2Children[i].getFullEscapedPath()).andReturn(
                    children2Paths[i]);
            EasyMock.expect(directory2Children[i].isDirectory()).andReturn(false);
        }

        for (int i=0; i<children2Paths.length; ++i) {
            EasyMock.expect(directory2Children[i].isAppFileName()).andReturn(false);
            mMockITestDevice.executeShellCommand(EasyMock.startsWith(children2Paths[i]),
                    EasyMock.same(mMockCancelableReceiver));
        }

        for (IFileEntry file : directory1Children) {
            EasyMock.replay(file);
        }
        for (IFileEntry file : directory2Children) {
            EasyMock.replay(file);
        }
        replayMocks();

        gtest.doRunAllTestsInSubdirectory(mMockRootFileEntry, mMockITestDevice,
                mMockCancelableReceiver);

        verifyMocks();
        for (IFileEntry file : directory1Children) {
            EasyMock.verify(file);
        }
        for (IFileEntry file : directory2Children) {
            EasyMock.verify(file);
        }
    }

    /**
     * Helper that returns a GTest object with the getFileListingService implementation stubbed out.
     *
     * @return A new GTest with the getFileListingService stubbed out
     */
    private GTest getStubbedMockGTest() {
      return new GTest() {
          @Override
          protected IFileListingService getFileListingService() {
              return mMockFileListingService;
          }
      };
    }

    /**
     * Helper function to do the actual filtering test.
     *
     * @param gtest The GTest to run the test on, with the positive/negative filters already set
     * @param filterString The string to search for in the Mock, to verify filtering was called
     * @throws DeviceNotAvailableException
     */
    private void doTestFilter(GTest gtest, String filterString) throws DeviceNotAvailableException {
        initializeMocks();

        String[] childrensPath = {
                "file1",
                "sub/file2",
                "file3",
                "path/to/some/random-file"};

        IFileEntry[] children = createArrayOfFileEntries(childrensPath.length);
        EasyMock.expect(mMockFileListingService.getChildren(mMockRootFileEntry, true, null)
                ).andReturn(children);

        for (int i=0; i<children.length; ++i) {
            EasyMock.expect(children[i].getFullEscapedPath()).andReturn(childrensPath[i]);
            EasyMock.expect(children[i].isDirectory()).andReturn(false);
            EasyMock.expect(children[i].isAppFileName()).andReturn(false);
        }

        for (int i=0; i<children.length; ++i) {
            mMockITestDevice.executeShellCommand(EasyMock.contains(filterString),
                    EasyMock.same(mMockCancelableReceiver));
        }

        for (IFileEntry file : children) {
            EasyMock.replay(file);
        }
        replayMocks();

        gtest.doRunAllTestsInSubdirectory(mMockRootFileEntry, mMockITestDevice,
                mMockCancelableReceiver);

        for (IFileEntry file : children) {
            EasyMock.verify(file);
        }
        verifyMocks();
    }

    /**
     * Test the positive filtering of test methods.
     */
    public void testPositiveFilter() throws DeviceNotAvailableException {
        GTest gtest = getStubbedMockGTest();

        String posFilter = "abbccc";
        gtest.setTestNamePositiveFilter(posFilter);

        doTestFilter(gtest, posFilter);
    }

    /**
     * Test the negative filtering of test methods.
     */
    public void testNegativeFilter() throws DeviceNotAvailableException {
        GTest gtest = getStubbedMockGTest();

        String negFilter = "*don?tRunMe*";
        gtest.setTestNameNegativeFilter(negFilter);

        doTestFilter(gtest, "-*." + negFilter);
    }

    /**
     * Test simultaneous positive and negative filtering of test methods.
     */
    public void testPositiveAndNegativeFilter() throws DeviceNotAvailableException {
        GTest gtest = getStubbedMockGTest();

        String posFilter = "pleaseRunMe";
        String negFilter = "dontRunMe";
        gtest.setTestNamePositiveFilter(posFilter);
        gtest.setTestNameNegativeFilter(negFilter);

        String filter = String.format("%s-*.%s", posFilter, negFilter);
        doTestFilter(gtest, filter);
    }
}
