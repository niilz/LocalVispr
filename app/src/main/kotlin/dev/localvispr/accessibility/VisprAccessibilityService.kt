package dev.localvispr.accessibility

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import dev.localvispr.service.FloatingBubbleService
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
        _hasEditableFocus.value = isInputActive()
        ensureBubbleServiceRunning()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val active = isInputActive()
        _hasEditableFocus.value = active
        if (active && !FloatingBubbleService.running.value) {
            ensureBubbleServiceRunning()
        }
    }

    private fun ensureBubbleServiceRunning() {
        val hasMic =
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        if (hasMic && Settings.canDrawOverlays(this) && !FloatingBubbleService.running.value) {
            runCatching { FloatingBubbleService.start(this) }
        }
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
        // Find the actual application package, ignoring soft keyboard windows
        val appWindow =
            runCatching { windows }
                .getOrNull()
                ?.firstOrNull {
                    it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION
                }
        val pkg =
            appWindow?.root?.packageName?.toString()
                ?: rootInActiveWindow?.packageName?.toString()
                ?: findFocusedEditableNode()?.packageName?.toString()

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

    // Matches Wispr Flow's `SoftKeyboardStateTracker` + `FocusedNodeFinder`:
    // input is considered active when the soft keyboard window (`TYPE_INPUT_METHOD`)
    // is on screen OR an editable / `ACTION_SET_TEXT`-capable node holds input focus
    // (the latter also covers Jetpack Compose `OutlinedTextField`).
    private fun isInputActive(): Boolean {
        val allWindows = runCatching { windows }.getOrNull().orEmpty()
        if (allWindows.any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }) {
            return true
        }
        return findFocusedEditableNode(allWindows) != null
    }

    private fun findFocusedEditableNode(
        allWindows: List<AccessibilityWindowInfo> = runCatching { windows }.getOrNull().orEmpty()
    ): AccessibilityNodeInfo? {
        rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.let { focused ->
            if (isNodeEditable(focused)) return focused
        }
        for (window in allWindows) {
            val node = window.root?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (node != null && isNodeEditable(node)) return node
        }
        return null
    }

    private fun isNodeEditable(node: AccessibilityNodeInfo): Boolean =
        node.isEditable || node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_SET_TEXT }

    companion object {
        private val _instance = MutableStateFlow<VisprAccessibilityService?>(null)
        val instance: StateFlow<VisprAccessibilityService?> = _instance.asStateFlow()

        private val _hasEditableFocus = MutableStateFlow(false)
        val hasEditableFocus: StateFlow<Boolean> = _hasEditableFocus.asStateFlow()

        fun isEnabled(context: android.content.Context): Boolean {
            if (_instance.value != null) return true
            val expected = "${context.packageName}/${VisprAccessibilityService::class.java.name}"
            val enabledServices =
                android.provider.Settings.Secure.getString(
                    context.contentResolver,
                    android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                ) ?: return false
            return enabledServices.split(':').any { it.equals(expected, ignoreCase = true) }
        }
    }
}
