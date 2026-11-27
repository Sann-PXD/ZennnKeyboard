package com.zenn.keyboard

import android.graphics.Paint
import java.util.Locale

private const val VARIATION_SELECTOR = "\uFE0F"
private const val REGIONAL_INDICATOR_A = 0x1F1E6

enum class EmojiCategory(val icon: String, val title: String) {
    RECENT("🕘", "Terbaru"),
    FACES("😀", "Wajah"),
    PEOPLE("👋", "Orang & tangan"),
    NATURE("🐶", "Hewan & alam"),
    FOOD("🍔", "Makanan"),
    ACTIVITY("⚽", "Aktivitas"),
    TRAVEL("🚗", "Perjalanan"),
    OBJECTS("💡", "Objek"),
    SYMBOLS("\u2764\uFE0F", "Simbol"),
    FLAGS("🏁", "Bendera"),
}

// Daftar kode unicode per kategori. Yang ditampilkan hanya emoji yang bisa
// digambar font HP (dicek lewat Paint.hasGlyph), jadi tidak ada kotak kosong.
private class Spec(val ranges: List<IntRange>, val singles: List<Int> = emptyList())

private val SPECS = mapOf(
    EmojiCategory.FACES to Spec(
        ranges = listOf(
            0x1F600..0x1F644, 0x1F910..0x1F917, 0x1F920..0x1F92F, 0x1F970..0x1F97A,
            0x1FAE0..0x1FAE8, 0x1F479..0x1F47F, 0x1F4A9..0x1F4A9, 0x1F9D0..0x1F9D0,
        ),
        singles = listOf(0x263A, 0x2639),
    ),
    EmojiCategory.PEOPLE to Spec(
        ranges = listOf(
            0x1F645..0x1F64F, 0x1F440..0x1F450, 0x1F466..0x1F478, 0x1F47C..0x1F47C,
            0x1F481..0x1F487, 0x1F4AA..0x1F4AA, 0x1F590..0x1F590, 0x1F595..0x1F596,
            0x1F918..0x1F91F, 0x1F930..0x1F939, 0x1F9B5..0x1F9B6, 0x1F9D1..0x1F9DF,
            0x1F9E0..0x1F9E0, 0x1FAF0..0x1FAF8,
        ),
        singles = listOf(0x261D, 0x270A, 0x270B, 0x270C, 0x270D),
    ),
    EmojiCategory.NATURE to Spec(
        ranges = listOf(
            0x1F300..0x1F320, 0x1F330..0x1F344, 0x1F400..0x1F43E,
            0x1F980..0x1F9AE, 0x1FAB0..0x1FABA,
        ),
        singles = listOf(0x2600, 0x2601, 0x2614, 0x26C4, 0x26A1, 0x2744, 0x2B50, 0x2728),
    ),
    EmojiCategory.FOOD to Spec(
        ranges = listOf(
            0x1F32D..0x1F32F, 0x1F345..0x1F37F, 0x1F950..0x1F96B,
            0x1F9C0..0x1F9CB, 0x1FAD0..0x1FADB,
        ),
        singles = listOf(0x2615),
    ),
    EmojiCategory.ACTIVITY to Spec(
        ranges = listOf(
            0x1F380..0x1F393, 0x1F3A0..0x1F3CA, 0x1F3CF..0x1F3D3, 0x1F93C..0x1F93E,
            0x1F945..0x1F945, 0x1F947..0x1F94F, 0x1FA80..0x1FA86,
        ),
        singles = listOf(0x26BD, 0x26BE, 0x26F3, 0x265F),
    ),
    EmojiCategory.TRAVEL to Spec(
        ranges = listOf(
            0x1F680..0x1F6C5, 0x1F3E0..0x1F3F0, 0x1F5FB..0x1F5FF,
            0x1F6D5..0x1F6D7, 0x1F6F4..0x1F6FC,
        ),
        singles = listOf(0x2708, 0x26F5, 0x26FA, 0x26FD, 0x2693, 0x26EA),
    ),
    EmojiCategory.OBJECTS to Spec(
        ranges = listOf(
            0x1F451..0x1F465, 0x1F488..0x1F492, 0x1F4A1..0x1F4A1, 0x1F4AC..0x1F4AE,
            0x1F4B0..0x1F4FC, 0x1F50B..0x1F517, 0x1F550..0x1F567, 0x1F9E2..0x1F9FF,
            0x1FA70..0x1FA7C, 0x1FA90..0x1FAAC,
        ),
        singles = listOf(0x231A, 0x231B, 0x260E, 0x2709, 0x270F, 0x2702, 0x2699),
    ),
    EmojiCategory.SYMBOLS to Spec(
        ranges = listOf(
            0x1F493..0x1F49F, 0x1F90D..0x1F90E, 0x1F9E1..0x1F9E1, 0x1F4A0..0x1F4A0,
            0x1F4A2..0x1F4AB, 0x1F4AF..0x1F4AF, 0x1F500..0x1F50A, 0x1F518..0x1F53D,
            0x1F7E0..0x1F7EB, 0x1F6AB..0x1F6AB,
        ),
        singles = listOf(
            0x2764, 0x2705, 0x274C, 0x2757, 0x2753, 0x2714, 0x2716, 0x2795, 0x2796,
            0x2797, 0x267B, 0x26A0, 0x2611, 0x2733, 0x2734, 0x203C, 0x2049, 0x2B55,
            0x27A1, 0x2B05, 0x2B06, 0x2B07, 0x2194, 0x2195, 0x25B6, 0x25C0, 0x2665,
            0x2660, 0x2663, 0x2666, 0x00A9, 0x00AE, 0x2122,
        ),
    ),
)

