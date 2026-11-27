package com.zenn.keyboard

import android.content.Context
import org.json.JSONArray
import org.json.JSONException

private const val RECENTS_PREFS = "emoji_recents"
private const val RECENTS_KEY = "items"
private const val RECENTS_MAX = 32

// Emoji terakhir dipakai, disimpan lokal di HP (terbaru di depan)
class EmojiRecents(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(RECENTS_PREFS, Context.MODE_PRIVATE)
    private val items = mutableListOf<String>()

    init {
        load()
    }

    fun snapshot(): List<String> = items.toList()

    fun add(emoji: String) {
        items.remove(emoji)
        items.add(0, emoji)
        while (items.size > RECENTS_MAX) items.removeAt(items.lastIndex)
        prefs.edit().putString(RECENTS_KEY, JSONArray(items).toString()).apply()
    }

    private fun load() {
        val raw = prefs.getString(RECENTS_KEY, null) ?: return
        try {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) items.add(array.getString(i))
        } catch (e: JSONException) {
            // Data rusak: mulai dari kosong
            prefs.edit().remove(RECENTS_KEY).apply()
        }
    }
}
