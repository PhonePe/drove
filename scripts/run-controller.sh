#!/bin/bash
#
# run-controller.sh — Start the Drove Controller using local target/classes + Maven repo JARs
#
# Usage:
#   ./run-controller.sh              # run with default config
#   ./run-controller.sh [config.yml] # run with a custom config file
#
# Flags (can be combined):
#   -s   Sync resources: copy drove-controller/src/main/resources/ → target/classes/
#        before starting (picks up template/JS/CSS changes without recompile)
#   -r   Recompile: run mvn compile for drove-controller and its local module
#        dependencies before starting (picks up Java source changes)
#
# Examples:
#   ./run-controller.sh -s                              # sync resources, then start
#   ./run-controller.sh -r                              # recompile, then start
#   ./run-controller.sh -r -s                           # recompile + sync, then start
#   ./run-controller.sh -s drove-controller/configs/config-dr.yml
#
# Prerequisites:
#   - Java 17+ on PATH (or set JAVA_HOME)
#   - ZooKeeper running on localhost:2181 (default config)
#

set -e

#SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCRIPT_DIR="$(pwd)"
cd "${SCRIPT_DIR}"

# ── Parse flags ────────────────────────────────────────────────────────────────
SYNC_RESOURCES=false
RECOMPILE=false
POSITIONAL=()
for arg in "$@"; do
  case "${arg}" in
    -s) SYNC_RESOURCES=true ;;
    -r) RECOMPILE=true ;;
    -h|--help)
      echo "Usage: $(basename "$0") [flags] [config.yml]"
      echo ""
      echo "Start the Drove Controller using local target/classes + Maven repo JARs."
      echo ""
      echo "Flags:"
      echo "  -r            Recompile drove-controller and its local module dependencies"
      echo "                before starting (mvn compile -pl drove-controller -am -DskipTests)"
      echo "  -s            Sync src/main/resources/ → target/classes/ before starting"
      echo "                (picks up template/JS/CSS changes without a full recompile)"
      echo "  -h, --help    Show this help message and exit"
      echo ""
      echo "Arguments:"
      echo "  config.yml    Path to the Dropwizard config file"
      echo "                (default: drove-controller/configs/config.yml)"
      echo ""
      echo "Examples:"
      echo "  $(basename "$0")                          # restart with current classes"
      echo "  $(basename "$0") -s                       # sync resources, then restart"
      echo "  $(basename "$0") -r                       # recompile, then restart"
      echo "  $(basename "$0") -r -s                    # recompile + sync, then restart"
      echo "  $(basename "$0") -s drove-controller/configs/config-dr.yml"
      exit 0
      ;;
    *) POSITIONAL+=("${arg}") ;;
  esac
done

# ── Configurable variables ─────────────────────────────────────────────────────
CONFIG="${POSITIONAL[0]:-${SCRIPT_DIR}/drove-controller/configs/config.yml}"

# JVM options (lightweight for local dev)
JVM_OPTS=(
  -server
  -Xmx512m
  -Xms256m
  -XX:+UseG1GC
  -Djava.security.egd=file:/dev/urandom
  -Dfile.encoding=utf-8
)

# ── Resolve Java binary ────────────────────────────────────────────────────────
if [ -n "${JAVA_HOME}" ]; then
  JAVA="${JAVA_HOME}/bin/java"
else
  JAVA="java"
fi

if ! command -v "${JAVA}" &>/dev/null; then
  echo "ERROR: Java not found. Set JAVA_HOME or add java to PATH." >&2
  exit 1
fi

MVN="${MVN_HOME:+${MVN_HOME}/bin/}mvn"
if ! command -v "${MVN}" &>/dev/null; then
  echo "ERROR: mvn not found. Add Maven to PATH or set MVN_HOME." >&2
  exit 1
fi

# ── Validate config file ───────────────────────────────────────────────────────
if [ ! -f "${CONFIG}" ]; then
  echo "ERROR: Config file not found at: ${CONFIG}" >&2
  exit 1
fi

# ── Recompile (optional) ──────────────────────────────────────────────────────
if [ "${RECOMPILE}" = "true" ]; then
  echo "Recompiling drove-controller and dependencies..."
  "${MVN}" compile -pl drove-controller -am -DskipTests
  echo "Recompile done."
fi

