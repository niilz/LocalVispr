#!/usr/bin/env bash
# Lint shell scripts with ShellCheck (WASM build from npm). Defaults to all
# tracked *.sh files when no arguments are given.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
install_dir="$repo_root/tmp/npm-tools"

if [[ ! -d "$install_dir/node_modules/@vscode-shellcheck/shellcheck-wasm" ]]; then
  mkdir -p "$install_dir"
  (cd "$install_dir" && npm init -y >/dev/null && npm install --silent @vscode-shellcheck/shellcheck-wasm)
fi

if [[ $# -eq 0 ]]; then
  mapfile -t scripts < <(cd "$repo_root" && git ls-files --cached --others --exclude-standard '*.sh' | sed "s|^|$repo_root/|")
  set -- "${scripts[@]}"
fi

# ShellCheck sees the host filesystem through a WASI preopen, so absolute paths are required.
args=()
for script in "$@"; do args+=("$(realpath "$script")"); done
node --no-warnings "$repo_root/tools/dev/shellcheck.mjs" "$install_dir/node_modules" "${args[@]}"
