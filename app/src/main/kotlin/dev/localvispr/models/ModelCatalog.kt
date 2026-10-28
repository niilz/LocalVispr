package dev.localvispr.models

// ==============================================================================
// On-device model catalog (STT + cleanup LLM)
// ==============================================================================
//
// Design constraint from the user: no account, no API key, no gated downloads.
// Every URL below points to a publicly readable artifact on Hugging Face.
// Note that Google's Gemma repositories on Hugging Face require a login to
// accept the Gemma license, so our out-of-the-box LLM options use SmolLM2 and
// Qwen3 from `litert-community`, which are un-gated. A user who already has a
// Gemma `.litertlm` file can still drop it into the model directory on device.

enum class ModelKind {
    STT,
    LLM,
}

data class ModelSpec(
    val id: String,
    val title: String,
    val subtitle: String,
    val kind: ModelKind,
    val approxSizeMb: Int,
    // Relative file names expected inside `models/<id>/` once installed.
    val requiredFiles: List<String>,
    // Direct HTTP URLs keyed by target file name inside `models/<id>/`.
    // Using per-file URLs avoids pulling a bzip2 decompressor into the app.
    val downloadUrls: Map<String, String>,
)

object ModelCatalog {

    private const val HF_WHISPER_TINY =
        "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-tiny/resolve/main"
    private const val HF_WHISPER_BASE =
        "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-base/resolve/main"

    val WHISPER_TINY =
        ModelSpec(
            id = "whisper-tiny",
            title = "Whisper Tiny (int8, DE/EN)",
            subtitle = "Fastest offline speech recognition (~110 MB)",
            kind = ModelKind.STT,
            approxSizeMb = 110,
            requiredFiles =
                listOf("tiny-encoder.int8.onnx", "tiny-decoder.int8.onnx", "tiny-tokens.txt"),
            downloadUrls =
                mapOf(
                    "tiny-encoder.int8.onnx" to "$HF_WHISPER_TINY/tiny-encoder.int8.onnx",
                    "tiny-decoder.int8.onnx" to "$HF_WHISPER_TINY/tiny-decoder.int8.onnx",
                    "tiny-tokens.txt" to "$HF_WHISPER_TINY/tiny-tokens.txt",
                ),
        )

    val WHISPER_BASE =
        ModelSpec(
            id = "whisper-base",
            title = "Whisper Base (int8, DE/EN)",
            subtitle = "Higher accuracy, slower on older phones (~195 MB)",
            kind = ModelKind.STT,
            approxSizeMb = 195,
            requiredFiles =
                listOf("base-encoder.int8.onnx", "base-decoder.int8.onnx", "base-tokens.txt"),
            downloadUrls =
                mapOf(
                    "base-encoder.int8.onnx" to "$HF_WHISPER_BASE/base-encoder.int8.onnx",
                    "base-decoder.int8.onnx" to "$HF_WHISPER_BASE/base-decoder.int8.onnx",
                    "base-tokens.txt" to "$HF_WHISPER_BASE/base-tokens.txt",
                ),
        )

    val SMOLLM2_135M =
        ModelSpec(
            id = "smollm2-135m",
            title = "SmolLM2 135M Instruct",
            subtitle = "Tiny cleanup LLM, very fast (~140 MB)",
            kind = ModelKind.LLM,
            approxSizeMb = 140,
            requiredFiles = listOf("SmolLM2_135M_Instruct.litertlm"),
            downloadUrls =
                mapOf(
                    "SmolLM2_135M_Instruct.litertlm" to
                        "https://huggingface.co/litert-community/SmolLM2-135M-Instruct/resolve/main/SmolLM2_135M_Instruct.litertlm"
                ),
        )

    val QWEN3_0_6B =
        ModelSpec(
            id = "qwen3-0.6b",
            title = "Qwen3 0.6B (int4, DE/EN)",
            subtitle = "Better German & English rewrites (~475 MB)",
            kind = ModelKind.LLM,
            approxSizeMb = 475,
            requiredFiles = listOf("qwen3_0_6b_mixed_int4.litertlm"),
            downloadUrls =
                mapOf(
                    "qwen3_0_6b_mixed_int4.litertlm" to
                        "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/qwen3_0_6b_mixed_int4.litertlm"
                ),
        )

    val GEMMA_4_E2B =
        ModelSpec(
            id = "gemma-4-e2b",
            title = "Gemma 4 E2B Instruct (CPU)",
            subtitle = "High-quality formatting & structuring (~2.6 GB)",
            kind = ModelKind.LLM,
            approxSizeMb = 2590,
            requiredFiles = listOf("gemma-4-E2B-it.litertlm"),
            downloadUrls =
                mapOf(
                    "gemma-4-E2B-it.litertlm" to
                        "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm"
                ),
        )

    val sttModels: List<ModelSpec> = listOf(WHISPER_TINY, WHISPER_BASE)
    val llmModels: List<ModelSpec> = listOf(SMOLLM2_135M, QWEN3_0_6B, GEMMA_4_E2B)
    val all: List<ModelSpec> = sttModels + llmModels

    // Start with the smallest models so the first-run download stays quick.
    val defaultStt: ModelSpec = WHISPER_TINY
    val defaultLlm: ModelSpec = SMOLLM2_135M

    fun byId(id: String): ModelSpec? = all.firstOrNull { it.id == id }
}
