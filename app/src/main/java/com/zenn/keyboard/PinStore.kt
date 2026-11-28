package com.zenn.keyboard

import android.content.Context
import org.json.JSONArray
import org.json.JSONException

private const val STORE_RECENT_KEY = "items"
private const val STORE_PINNED_KEY = "pinned"

// Riwayat yang tersimpan permanen di HP: hilang hanya saat dihapus atau melewati
// batas (yang terlama dulu). Item yang disematkan tidak terhapus otomatis,
// tanpa batas jumlah, dan selalu berada di atas.
open class PinStore(
    context: Context,
    prefsName: String,
    private val limit: Int,
    private val maxChars: Int = Int.MAX_VALUE,
) {
    private val prefs = context.applicationContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
    private val pinned = mutableListOf<String>()
    private val recent = mutableListOf<String>()

    // Dipanggil setiap isi berubah
    var onChange: (() -> Unit)? = null

    init {
        pinned.addAll(read(STORE_PINNED_KEY))
        recent.addAll(read(STORE_RECENT_KEY))
    }

    // Yang disematkan di atas, lalu yang terbaru
    fun snapshot(): List<String> = pinned + recent

    fun isPinned(item: String): Boolean = item in pinned

    fun add(item: String) {
        if (item.isBlank() || item.length > maxChars || item in pinned) return
        recent.remove(item)
        recent.add(0, item)
        trim()
        commit()
    }

    fun togglePin(item: String) {
        if (pinned.remove(item)) {
            // Lepas sematan: kembali jadi riwayat biasa dan ikut batas
            recent.add(0, item)
            trim()
        } else {
            recent.remove(item)
            pinned.add(0, item)
        }
        commit()
    }

    fun remove(item: String) {
        if (pinned.remove(item) || recent.remove(item)) commit()
    }

    // Menghapus semua riwayat biasa; yang disematkan tetap ada
    fun clearUnpinned() {
        if (recent.isEmpty()) return
        recent.clear()
        commit()
    }

    private fun trim() {
        while (recent.size > limit) recent.removeAt(recent.lastIndex)
    }

    private fun commit() {
        prefs.edit()
            .putString(STORE_RECENT_KEY, JSONArray(recent).toString())
            .putString(STORE_PINNED_KEY, JSONArray(pinned).toString())
            .apply()
        onChange?.invoke()
    }

    private fun read(key: String): List<String> {
        val raw = prefs.getString(key, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            List(array.length()) { array.getString(it) }
        } catch (e: JSONException) {
            // Data rusak: mulai dari kosong
            prefs.edit().remove(key).apply()
            emptyList()
        }
    }
}
