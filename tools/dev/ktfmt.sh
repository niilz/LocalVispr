#!/usr/bin/env bash
# Format all Kotlin sources with ktfmt (kotlinlang style), matching the Spotless
# configuration used by Gradle on the host. Works offline-from-Maven via npm.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
install_dir="$repo_root/tmp/npm-tools"

if [[ ! -f "$install_dir/node_modules/ktfmt/lib/ktfmt.jar" ]]; then
  mkdir -p "$install_dir"
  (cd "$install_dir" && npm init -y >/dev/null && npm install --silent ktfmt@0.59.0)
fi

mapfile -t files < <(cd "$repo_root" && git ls-files --cached --others --exclude-standard '*.kt' '*.kts' | sed "s|^|$repo_root/|")
java -jar "$install_dir/node_modules/ktfmt/lib/ktfmt.jar" --kotlinlang-style "${files[@]}"
