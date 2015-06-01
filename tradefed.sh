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
java_version_string=$(java -version 2>&1)
JAVA_VERSION=$(echo "$java_version_string" | grep '[ "]1\.[67][\. "$$]')
if [ "${JAVA_VERSION}" == "" ]; then
    echo "Wrong java version. 1.6 is required."
    exit
else
    # We have 1.6 or 1.7.  Now print a warning if the version was 1.7
    java_version_17=$(echo "$java_version_string" | grep '[ "]1\.7[\. "$$]')
    if [ "${java_version_17}" != "" ]; then
        echo "WARNING: Trade Federation is not heavily tested under Java version 1.7"
        echo
    fi
fi

# check debug flag and set up remote debugging
if [ -n "${TF_DEBUG}" ]; then
    if [ -z "${TF_DEBUG_PORT}" ]; then
        TF_DEBUG_PORT=10088
    fi
    RDBG_FLAG="-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=${TF_DEBUG_PORT}"
fi

# first try to find TF jars in same dir as this script
CUR_DIR=$(dirname "$0")
if [ -f "${CUR_DIR}/tradefed.jar" ]; then
    tf_path="${CUR_DIR}/*"
elif [ ! -z "${ANDROID_HOST_OUT}" ]; then
    # in an Android build env, tradefed.jar should be in
    # $ANDROID_HOST_OUT/tradefed/
    if [ -f "${ANDROID_HOST_OUT}/tradefed/tradefed.jar" ]; then
        # We intentionally pass the asterisk through without shell expansion
        tf_path="${ANDROID_HOST_OUT}/tradefed/*"
        # ddmlib-prebuilt is in the framework subdir
        ddmlib_path="${ANDROID_HOST_OUT}/framework/ddmlib-prebuilt.jar"
    fi
fi

if [ -z "${tf_path}" ]; then
    echo "ERROR: Could not find tradefed jar files"
    exit
fi


# Note: must leave ${RDBG_FLAG} unquoted so that it goes away when unset
java ${RDBG_FLAG} -XX:+HeapDumpOnOutOfMemoryError \
  -cp "${ddmlib_path}:${tf_path}" com.android.tradefed.command.Console "$@"
