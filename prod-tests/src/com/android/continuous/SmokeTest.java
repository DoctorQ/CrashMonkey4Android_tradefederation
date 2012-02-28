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

package com.android.continuous;

import com.android.tradefed.config.Option;
import com.android.tradefed.config.Option.Importance;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.result.BugreportCollector;
import com.android.tradefed.result.BugreportCollector.Freq;
import com.android.tradefed.result.BugreportCollector.Noun;
import com.android.tradefed.result.BugreportCollector.Predicate;
import com.android.tradefed.result.BugreportCollector.Relation;
import com.android.tradefed.result.DeviceFileReporter;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.testtype.InstrumentationTest;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A test that runs the smoke tests
 * <p />
 * Simply {@link InstrumentationTest} with extra reporting.  Should be re-integrated with
 * {@link InstrumentationTest} after it's clear that it works as expected.
 */
@OptionClass(alias = "smoke")
public class SmokeTest extends InstrumentationTest {
    @Option(name = "capture-file-pattern", description = "File glob of on-device files to log " +
            "if found.  Takes two arguments: the glob, and the file type " +
            "(text/xml/zip/gzip/png/unknown).  May be repeated.", importance = Importance.IF_UNSET)
    private Map<String, LogDataType> mUploadFilePatterns = new LinkedHashMap<String, LogDataType>();

    /**
     * {@inheritDoc}
     */
    @Override
    public void run(final ITestInvocationListener listener) throws DeviceNotAvailableException {
        final BugreportCollector bugListener = new BugreportCollector(listener, getDevice());
        bugListener.addPredicate(BugreportCollector.AFTER_FAILED_TESTCASES);

        super.run(bugListener);

        final DeviceFileReporter dfr = new DeviceFileReporter(getDevice(), bugListener);
        dfr.addPatterns(mUploadFilePatterns);
        dfr.run();
    }

}

