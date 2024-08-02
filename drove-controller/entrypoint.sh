#!/bin/bash
#
#  Copyright (c) 2022 Original Author(s), PhonePe India Pvt. Ltd.
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#  http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.
#

#set -x

pid=0

# SIGTERM-handler
term_handler() {
  if [ $pid -ne 0 ]; then
    kill -SIGTERM "$pid"
    wait "$pid"
  fi
  echo "Exiting on sigterm"
  exit 143; # 128 + 15 -- SIGTERM
}

# setup handlers
# on callback, kill the last background process, which is `tail -f /dev/null` and execute the specified handler
trap 'kill ${!}; term_handler' SIGTERM

if [ -z "${ZK_CONNECTION_STRING}" ]; then
  echo "ZK_CONNECTION_STRING is a mandatory parameter"
  exit 1
fi

export DROVE_ADMIN_PASSWORD="${ADMIN_PASSWORD-admin}"
export DROVE_GUEST_PASSWORD="${GUEST_PASSWORD-guest}"
export DROVE_CONTROLLER_SECRET="${CONTROLLER_SECRET-ControllerSecret}"
export DROVE_EXECUTOR_SECRET="${EXECUTOR_SECRET-ExecutorSecret}"
export DROVE_INSTANCE_AUTH_SECRET="${INSTANCE_AUTH_SECRET-InstanceAuthSecret}"

if [ "${DROVE_ADMIN_PASSWORD}" = "admin" ] \
      || [ "${DROVE_GUEST_PASSWORD}" = "guest" ] \
      || [ "${DROVE_CONTROLLER_SECRET}" = "ControllerSecret" ] \
      || [ "${DROVE_EXECUTOR_SECRET}" = "ExecutorSecret" ] \
      || [ "${DROVE_INSTANCE_AUTH_SECRET}" = "InstanceAuthSecret" ]; then
  echo "WARNING: Using default values for at least one security variable."
  echo "It is strongly recommended to override all security related variables."
fi

export DROVE_LOG_LEVEL="${LOG_LEVEL-INFO}"
export DROVE_TIMEZONE="${TZ-UTC}"

CONFIG_PATH=${CONFIG_FILE_PATH:-config.yml}

if [ ! -f "${CONFIG_PATH}" ]; then
  echo "Config file ${CONFIG_PATH} not found."
  CONFIG_DIR=$(dirname "${CONFIG_PATH}")
  echo "File system for config directory: ${CONFIG_DIR}"
  ls -l "${CONFIG_DIR}"
  exit 1
else
  echo "Config ${CONFIG_PATH} file exists. Proceeding to service startup."
fi

export JAVA_HOME="${JAVA_HOME}:${PWD}"

DEBUG_ENABLED="${DEBUG-0}"
if [ "$DEBUG_ENABLED" -ne 0 ]; then

  echo "Environment variables:"
  printenv

  echo "Java version details:"
  java -version

  echo "Contents of working dir: ${PWD}"
  ls -l "${PWD}"

  echo "IP assigned to container: $(hostname -I)"
fi

# run application
CMD=$(eval echo "java -jar -XX:+${GC_ALGO-UseG1GC} -Xms${JAVA_PROCESS_MIN_HEAP-1g} -Xmx${JAVA_PROCESS_MAX_HEAP-1g} ${JAVA_OPTS} drove-controller.jar server ${CONFIG_PATH}")
echo "Starting Drove Controller by running command: ${CMD}"

eval "${CMD}" &

pid="$!"

# wait forever
while true
do
  tail -f /dev/null & wait ${!}
done


