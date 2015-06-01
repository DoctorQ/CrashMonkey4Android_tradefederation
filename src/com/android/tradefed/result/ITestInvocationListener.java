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
package com.android.tradefed.result;

import java.io.File;

import com.android.ddmlib.testrunner.ITestRunListener;
import com.android.tradefed.build.IBuildInfo;

/**
 * Listener for test results from the test invocation.
 * <p/>
 * A test invocation can itself include multiple test runs, so the sequence of calls will be
 * <ul>
 * <li>invocationStarted(BuildInfo)</li>
 * <li>测试的开始,是cts测试类型,不是单独一条case的开始,testRunStarted</li>
 * <li>一条case的开始,testStarted</li>
 * <li>一条case的failed,[testFailed]</li>
 * <li>一条case的结束,testEnded</li>
 * <li>...</li>
 * <li>测试的结束,cts测试类型,不是一条单独case的结束,是整个测试任务的结束,testRunEnded</li>
 * <li>...</li>
 * <li>测试任务的失败,testRunFailed</li>
 * <li>...</li>
 * <li>测试任务的停止,testRunStopped</li>
 * <li>[invocationFailed]</li>
 * <li>[testLog+]</li>
 * <li>invocationEnded</li>
 * <li>getSummary</li>
 * </ul>
 * <p/>
 * Note that this is re-using the {@link com.android.ddmlib.testrunner.ITestRunListener}
 * because it's a generic interface. The results being reported are not necessarily device specific.
 */
public interface ITestInvocationListener extends ITestRunListener {

    /**
     * Reports the start of the test invocation.
     * <p/>
     * Will be automatically called by the TradeFederation framework.
     *	方法名改为invakeStarted更容易让人理解,意思为调用的开始,因为cts的测试不只是上来就开始跑case,而是一系列的动作,前期准备build啊,机器的准备啊等等。
     * @param buildInfo information about the build being tested
     */
    public void invocationStarted(IBuildInfo buildInfo);
    /**
     * Provides the associated log or debug data from the test invocation.
     * <p/>
     * Must be called before {@link #invocationFailed(Throwable)} or {@link #invocationEnded(long)}
     * <p/>
     * The TradeFederation framework will automatically call this method, providing the host log
     * and if applicable, the device logcat.
     *
     * @param dataName a {@link String} descriptive name of the data. e.g. "device_logcat". Note
     *            dataName may not be unique per invocation. ie implementers must be able to handle
     *            multiple calls with same dataName
     * @param dataType the {@link LogDataType} of the data
     * @param dataStream the {@link InputStreamSource} of the data. Implementers should call
     *        createInputStream to start reading the data, and ensure to close the resulting
     *        InputStream when complete. Callers should ensure the source of the data remains
     *        present and accessible until the testLog method completes.
     */
    public void testLog(String dataName, LogDataType dataType, InputStreamSource dataStream);

    /**
     * Reports that the invocation has terminated, whether successfully or due to some error
     * condition.
     * <p/>
     * Will be automatically called by the TradeFederation framework.
     *	
     * @param elapsedTime the elapsed time of the invocation in ms
     */
    public void invocationEnded(long elapsedTime);

    /**
     * Reports an incomplete invocation due to some error condition.
     * <p/>
     * Will be automatically called by the TradeFederation framework.
     *
     * @param cause the {@link Throwable} cause of the failure
     */
    public void invocationFailed(Throwable cause);

    /**
     * Allows the InvocationListener to return a summary.
     *	返回一个TestSummary对象,可以查看一下TestSummary对象的意义
     * @return A {@link TestSummary} summarizing the run, or null
     */
    public TestSummary getSummary();
    
    
    	
    

}
