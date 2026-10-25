package dev.localvispr.models

import java.io.File
import java.io.InputStream

data class WhisperModelFiles(val encoder: String, val decoder: String, val tokens: String)

class ModelRepository(private val rootDir: File) {

    fun modelDir(spec: ModelSpec): File = File(rootDir, spec.id)

    fun isInstalled(spec: ModelSpec): Boolean {
        val dir = modelDir(spec)
        return spec.requiredFiles.all { fileName ->
            val f = File(dir, fileName)
            f.isFile && f.length() > 0L
        }
    }

    fun whisperFiles(spec: ModelSpec): WhisperModelFiles {
        require(spec.kind == ModelKind.STT) { "${spec.id} is not an STT model" }
        val dir = modelDir(spec)
        val encoder = spec.requiredFiles.first { it.contains("encoder") }
        val decoder = spec.requiredFiles.first { it.contains("decoder") }
        val tokens = spec.requiredFiles.first { it.contains("tokens") }
        return WhisperModelFiles(
            encoder = File(dir, encoder).absolutePath,
            decoder = File(dir, decoder).absolutePath,
            tokens = File(dir, tokens).absolutePath,
        )
    }

    fun llmFile(spec: ModelSpec): File {
        require(spec.kind == ModelKind.LLM) { "${spec.id} is not an LLM model" }
        return File(modelDir(spec), spec.requiredFiles.single())
    }

    fun delete(spec: ModelSpec) {
        modelDir(spec).deleteRecursively()
    }

    // Downloads into `.part` files first and renames atomically so a killed
    // download never leaves `isInstalled()` returning true on a truncated file.
    fun install(
        spec: ModelSpec,
        openStream: (url: String) -> Pair<InputStream, Long>,
        onProgress: (bytesRead: Long, totalBytes: Long) -> Unit = { _, _ -> },
    ) {
        val dir = modelDir(spec).apply { mkdirs() }
        var cumulativeRead = 0L
        var knownTotal = 0L

        for ((fileName, url) in spec.downloadUrls) {
            val target = File(dir, fileName)
            val part = File(dir, "$fileName.part")
            val (input, contentLength) = openStream(url)
            if (contentLength > 0) knownTotal += contentLength

            input.use { stream ->
                part.outputStream().use { out ->
                    val buf = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val n = stream.read(buf)
                        if (n <= 0) break
                        out.write(buf, 0, n)
                        cumulativeRead += n
                        onProgress(cumulativeRead, knownTotal)
                    }
                }
            }
            if (!part.renameTo(target)) {
                part.copyTo(target, overwrite = true)
                part.delete()
            }
        }
    }
}
