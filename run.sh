#!/usr/bin/env bash
# Builds the jar if needed and forwards all arguments to the CLI, e.g. ./run.sh serve --port 18765
set -euo pipefail
cd "$(dirname "$0")"
JAR=target/sug-spell.jar
if [[ ! -f "$JAR" ]] || [[ -n "$(find src pom.xml -newer "$JAR" -print -quit)" ]]; then
  mvn -q -B package -DskipTests
fi
exec java ${JAVA_OPTS:-} -jar "$JAR" "$@"