private val FLAG_EXTRAS = listOf(0x1F3C1, 0x1F6A9, 0x1F38C, 0x1F3F4)

object EmojiCatalog {
    private val paint = Paint()
    private val cache = mutableMapOf<EmojiCategory, List<String>>()

    // Hasil disimpan di cache, jadi pemeriksaan font hanya terjadi sekali per kategori
    fun emojis(category: EmojiCategory): List<String> =
        cache.getOrPut(category) { build(category) }

    private fun build(category: EmojiCategory): List<String> {
        if (category == EmojiCategory.FLAGS) return flags()
        val spec = SPECS[category] ?: return emptyList()
        val out = ArrayList<String>()
        for (range in spec.ranges) {
            for (codePoint in range) addIfRenderable(out, codePointString(codePoint))
        }
        for (codePoint in spec.singles) addSingle(out, codePointString(codePoint))
        return out
    }

    private fun flags(): List<String> {
        val out = ArrayList<String>()
        for (codePoint in FLAG_EXTRAS) addIfRenderable(out, codePointString(codePoint))
        for (country in Locale.getISOCountries()) addIfRenderable(out, flagOf(country))
        return out
    }

    // Bendera = dua huruf regional indicator (ID -> 🇮🇩)
    private fun flagOf(country: String): String {
        val first = REGIONAL_INDICATOR_A + (country[0] - 'A')
        val second = REGIONAL_INDICATOR_A + (country[1] - 'A')
        return codePointString(first) + codePointString(second)
    }

    private fun codePointString(codePoint: Int): String = String(Character.toChars(codePoint))

    private fun addIfRenderable(out: MutableList<String>, emoji: String) {
        if (paint.hasGlyph(emoji)) out.add(emoji)
    }

    // Simbol lama (❤ ✔ ☀) butuh variation selector supaya tampil sebagai emoji berwarna
    private fun addSingle(out: MutableList<String>, symbol: String) {
        val emojiStyle = symbol + VARIATION_SELECTOR
        when {
            paint.hasGlyph(emojiStyle) -> out.add(emojiStyle)
            paint.hasGlyph(symbol) -> out.add(symbol)
        }
    }
}
