# Session notes

- REA MCP tools (`inspect_android_package`, `search_android_classes`, …) are not exposed
  in the pi session; `~/.pi/agent/mcp.json` symlinks to the missing
  `/opt/dev-configs/agentic/mcp/pi-mcp.json`. JADX was driven directly through
  `tmp/tools/Decompile2.java` instead.
- A full JADX `save()` with 8 threads hits OOM on this 7 GB container; decompiling
  class by class with `unload()` works.
- The container has no Android SDK or Gradle (only `platform-tools`), and it is
  `aarch64` Linux, where Google ships no `aapt2`/build-tools.
- The project is not a git repository yet.
