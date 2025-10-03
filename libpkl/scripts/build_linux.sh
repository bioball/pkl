#!/usr/bin/env bash
#===----------------------------------------------------------------------===//
# Copyright © 2025 Apple Inc. and the Pkl project authors. All rights reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#===----------------------------------------------------------------------===//
## build_linux.sh
# This script intercepts compiler arguments from graalvm native-image to build libpkl.
#
# Use with --native-compiler-path=${pathToThisScript}

set -e

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
SRC_DIR="${SCRIPT_DIR}/../src/main/c"
TEMP_OUTPUT_PATH="${PKL_TEMP_OUTPUT_PATH}"
OUTPUT_PATH="${PKL_OUTPUT_PATH}"
OBJECTS_DIR=${TEMP_OUTPUT_PATH}/objects
PKL_OBJECT_FILE="${OBJECTS_DIR}"/pkl.o

rm -rf "${OBJECTS_DIR}"
mkdir -p "${OBJECTS_DIR}"

# Determine the project name based on the output .so argument
for arg in "$@"; do
  if [[ "$arg" == *.so ]]; then
    LIB_NAME=$(basename "${arg%.so}")
    break
  fi
done

# Do a simple forward for any calls that are used to compile individual C files
if [[ -z $LIB_NAME ]]; then
    gcc "$@"
    exit 0
fi

# Create a debug log in $output/logs
LOG_PATH="${TEMP_OUTPUT_PATH}/logs"
LOG_FILE="${LOG_PATH}/compiler_commands.txt"

rm -rf "$LOG_PATH"
mkdir -p "$LOG_PATH"

echo "original args: " >> "${LOG_FILE}"
CC_ARGS=$*
echo "cc $CC_ARGS" >> "${LOG_FILE}"

WORKINGDIR=${PWD}
echo "Working directory: ${WORKINGDIR}" > "${LOG_FILE}"
echo "Output path: ${OUTPUT_PATH}" >> "${LOG_FILE}"

echo "" >> "${LOG_FILE}"
echo "===== Original args =====" >> "${LOG_FILE}"
echo "$*" >> "${LOG_FILE}"

echo "" >> "${LOG_FILE}"
echo "===== Build pkl.o =====" >> "${LOG_FILE}"

PKL_CC_ARGS=(
  "-c"
  "${SRC_DIR}/pkl.c"
  "-I${TEMP_OUTPUT_PATH}"
  "-I${SCRIPT_DIR}/../src/main/c/"
  "-DPKL_VERSION=\"${PKL_VERSION}\""
  "-o"
  "${PKL_OBJECT_FILE}"
)

echo "gcc" "${PKL_CC_ARGS[@]}" >> "${LOG_FILE}"
# shellcheck disable=SC2086
gcc "${PKL_CC_ARGS[@]}"

echo "===== Build shared library =====" >> "${LOG_FILE}"

SHARED_CC_ARGS=()
for arg in "$@"
do
  if [[ "$arg" == *.so ]]; then
    # change output .so to libpkl.so, placed into our own output dir
    SHARED_CC_ARGS+=("${OUTPUT_PATH}/libpkl.so")
  elif [[ "$arg" == "$LIB_NAME.o" ]]; then
    # insert pkl.o as an object file
    SHARED_CC_ARGS+=("${PKL_OBJECT_FILE}" "${TEMP_OUTPUT_PATH}/objects/$LIB_NAME.o")
  elif [[ "$arg" == *exported_symbols.list ]]; then
    # add symbols from pkl.o to exported_symbols.list. algorithm:
    #   1. extract public symbols from `pkl.o` and write them to a temp file
    #   2. insert these symbols after the `global:` line in the symbol list
    #   3. remove temp file
    SYMBOL_LIST_FILE=$(echo "$arg" | cut -d "," -f3-)
    echo "Adding symbols to $SYMBOL_LIST_FILE" >> "${LOG_FILE}"
    temp=$(mktemp)
    nm -g "${PKL_OBJECT_FILE}" | awk '/^[0-9a-f]+ [TD] / { print "\"" $3 "\";" }' > "$temp"
    sed -i '/global:/r '"$temp" "$SYMBOL_LIST_FILE"
    rm "$temp"
    echo "New version file contents: $(cat "$SYMBOL_LIST_FILE")" >> "${LOG_FILE}"
    SHARED_CC_ARGS+=("$arg")
  else
    SHARED_CC_ARGS+=("$arg")
  fi
done

echo "gcc" "${SHARED_CC_ARGS[@]}" >> "${LOG_FILE}"
gcc "${SHARED_CC_ARGS[@]}"

echo "" >> "${LOG_FILE}"
echo "===== Build static library =====" >> "${LOG_FILE}"

# To create a single static library on linux we need to call 'ar -r' on all .o files.
# In order to also include all static library dependencies, we can first extract the
# .o files and then include them as well.
echo "======= Source archives"  >> "${LOG_FILE}"
AR_ARGS="-rcs ${OUTPUT_PATH}/libpkl.a ${OBJECTS_DIR}/*.o"

for arg in $CC_ARGS; do
  if [[ $arg =~ .*\.(a)$ ]]; then
    # extract the objects (.o) of each archive (.a) into
    # separate directories to avoid naming collisions
    echo "$arg"  >> "${LOG_FILE}"
    ARCHIVE_DIR="${OBJECTS_DIR}"/$(basename "${arg%.a}")
    mkdir "${ARCHIVE_DIR}"
    cp "$arg" "${ARCHIVE_DIR}"
    cd "${ARCHIVE_DIR}" || exit
    ar -x "$arg"
    cd "${WORKINGDIR}" || exit
    AR_ARGS+=" ${ARCHIVE_DIR}/*.o"
  elif [[ $arg =~ .*\.(o)$ ]]; then
    cp "$arg" "${OBJECTS_DIR}"
  fi
done

echo "======= Objects"  >> "${LOG_FILE}"
find "${OBJECTS_DIR}" -name "*.o" >> "${LOG_FILE}"

echo "======= Archive command"  >> "${LOG_FILE}"
echo "ar $AR_ARGS" >> "${LOG_FILE}"
# shellcheck disable=SC2086
ar $AR_ARGS

echo "" >> "${LOG_FILE}"
echo "===== Copy header file =====" >> "${LOG_FILE}"
CP_ARGS="${SRC_DIR}/pkl.h ${OUTPUT_PATH}/pkl.h"
echo "cp $CP_ARGS" >> "${LOG_FILE}"
# shellcheck disable=SC2086
cp $CP_ARGS
