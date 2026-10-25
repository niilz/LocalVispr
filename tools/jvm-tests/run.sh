#!/usr/bin/env bash
# Compile and run the pure-JVM unit tests without Gradle.
#
# The dev container cannot reach Google Maven / Maven Central, so Gradle cannot
# build here. The npm package `ktfmt` ships a fat jar that embeds the Kotlin
# compiler, stdlib, kotlin.test and kotlinx.coroutines, which is enough to
# compile and execute everything under app/src that does not touch Android or
# native third-party APIs. Android-dependent sources are filtered out by import.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
work_dir="$repo_root/tmp/jvm-tests"
ktfmt_dir="$repo_root/tmp/npm-tools"
compiler_jar="$ktfmt_dir/node_modules/ktfmt/lib/ktfmt.jar"

if [[ ! -f "$compiler_jar" ]]; then
  mkdir -p "$ktfmt_dir"
  (cd "$ktfmt_dir" && npm init -y >/dev/null && npm install --silent ktfmt@0.59.0)
fi

# Only sources free of platform imports can be compiled against the plain JVM.
platform_imports='^import (android|androidx|com\.k2fsa|com\.google\.ai\.edge|kotlinx\.coroutines\.android)\.'
mapfile -t sources < <(
  find "$repo_root/app/src/main/kotlin" "$repo_root/app/src/test/kotlin" -name '*.kt' -print0 2>/dev/null \
    | xargs -0 grep -L -E "$platform_imports"
)
sources+=("$repo_root/tools/jvm-tests/shim/org/junit/Test.kt" "$repo_root/tools/jvm-tests/TestRunner.kt")

rm -rf "$work_dir" && mkdir -p "$work_dir/classes"
java -cp "$compiler_jar" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -no-stdlib -cp "$compiler_jar" -d "$work_dir/classes" "${sources[@]}" 2>&1 \
  | grep -v '^warning:' || true

if [[ ! -f "$work_dir/classes/TestRunnerKt.class" ]]; then
  echo "Compilation failed" >&2
  exit 1
fi
java -cp "$compiler_jar:$work_dir/classes" TestRunnerKt "$work_dir/classes"
