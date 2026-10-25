package dev.localvispr.text

data class EditorContext(
    val packageName: String? = null,
    val textBeforeCursor: String? = null,
    val textAfterCursor: String? = null,
)