# ── Check that the controller classes exist ────────────────────────────────────
CONTROLLER_CLASSES="${SCRIPT_DIR}/drove-controller/target/classes"
if [ ! -d "${CONTROLLER_CLASSES}" ]; then
  echo "ERROR: Compiled classes not found at: ${CONTROLLER_CLASSES}"
  echo ""
  echo "Compile first with:"
  echo "  mvn compile -pl drove-controller -am -DskipTests"
  echo "  (or use the -r flag)"
  exit 1
fi

# ── Sync resources (optional) ─────────────────────────────────────────────────
if [ "${SYNC_RESOURCES}" = "true" ]; then
  SRC_RESOURCES="${SCRIPT_DIR}/drove-controller/src/main/resources"
  DST_RESOURCES="${SCRIPT_DIR}/drove-controller/target/classes"
  echo "Syncing resources: ${SRC_RESOURCES} → ${DST_RESOURCES}"
  rsync -a --exclude="*.java" "${SRC_RESOURCES}/" "${DST_RESOURCES}/"
  echo "Resources synced."
fi

# ── Build classpath ────────────────────────────────────────────────────────────
# 1. Get the full Maven dependency classpath (third-party JARs from ~/.m2)
echo "Resolving dependency classpath via Maven..."
CP_FILE="$(mktemp /tmp/drove-controller-cp.XXXXXX)"
trap "rm -f ${CP_FILE}" EXIT

"${MVN}" -q dependency:build-classpath \
  -pl drove-controller \
  -Dmdep.outputFile="${CP_FILE}" \
  -Dmdep.includeScope=runtime

# 2. Replace local drove-* module JARs with their target/classes directories
#    (so changes in source are picked up without re-installing to ~/.m2)
DROVE_MODULES=(
  "drove-common"
  "drove-statemachine"
  "drove-models"
  "drove-authentication"
  "drove-jobexecutor"
  "drove-events-client"
  "drove-client"
)

DEP_CP=$(cat "${CP_FILE}")

for MOD in "${DROVE_MODULES[@]}"; do
  LOCAL_CLASSES="${SCRIPT_DIR}/${MOD}/target/classes"
  if [ -d "${LOCAL_CLASSES}" ]; then
    # Replace any .../com/phonepe/drove/<mod>/... jar with the local classes dir
    DEP_CP=$(echo "${DEP_CP}" | python3 -c "
import sys, re
cp = sys.stdin.read().strip()
entries = cp.split(':')
mod = '${MOD}'
local = '${LOCAL_CLASSES}'
result = []
replaced = False
for e in entries:
    if ('com/phonepe/drove/' + mod + '/') in e:
        if not replaced:
            result.append(local)
            replaced = True
    else:
        result.append(e)
print(':'.join(result))
")
  fi
done

# 3. Prepend the controller's own classes
FULL_CP="${CONTROLLER_CLASSES}:${DEP_CP}"

# ── Kill any existing controller process ──────────────────────────────────────
EXISTING_PIDS=$(pgrep -f "drove-controller" 2>/dev/null || true)
if [ -n "${EXISTING_PIDS}" ]; then
  echo "Stopping existing drove-controller process(es): ${EXISTING_PIDS}"
  kill ${EXISTING_PIDS}
  # Wait up to 10s for graceful shutdown
  for i in $(seq 1 10); do
    sleep 1
    STILL_RUNNING=$(pgrep -f "drove-controller" 2>/dev/null || true)
    if [ -z "${STILL_RUNNING}" ]; then
      echo "Process stopped."
      break
    fi
    if [ "${i}" -eq 10 ]; then
      echo "Process did not stop in time, sending SIGKILL..."
      kill -9 ${STILL_RUNNING} 2>/dev/null || true
      sleep 1
    fi
  done
fi

# ── Launch ─────────────────────────────────────────────────────────────────────
echo "──────────────────────────────────────────────────────"
echo " Starting Drove Controller (from classes)"
echo "  Config : ${CONFIG}"
echo "  Java   : $(${JAVA} -version 2>&1 | head -1)"
echo "──────────────────────────────────────────────────────"
echo ""

exec "${JAVA}" "${JVM_OPTS[@]}" \
  -classpath "${FULL_CP}" \
  com.phonepe.drove.controller.App \
  server "${CONFIG}"
