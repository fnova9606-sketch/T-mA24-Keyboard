package com.tma24.keyboard

import android.content.Context
import android.content.SharedPreferences

/**
 * ClipboardManager
 *
 * Ordered history of clipboard entries, persisted in SharedPreferences.
 * Maximum 50 entries. Fully offline — zero network use.
 */
class ClipboardManager(private val context: Context) {

    companion object {
        private const val PREFS_NAME  = "tma24_clipboard"
        private const val KEY_ENTRIES = "entries"
        private const val SEPARATOR   = "\u0001"
        private const val MAX_ENTRIES = 50
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val entries: MutableList<String> = loadFromPrefs()

    fun addEntry(text: String) {
        if (text.isBlank() || text.length > 5000) return
        entries.remove(text)
        entries.add(0, text)
        while (entries.size > MAX_ENTRIES) entries.removeAt(entries.size - 1)
        persist()
    }

    fun removeEntry(text: String) {
        entries.remove(text)
        persist()
    }

    fun getRecentEntries(count: Int): List<String> =
        entries.take(count.coerceAtMost(MAX_ENTRIES))

    fun hasEntries(): Boolean = entries.isNotEmpty()

    fun clearAll() {
        entries.clear()
        persist()
    }

    private fun persist() {
        prefs.edit()
            .putString(KEY_ENTRIES, entries.joinToString(SEPARATOR))
            .apply()
    }

    private fun loadFromPrefs(): MutableList<String> {
        val raw = prefs.getString(KEY_ENTRIES, "") ?: ""
        if (raw.isBlank()) return mutableListOf()
        return raw.split(SEPARATOR).filter { it.isNotBlank() }.toMutableList()
    }
}