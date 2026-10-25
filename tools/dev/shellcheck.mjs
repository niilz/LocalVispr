// Runs the WASI build of ShellCheck from npm with plain Node. The dev container
// cannot download the native binary (GitHub is firewalled), but npm is reachable.
// Usage: node shellcheck.mjs <node_modules dir> <shellcheck args...>
import { readFile } from "node:fs/promises";
import { join } from "node:path";
import { WASI } from "node:wasi";

const [nodeModules, ...args] = process.argv.slice(2);
const wasmPath = join(nodeModules, "@vscode-shellcheck/shellcheck-wasm/dist/shellcheck.wasm");
const wasm = await WebAssembly.compile(await readFile(wasmPath));
const wasi = new WASI({
  version: "preview1",
  args: ["shellcheck", ...args],
  env: {},
  // Expose the host root so absolute script paths resolve unchanged.
  preopens: { "/": "/" },
  returnOnExit: true,
});
process.exitCode = wasi.start(await WebAssembly.instantiate(wasm, wasi.getImportObject()));
