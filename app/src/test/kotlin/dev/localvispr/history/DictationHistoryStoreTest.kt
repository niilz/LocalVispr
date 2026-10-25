package dev.localvispr.history

import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class DictationHistoryStoreTest {

    private fun createTempStoreFile(): File {
        val file = File.createTempFile("history-test-", ".tsv")
        file.deleteOnExit()
        file.delete()
        return file
    }

    @Test
    fun ordersEntriesNewestFirst() {
        val file = createTempStoreFile()
        val store = DictationHistoryStore(file)

        store.record("First", "de", timestampEpochMs = 1_000L)
        store.record("Second", "en", timestampEpochMs = 2_000L)
        store.record("Third", "fr", timestampEpochMs = 3_000L)

        val entries = store.list()
        assertEquals(3, entries.size)
        assertEquals("Third", entries[0].text)
        assertEquals(3_000L, entries[0].timestampEpochMs)
        assertEquals("Second", entries[1].text)
        assertEquals("First", entries[2].text)
    }

    @Test
    fun persistsEntriesAcrossInstances() {
        val file = createTempStoreFile()
        val store1 = DictationHistoryStore(file)
        store1.record("Persistent dictation", "de", timestampEpochMs = 10_000L)

        val store2 = DictationHistoryStore(file)
        val entries = store2.list()

        assertEquals(1, entries.size)
        assertEquals("Persistent dictation", entries[0].text)
        assertEquals("de", entries[0].languageCode)
        assertEquals(10_000L, entries[0].timestampEpochMs)
    }

    @Test
    fun searchesCaseInsensitively() {
        val file = createTempStoreFile()
        val store = DictationHistoryStore(file)

        store.record("Meeting notes about Android", "en", timestampEpochMs = 1_000L)
        store.record("Shopping list with apples", "de", timestampEpochMs = 2_000L)
        store.record("Android architecture review", "en", timestampEpochMs = 3_000L)

        val results = store.search("android")
        assertEquals(2, results.size)
        assertEquals("Android architecture review", results[0].text)
        assertEquals("Meeting notes about Android", results[1].text)

        val emptyQueryResults = store.search("")
        assertEquals(3, emptyQueryResults.size)

        val langQueryResults = store.search("DE")
        assertEquals(1, langQueryResults.size)
        assertEquals("Shopping list with apples", langQueryResults[0].text)
    }

    @Test
    fun enforcesMaxEntriesBound() {
        val file = createTempStoreFile()
        val store = DictationHistoryStore(file, maxEntries = 3)

        store.record("1", "en", 100L)
        store.record("2", "en", 200L)
        store.record("3", "en", 300L)
        store.record("4", "en", 400L)
        store.record("5", "en", 500L)

        val entries = store.list()
        assertEquals(3, entries.size)
        assertEquals("5", entries[0].text)
        assertEquals("4", entries[1].text)
        assertEquals("3", entries[2].text)
    }

    @Test
    fun clearRemovesAllEntries() {
        val file = createTempStoreFile()
        val store = DictationHistoryStore(file)

        store.record("Some text", "de", 1_000L)
        assertEquals(1, store.list().size)

        store.clear()
        assertTrue(store.list().isEmpty())

        // Also persistent across reload
        val reloaded = DictationHistoryStore(file)
        assertTrue(reloaded.list().isEmpty())
    }
}
