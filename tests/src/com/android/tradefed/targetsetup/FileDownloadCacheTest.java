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
package com.android.tradefed.targetsetup;

import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.StreamUtil;

import org.easymock.EasyMock;
import org.easymock.IAnswer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import junit.framework.TestCase;

/**
 * Unit tests for {@link FileDownloadCache}.
 */
public class FileDownloadCacheTest extends TestCase {

    private static final String REMOTE_PATH = "foo/path";
    private static final String DOWNLOADED_CONTENTS = "downloaded contents";

    private IFileDownloader mMockDownloader;

    private FileDownloadCache mCache;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mMockDownloader = EasyMock.createMock(IFileDownloader.class);
        mCache = new FileDownloadCache(FileUtil.createTempDir("unittest"));
    }

    @Override
    protected void tearDown() throws Exception {
        mCache.empty();
        super.tearDown();
    }

    /**
     * Test basic case for {@link FileDownloadCache#fetchRemoteFile(IFileDownloader, String)}.
     */
    public void testFetchRemoteFile() throws Exception {
        setDownloadExpections();
        EasyMock.replay(mMockDownloader);
        assertFetchRemoteFile();
        EasyMock.verify(mMockDownloader);
    }

    /**
     * Test {@link FileDownloadCache#fetchRemoteFile(IFileDownloader, String)} when file can be
     * retrieved from cache.
     */
    public void testFetchRemoteFile_cacheHit() throws Exception {
        setDownloadExpections();
        EasyMock.replay(mMockDownloader);
        assertFetchRemoteFile();
        // now retrieve file again
        assertFetchRemoteFile();
        // verify only one download call occurred
        EasyMock.verify(mMockDownloader);
    }

    /**
     * Test {@link FileDownloadCache#fetchRemoteFile(IFileDownloader, String)} when cache grows
     * larger than max
     */
    public void testFetchRemoteFile_cacheSizeExceeded() throws Exception {
        final String remotePath2 = "anotherpath";
        // set cache size to be small
        mCache.setMaxCacheSize(DOWNLOADED_CONTENTS.length() + 1);
        setDownloadExpections(remotePath2);
        setDownloadExpections();
        EasyMock.replay(mMockDownloader);
        assertFetchRemoteFile(remotePath2);
        // now retrieve another file, which will exceed size of cache
        assertFetchRemoteFile();
        assertTrue(mCache.cacheContainsFile(REMOTE_PATH));
        assertFalse(mCache.cacheContainsFile(remotePath2));
        EasyMock.verify(mMockDownloader);
    }

    /**
     * Perform one fetchRemoteFile call and verify contents for default remote path
     */
    private void assertFetchRemoteFile() throws TargetSetupError, IOException {
        assertFetchRemoteFile(REMOTE_PATH);
    }

    /**
     * Perform one fetchRemoteFile call and verify contents
     */
    private void assertFetchRemoteFile(String remotePath) throws TargetSetupError, IOException {
        // test downloading file not in cache
        File fileCopy = mCache.fetchRemoteFile(mMockDownloader, remotePath);
        try {
            assertTrue(mCache.cacheContainsFile(remotePath));
            String contents = StreamUtil.getStringFromStream(new FileInputStream(fileCopy));
            assertEquals(DOWNLOADED_CONTENTS, contents);
        } finally {
            fileCopy.delete();
        }
    }

    /**
     * Set EasyMock expectations for a downloadFile call for default remote path
     */
    private void setDownloadExpections() throws TargetSetupError {
        setDownloadExpections(REMOTE_PATH);
    }

    /**
     * Set EasyMock expectations for a downloadFile call
     */
    @SuppressWarnings("unchecked")
    private void setDownloadExpections(String remotePath)
            throws TargetSetupError {
        IAnswer downloadAnswer = new IAnswer() {
            @Override
            public Object answer() throws Throwable {
                File fileArg =  (File) EasyMock.getCurrentArguments()[1];
                FileUtil.writeToFile("downloaded contents", fileArg);
                return null;
            }
        };
        mMockDownloader.downloadFile(EasyMock.eq(remotePath),
                (File)EasyMock.anyObject());
        EasyMock.expectLastCall().andAnswer(downloadAnswer);
    }
}
