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

# Makefile to build device-based native tests.

# GTest does not build on the simulator because it depends on STLport.
ifneq ($(TARGET_SIMULATOR),true)

LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

# All source files will be bundled into one test module
LOCAL_SRC_FILES := \
	TradeFedNativeTestApp_test.cpp

# stlport required for gtest
LOCAL_SHARED_LIBRARIES := \
	libstlport

# GTest libraries
LOCAL_STATIC_LIBRARIES := \
	libgtest \
	libgtest_main

# bionic, libstdc++, and stlport are required for gtest
LOCAL_C_INCLUDES := \
    bionic \
    bionic/libstdc++/include \
    external/gtest/include \
    external/stlport/stlport

LOCAL_MODULE_TAGS := tests

# All gtests in all files should be compiled into one binary
# The standard naming should conform to: <module_being_tested>tests
# For example, for libjingle, use libjingletests
LOCAL_MODULE := nativetestapp

# Always put native tests into data/nativetest
LOCAL_MODULE_PATH := $(TARGET_OUT_DATA)/nativetest/tfnativetests
# TODO: Move this into a new variable: TARGET_OUT_DATA_NATIVE_TESTS, eg:
#LOCAL_MODULE_PATH := $(TARGET_OUT_DATA_NATIVE_TESTS)/tfnativetests

include $(BUILD_EXECUTABLE)

endif  # ($(TARGET_SIMULATOR),true)
