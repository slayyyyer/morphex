#!/usr/bin/env bash
# Puts the locally pinned toolchain (downloaded into .tools/, not committed) on PATH.
# Usage: source scripts/dev-env.sh
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

JDK_DIR="$(ls -d "$ROOT"/.tools/jdk-* 2>/dev/null | sort -V | tail -1 || true)"
MVN_DIR="$(ls -d "$ROOT"/.tools/apache-maven-* 2>/dev/null | sort -V | tail -1 || true)"

if [ -z "${JDK_DIR}" ]; then
  echo "dev-env: no JDK found under $ROOT/.tools (expected .tools/jdk-*)" >&2
  return 1 2>/dev/null || exit 1
fi

export JAVA_HOME="$JDK_DIR"
export PATH="$JAVA_HOME/bin:$PATH"
if [ -n "${MVN_DIR}" ]; then
  export PATH="$MVN_DIR/bin:$PATH"
  export MAVEN_HOME="$MVN_DIR"
fi

echo "dev-env: JAVA_HOME=$JAVA_HOME"
[ -n "${MVN_DIR}" ] && echo "dev-env: MAVEN_HOME=$MAVEN_HOME"
java -version 2>&1 | head -1
