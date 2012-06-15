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

checkPath() {
    if ! type -P $1 &> /dev/null; then
        echo "Unable to find $1 in path."
        exit
    fi;
}

checkFile() {
    if [ ! -f "$1" ]; then
        echo "Unable to locate $1"
        exit
    fi;
}

checkPath adb
checkPath java

# check java version
JAVA_VERSION=$(java -version 2>&1 | grep '[ "]1\.6[\. "$$]')
if [ "${JAVA_VERSION}" == "" ]; then
    echo "Wrong java version. 1.6 is required."
    exit
fi

# check debug flag and set up remote debugging
if [ -n "${TF_DEBUG}" ]; then
    if [ -z "${TF_DEBUG_PORT}" ]; then
        TF_DEBUG_PORT=10088
    fi
    RDBG_FLAG=-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=${TF_DEBUG_PORT}
fi

# first try to find TF jars in same dir as this script
CUR_DIR=$(dirname $0)
if [ -f "${CUR_DIR}/tradefed.jar" ]; then
    tf_path=${CUR_DIR}/\*
elif [ ! -z "${ANDROID_BUILD_TOP}" ]; then
    # in an Android build env, tradefed.sh should be in
    # out/host/$OS/bin and tradefed.jar should be in
    # out/host/$OS/tradefed
    if [ -f "${CUR_DIR}/../tradefed/tradefed.jar" ]; then
      tf_path=$CUR_DIR/../tradefed/\*
      # ddmlib-prebuilt is still in standard output dir
      ddmlib_path=${CUR_DIR}/../framework/ddmlib-prebuilt.jar
    fi
fi

if [ -z "${tf_path}" ]; then
    echo "Could not find tradefed jar files"
    exit
fi


java $RDBG_FLAG \
  -cp $ddmlib_path:$tf_path com.android.tradefed.command.Console "$@"
