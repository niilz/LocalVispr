# Session notes

- REA MCP tools (`inspect_android_package`, `search_android_classes`, …) are not exposed
  in the pi session; `~/.pi/agent/mcp.json` symlinks to the missing
  `/opt/dev-configs/agentic/mcp/pi-mcp.json`. JADX was driven directly through
  `tmp/tools/Decompile2.java` instead.
- A full JADX `save()` with 8 threads hits OOM on this 7 GB container; decompiling
  class by class with `unload()` works.
- Outbound HTTPS from the dev container is blocked for `dl.google.com`,
  `repo.maven.apache.org`, `services.gradle.org`, `github.com`, and
  `huggingface.co`; only `registry.npmjs.org` is reachable. Full Gradle/SDK builds
  must therefore run on the host, while pure-JVM unit tests, `ktfmt`, and
  ShellCheck run inside the container via npm packages (`tools/`).
- Google's Gemma repositories on Hugging Face are gated behind a login to accept
  the Gemma license. To honour the "no account required" requirement, the
  built-in LLM catalog uses public `litert-community` models (`SmolLM2-135M-Instruct`
  and `Qwen3-0.6B`).
- Deferred option: an additional `InputMethodService` (custom keyboard / IME) can
  be added alongside the floating bubble if the user wants a keyboard mic button
  later on.
