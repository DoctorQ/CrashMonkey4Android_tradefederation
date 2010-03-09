#!/bin/bash

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

# A simpler helper script that runs the Trade Federation functional tests

lib_path=$ANDROID_BUILD_TOP/out/host/linux-x86/framework

java -cp $lib_path/easymock.jar:$lib_path/junit.jar:$lib_path/hosttestlib.jar:$lib_path/ddmlib.jar:$lib_path/GoogleTradeFed.jar:$lib_path/GoogleTradeFedTests.jar com.android.tradefed.command.Command --class com.android.tradefed.FuncTests "@$" host 
