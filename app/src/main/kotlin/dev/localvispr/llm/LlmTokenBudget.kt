package dev.localvispr.llm

// ==============================================================================
// Token budget for the on-device LLM
// ==============================================================================
//
// LiteRT-LM's `maxNumTokens` is shared by the prompt *and* the generated answer.
// Our system prompt alone (style rules + email example) costs ~330 tokens, so the
// old budget of 512 left almost no room for a formatted email and silently cut
// longer dictations short. 2048 fits a ~90 s German email with headroom.
//
// Kept free of LiteRT imports so the pure-JVM tests can check the budget against
// the real prompts built by `TranscriptPostProcessor`.

object LlmTokenBudget {
    const val MAX_NUM_TOKENS = 2048

    // Some `.litertlm` files are compiled with a smaller KV-cache. If the engine
    // refuses the preferred budget we retry once with the previous, known-good
    // value instead of dropping to rules-only mode.
    const val FALLBACK_MAX_NUM_TOKENS = 512
}
