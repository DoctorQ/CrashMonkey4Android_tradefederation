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

# A helper script that launches TradeFederation from the current build
# environment.

# Currently fixed to linux
lib_path=$ANDROID_BUILD_TOP/out/host/linux-x86/framework

# Help folks get TF up and running.  Can be removed when TF has a real "-h"
getopts h help_opt

if [ "x$help_opt" = "xh" ]; then
    # Print out help message
    dir=`dirname $0`

    echo "Usage: $0 [OPTIONS] CONFIG"
    echo
    echo "CONFIG is a configuration.  Use 'instrument' or dig around in"
    echo "${dir}/src/com/android/tradefed/config/ to find other configs."
    echo
    echo "The options are many and only self-documenting at this point.  To see"
    echo "a list, run the script ${dir}/get_options.sh"
    echo
    echo "To get started, if you used the following command to run tests by hand:"
    echo "adb -s DEVICE -e class CLASS#METHOD -w PACKAGE/RUNNER"
    echo
    echo "You can try to run the same test in Trade Federation with:"
    echo "$0 -s DEVICE --package PACKAGE --runner RUNNER --class CLASS --method METHOD instrument"
    exit 1
fi

java -cp $lib_path/TradeFed.jar com.android.tradefed.command.Command "$@"

