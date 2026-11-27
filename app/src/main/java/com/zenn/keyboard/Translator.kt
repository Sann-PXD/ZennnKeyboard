package com.zenn.keyboard

import org.json.JSONArray
import org.json.JSONException
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

private const val ENDPOINT = "https://translate.googleapis.com/translate_a/single"
private const val TIMEOUT_MS = 8_000
private const val HTTP_TOO_MANY_REQUESTS = 429

data class Translation(val text: String, val detectedLang: String)

// Pesan sudah siap ditampilkan ke pengguna
class TranslateException(message: String) : Exception(message)

object Languages {
    // Tambah bahasa baru cukup dengan menambah satu pasangan kode to nama
    val all = listOf(
        "en" to "Inggris", "id" to "Indonesia", "jw" to "Jawa", "su" to "Sunda",
        "ms" to "Melayu", "ar" to "Arab", "zh-CN" to "Mandarin", "zh-TW" to "Mandarin (Trad.)",
        "ja" to "Jepang", "ko" to "Korea", "hi" to "Hindi", "th" to "Thai",
        "vi" to "Vietnam", "tl" to "Filipino", "es" to "Spanyol", "fr" to "Prancis",
        "de" to "Jerman", "it" to "Italia", "pt" to "Portugis", "nl" to "Belanda",
        "ru" to "Rusia", "tr" to "Turki", "pl" to "Polandia", "uk" to "Ukraina",
        "sv" to "Swedia", "no" to "Norwegia", "da" to "Denmark", "fi" to "Finlandia",
        "el" to "Yunani", "iw" to "Ibrani", "cs" to "Ceko", "hu" to "Hungaria",
        "ro" to "Rumania", "bn" to "Bengali", "ur" to "Urdu", "fa" to "Persia",
        "sw" to "Swahili", "ta" to "Tamil", "te" to "Telugu", "my" to "Myanmar",
        "km" to "Khmer", "ne" to "Nepal", "si" to "Sinhala", "af" to "Afrikaans",
        "la" to "Latin",
    )

    private val names = all.toMap()

    fun nameOf(code: String): String = names[code] ?: code
}

object Translator {
    // Memakai endpoint Google Translate tidak resmi (tanpa API key). Kalau suatu saat
    // berhenti bekerja, cukup ganti isi fungsi ini; bagian lain tidak perlu diubah.
    // Panggil dari thread latar belakang, bukan thread utama.
    fun translate(text: String, target: String): Translation {
        val query = URLEncoder.encode(text, "UTF-8")
        val url = URL("$ENDPOINT?client=gtx&sl=auto&tl=$target&dt=t&q=$query")
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("User-Agent", "Mozilla/5.0")
            val code = connection.responseCode
            if (code == HTTP_TOO_MANY_REQUESTS) {
                throw TranslateException("Terlalu banyak permintaan, coba lagi sebentar lagi.")
            }
            if (code != HttpURLConnection.HTTP_OK) {
                throw TranslateException("Server menolak permintaan (kode $code).")
            }
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            return parse(body)
        } catch (e: IOException) {
            throw TranslateException("Tidak ada koneksi internet.")
        } finally {
            connection.disconnect()
        }
    }

    // Balasan berbentuk [[["hasil","asli",...], ...], null, "kode-bahasa-asal", ...]
    private fun parse(body: String): Translation {
        try {
            val root = JSONArray(body)
            val segments = root.getJSONArray(0)
            val out = StringBuilder()
            for (i in 0 until segments.length()) {
                val segment = segments.getJSONArray(i)
                if (!segment.isNull(0)) out.append(segment.getString(0))
            }
            if (out.isEmpty()) throw TranslateException("Tidak ada hasil terjemahan.")
            return Translation(out.toString(), root.optString(2))
        } catch (e: JSONException) {
            throw TranslateException("Balasan server tidak dikenali.")
        }
    }
}
