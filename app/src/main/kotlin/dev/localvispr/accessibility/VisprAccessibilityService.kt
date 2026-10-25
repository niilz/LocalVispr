package dev.localvispr.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import dev.localvispr.text.EditorContext
import dev.localvispr.text.TranscriptPostProcessor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// ==============================================================================
// System-wide text field bridge (`flagInputMethodEditor`, Android 13+)
// ==============================================================================
//
// Replicates the three-tier text injection pattern identified in the reference
// app without requiring LocalVispr to be the active soft keyboard:
//   1. `getInputMethod().getCurrentInputConnection().commitText(...)`
//   2. `AccessibilityNodeInfo.performAction(ACTION_SET_TEXT, ...)`
//   3. Clipboard copy + `ACTION_PASTE` as last resort.

enum class InsertionOutcome {
    COMMITTED_VIA_INPUT_CONNECTION,
    SET_ON_ACCESSIBILITY_NODE,
    COPIED_TO_CLIPBOARD,
}

class VisprAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        _instance.value = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        _hasEditableFocus.value = findFocusedEditableNode() != null
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (_instance.value === this) {
            _instance.value = null
            _hasEditableFocus.value = false
        }
        super.onDestroy()
    }

    // Reads the text immediately preceding the cursor so the cleanup LLM knows
    // the sentence context and `formatForInsertion` can decide on a leading space.
    fun readTextBeforeCursor(maxChars: Int = 200): String? {
        val ic = inputMethod?.currentInputConnection
        if (ic != null) {
            val surrounding = runCatching { ic.getSurroundingText(maxChars, 0, 0) }.getOrNull()
            if (surrounding != null) return surrounding.text?.toString()
        }
        return findFocusedEditableNode()?.text?.toString()?.takeLast(maxChars)
    }

    fun readEditorContext(maxChars: Int = 200): EditorContext {
        val pkg = rootInActiveWindow?.packageName?.toString()
        val ic = inputMethod?.currentInputConnection
        if (ic != null) {
            val surrounding =
                runCatching { ic.getSurroundingText(maxChars, maxChars, 0) }.getOrNull()
            if (surrounding != null) {
                val text = surrounding.text
                val start = surrounding.selectionStart.coerceIn(0, text.length)
                val end = surrounding.selectionEnd.coerceIn(start, text.length)
                return EditorContext(
                    packageName = pkg,
                    textBeforeCursor = text.subSequence(0, start).toString(),
                    textAfterCursor = text.subSequence(end, text.length).toString(),
                )
            }
        }
        val fallback = findFocusedEditableNode()?.text?.toString()?.takeLast(maxChars)
        return EditorContext(packageName = pkg, textBeforeCursor = fallback, textAfterCursor = null)
    }

    fun applyStreamingDelta(deleteBeforeCursor: Int, textToCommit: String): Boolean {
        val ic = inputMethod?.currentInputConnection ?: return false
        return runCatching {
                if (deleteBeforeCursor > 0) {
                    ic.deleteSurroundingText(deleteBeforeCursor, 0)
                }
                if (textToCommit.isNotEmpty()) {
                    ic.commitText(textToCommit, 1, null)
                }
                true
            }
            .getOrDefault(false)
    }

    fun insertText(cleanedText: String): InsertionOutcome {
        val before = readTextBeforeCursor(maxChars = 64)
        val formatted = TranscriptPostProcessor.formatForInsertion(cleanedText, before)

        // Tier 1: direct InputConnection commit (API 33+ accessibility IME channel).
        val ic = inputMethod?.currentInputConnection
        if (ic != null && runCatching { ic.commitText(formatted, 1, null) }.isSuccess) {
            return InsertionOutcome.COMMITTED_VIA_INPUT_CONNECTION
        }

        // Tier 2: append to the focused editable node via ACTION_SET_TEXT.
        val node = findFocusedEditableNode()
        if (node != null) {
            val existing = node.text?.toString().orEmpty()
            val combined = TranscriptPostProcessor.formatForInsertion(cleanedText, existing)
            val args =
                Bundle().apply {
                    putCharSequence(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                        existing + combined,
                    )
                }
            if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
                return InsertionOutcome.SET_ON_ACCESSIBILITY_NODE
            }
        }

        // Tier 3: copy to clipboard and attempt ACTION_PASTE on the focused node.
        val clipboard = getSystemService(ClipboardManager::class.java)
        clipboard?.setPrimaryClip(ClipData.newPlainText("LocalVispr", cleanedText))
        node?.performAction(AccessibilityNodeInfo.ACTION_PASTE)
        return InsertionOutcome.COPIED_TO_CLIPBOARD
    }

    private fun findFocusedEditableNode(): AccessibilityNodeInfo? {
        rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.let { focused ->
            if (focused.isEditable) return focused
        }
        for (window in windows) {
            val node = window.root?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (node != null && node.isEditable) return node
        }
        return null
    }

    companion object {
        private val _instance = MutableStateFlow<VisprAccessibilityService?>(null)
        val instance: StateFlow<VisprAccessibilityService?> = _instance.asStateFlow()

        private val _hasEditableFocus = MutableStateFlow(false)
        val hasEditableFocus: StateFlow<Boolean> = _hasEditableFocus.asStateFlow()
    }
}
