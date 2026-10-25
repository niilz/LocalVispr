package dev.localvispr.history

import java.io.File

data class DictationHistoryEntry(
    val timestampEpochMs: Long,
    val text: String,
    val languageCode: String,
)

/**
 * File-backed persistence store for recorded dictations.
 *
 * Entries are stored in a newline-delimited, tab-separated text file so that reading and writing
 * require no Android or external JSON libraries. Newlines and tabs inside transcribed text or
 * language tags are normalized to spaces to guarantee robust single-line record integrity.
 */
class DictationHistoryStore(private val storageFile: File, private val maxEntries: Int = 100) {
    private val lock = Any()

    init {
        require(maxEntries > 0) { "maxEntries must be positive" }
    }

    fun record(
        text: String,
        languageCode: String,
        timestampEpochMs: Long = System.currentTimeMillis(),
    ) {
        synchronized(lock) {
            val current = readAll()
            val sanitizedText = sanitizeField(text)
            val sanitizedLang = sanitizeField(languageCode)
            val newEntry =
                DictationHistoryEntry(
                    timestampEpochMs = timestampEpochMs,
                    text = sanitizedText,
                    languageCode = sanitizedLang,
                )
            // Prepend new entry so newest is at the top, then trim to maxEntries
            val updated = (listOf(newEntry) + current).take(maxEntries)
            writeAll(updated)
        }
    }

    fun list(): List<DictationHistoryEntry> {
        synchronized(lock) {
            return readAll()
        }
    }

    fun search(query: String): List<DictationHistoryEntry> {
        val trimmedQuery = query.trim()
        synchronized(lock) {
            val all = readAll()
            if (trimmedQuery.isEmpty()) {
                return all
            }
            return all.filter { entry ->
                entry.text.contains(trimmedQuery, ignoreCase = true) ||
                    entry.languageCode.contains(trimmedQuery, ignoreCase = true)
            }
        }
    }

    fun clear() {
        synchronized(lock) {
            if (storageFile.exists()) {
                storageFile.delete()
            }
        }
    }

    private fun readAll(): List<DictationHistoryEntry> {
        if (!storageFile.exists() || storageFile.length() == 0L) {
            return emptyList()
        }
        val entries = mutableListOf<DictationHistoryEntry>()
        storageFile.forEachLine { line ->
            val parts = line.split('\t')
            if (parts.size >= 3) {
                val timestamp = parts[0].toLongOrNull()
                if (timestamp != null) {
                    entries.add(
                        DictationHistoryEntry(
                            timestampEpochMs = timestamp,
                            languageCode = parts[1],
                            text = parts.subList(2, parts.size).joinToString("\t"),
                        )
                    )
                }
            }
        }
        return entries
    }

    private fun writeAll(entries: List<DictationHistoryEntry>) {
        val parent = storageFile.parentFile
        if (parent != null && !parent.exists()) {
            parent.mkdirs()
        }
        storageFile.bufferedWriter().use { writer ->
            for (entry in entries) {
                writer.write("${entry.timestampEpochMs}\t${entry.languageCode}\t${entry.text}\n")
            }
        }
    }

    private fun sanitizeField(value: String): String {
        return value.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ')
    }
}
