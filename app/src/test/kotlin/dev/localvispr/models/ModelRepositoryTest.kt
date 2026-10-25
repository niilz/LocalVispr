package dev.localvispr.models

import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class ModelRepositoryTest {

    @Test
    fun reportsWhisperModelMissingUntilAllRequiredFilesExist() {
        val root = Files.createTempDirectory("localvispr-models").toFile()
        try {
            val repo = ModelRepository(root)
            val spec = ModelCatalog.WHISPER_TINY

            assertFalse(repo.isInstalled(spec))

            // Creating only two of the three required files is not enough.
            val dir = repo.modelDir(spec).apply { mkdirs() }
            dir.resolve("tiny-encoder.int8.onnx").writeBytes(byteArrayOf(1))
            dir.resolve("tiny-decoder.int8.onnx").writeBytes(byteArrayOf(1))
            assertFalse(repo.isInstalled(spec))

            dir.resolve("tiny-tokens.txt").writeText("a 0\n")
            assertTrue(repo.isInstalled(spec))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun ignoresZeroByteFilesLeftBehindByAbortedDownloads() {
        val root = Files.createTempDirectory("localvispr-models").toFile()
        try {
            val repo = ModelRepository(root)
            val spec = ModelCatalog.QWEN3_0_6B
            val dir = repo.modelDir(spec).apply { mkdirs() }
            dir.resolve(spec.requiredFiles.single()).writeBytes(ByteArray(0))

            assertFalse(repo.isInstalled(spec))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun resolvesWhisperFilePathsUnderTheModelDirectory() {
        val root = Files.createTempDirectory("localvispr-models").toFile()
        try {
            val repo = ModelRepository(root)
            val files = repo.whisperFiles(ModelCatalog.WHISPER_BASE)
            assertTrue(files.encoder.endsWith("whisper-base/base-encoder.int8.onnx"))
            assertTrue(files.decoder.endsWith("whisper-base/base-decoder.int8.onnx"))
            assertTrue(files.tokens.endsWith("whisper-base/base-tokens.txt"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun catalogProvidesSmallDefaultsAndAllowsLookingUpEveryModelById() {
        assertEquals(ModelKind.STT, ModelCatalog.defaultStt.kind)
        assertEquals(ModelKind.LLM, ModelCatalog.defaultLlm.kind)
        // None of our catalog entries may require an account or login token.
        assertTrue(ModelCatalog.all.all { it.downloadUrls.isNotEmpty() })
        ModelCatalog.all.forEach { spec -> assertEquals(spec, ModelCatalog.byId(spec.id)) }
    }

    @Test
    fun deletesAnInstalledModelCleanly() {
        val root = Files.createTempDirectory("localvispr-models").toFile()
        try {
            val repo = ModelRepository(root)
            val spec = ModelCatalog.SMOLLM2_135M
            val dir = repo.modelDir(spec).apply { mkdirs() }
            dir.resolve(spec.requiredFiles.single()).writeBytes(byteArrayOf(42))
            assertTrue(repo.isInstalled(spec))

            repo.delete(spec)
            assertFalse(repo.isInstalled(spec))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun installsAllFilesAndReportsProgress() {
        val root = Files.createTempDirectory("localvispr-models").toFile()
        try {
            val repo = ModelRepository(root)
            val spec = ModelCatalog.WHISPER_TINY
            val payload = ByteArray(1024) { 7 }
            var lastRead = 0L

            repo.install(
                spec = spec,
                openStream = { payload.inputStream() to payload.size.toLong() },
                onProgress = { read, _ -> lastRead = read },
            )

            assertTrue(repo.isInstalled(spec))
            assertEquals(3L * payload.size, lastRead)
        } finally {
            root.deleteRecursively()
        }
    }
}
