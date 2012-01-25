# Copyright (C) 2010 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

# Only compile source java files in this lib.
LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_JAVA_RESOURCE_DIRS := res

LOCAL_JAVACFLAGS += -g -Xlint

LOCAL_MODULE := tradefed

LOCAL_MODULE_TAGS := optional
LOCAL_STATIC_JAVA_LIBRARIES := junit kxml2-2.3.0 guavalib jline-1.0
LOCAL_JAVA_LIBRARIES := ddmlib-prebuilt

include $(BUILD_HOST_JAVA_LIBRARY)

# makefile rules to copy jars to HOST_OUT/tradefed
# so tradefed.sh can automatically add to classpath
DEST_JAR := $(HOST_OUT)/tradefed/$(LOCAL_MODULE).jar
$(DEST_JAR): $(LOCAL_BUILT_MODULE)
	$(copy-file-to-new-target)

# this dependency ensure the above rule will be executed if jar is built
$(LOCAL_INSTALLED_MODULE) : $(DEST_JAR)

#######################################################
include $(CLEAR_VARS)

# Create a simple alias to build all the TF-related targets
# Note that this is incompatible with `make dist`.  If you want to make
# the distribution, you must run `tapas` with the individual target names.
.PHONY: tradefed-all
tradefed-all: tradefed tradefed-tests tf-prod-tests

# ====================================
include $(CLEAR_VARS)
# copy tradefed.sh script to host dir

LOCAL_MODULE_TAGS := optional

LOCAL_PREBUILT_EXECUTABLES := tradefed.sh
include $(BUILD_HOST_PREBUILT)

# Build all sub-directories
include $(call all-makefiles-under,$(LOCAL_PATH))
