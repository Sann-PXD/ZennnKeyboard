package com.zenn.keyboard

import android.content.ClipboardManager
import android.content.Context
import org.json.JSONArray
import org.json.JSONException

private const val PREFS_NAME = "clipboard_history"
private const val KEY_ITEMS = "items"
private const val MAX_ITEMS = 30
private const val MAX_ITEM_CHARS = 5_000

// Penanda "sensitif" dari Android 13+ (mis. salinan dari pengelola password)
private const val EXTRA_SENSITIVE = "android.content.extra.IS_SENSITIVE"

// Riwayat disimpan lokal di HP (penyimpanan privat aplikasi), maksimal 30 item
class ClipboardHistory(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val manager = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private val items = mutableListOf<String>()
    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener { captureCurrent() }

    // Dipanggil setiap isi riwayat berubah
    var onChange: (() -> Unit)? = null

    init {
        load()
    }

    fun start() = manager.addPrimaryClipChangedListener(clipListener)

    fun stop() = manager.removePrimaryClipChangedListener(clipListener)

    fun snapshot(): List<String> = items.toList()

    fun captureCurrent() {
        try {
            val clip = manager.primaryClip ?: return
            if (clip.itemCount == 0) return
            val sensitive = clip.description?.extras?.getBoolean(EXTRA_SENSITIVE) == true
            if (sensitive) return
            add(clip.getItemAt(0).coerceToText(appContext).toString())
        } catch (e: SecurityException) {
            // Android menolak akses clipboard saat ini; abaikan
        }
    }

    fun add(text: String) {
        if (text.isBlank() || text.length > MAX_ITEM_CHARS) return
        items.remove(text)
        items.add(0, text)
        while (items.size > MAX_ITEMS) items.removeAt(items.lastIndex)
        commit()
    }

    fun remove(text: String) {
        if (items.remove(text)) commit()
    }

    fun clear() {
        if (items.isEmpty()) return
        items.clear()
        commit()
    }

    private fun commit() {
        prefs.edit().putString(KEY_ITEMS, JSONArray(items).toString()).apply()
        onChange?.invoke()
    }

    private fun load() {
        val raw = prefs.getString(KEY_ITEMS, null) ?: return
        try {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) items.add(array.getString(i))
        } catch (e: JSONException) {
            // Data rusak: mulai dari riwayat kosong
            prefs.edit().remove(KEY_ITEMS).apply()
        }
    }
}
