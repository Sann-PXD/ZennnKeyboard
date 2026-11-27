package com.zenn.keyboard

import android.graphics.Paint

enum class SymbolCategory(
    val icon: String,
    val title: String,
    val columns: Int = 8,
    val textSp: Float = 22f,
    val monospace: Boolean = false,
) {
    COMMON("!?", "Umum"),
    CODE("</>", "Kode", columns = 4, textSp = 15f, monospace = true),
    MATH("±", "Matematika"),
    NUMBERS("½", "Angka dan pecahan"),
    CURRENCY("\$", "Mata uang"),
    ARROWS("→", "Panah"),
    SHAPES("★", "Bentuk dan garis"),
    ACCENTS("é", "Huruf beraksen"),
    GREEK("Ω", "Yunani"),
}

// close != null berarti item ini pasangan: text = pembuka, close = penutup,
// kursor masuk ke tengah (atau membungkus teks yang sedang dipilih)
data class SymbolItem(
    val label: String,
    val text: String = label,
    val close: String? = null,
)

private fun tokens(raw: String): List<String> = raw.split(' ').filter { it.isNotEmpty() }

private fun plain(label: String) = SymbolItem(label)

private fun pair(label: String, open: String, close: String) = SymbolItem(label, open, close)

private val COMMON_TOKENS = tokens(
    """. , ? ! ' " : ; - _ ( ) [ ] { } / \ | @ # $ % ^ & * + = < > ~ ` ¡ ¿ … · • ¶ § † ‡ © ® ™ « » ‹ › “ ” ‘ ’ „ ‚ – — ′ ″ № ° ª º ¦ ¬ ¨ ¯ ´ ¸""",
)

private val MATH_TOKENS = tokens(
    "+ − × ÷ = ≠ ≈ ≤ ≥ < > ± ∓ √ ∛ ∞ ∑ ∏ ∫ ∂ ∆ ∇ π µ ° ′ ″ % ‰ ‱ ∈ ∉ ∋ ∪ ∩ ⊂ ⊃ ⊆ ⊇ ∅ " +
        "∀ ∃ ∄ ¬ ∧ ∨ ⊕ ⊗ ∝ ∴ ∵ ⇒ ⇔ ≡ ≢ ∥ ⊥ ∠ ∘ ∙ ⁄ ℝ ℕ ℤ ℚ ℂ",
)

private val NUMBERS_TOKENS = tokens(
    "⁰ ¹ ² ³ ⁴ ⁵ ⁶ ⁷ ⁸ ⁹ ⁿ ₀ ₁ ₂ ₃ ₄ ₅ ₆ ₇ ₈ ₉ ¼ ½ ¾ ⅓ ⅔ ⅛ ⅜ ⅝ ⅞ " +
        "⓪ ① ② ③ ④ ⑤ ⑥ ⑦ ⑧ ⑨ ⑩ ⑪ ⑫ ⑬ ⑭ ⑮ ⑯ ⑰ ⑱ ⑲ ⑳ ❶ ❷ ❸ ❹ ❺ ❻ ❼ ❽ ❾ ❿ " +
        "Ⅰ Ⅱ Ⅲ Ⅳ Ⅴ Ⅵ Ⅶ Ⅷ Ⅸ Ⅹ Ⅺ Ⅻ ⅰ ⅱ ⅲ ⅳ ⅴ ⅵ ⅶ ⅷ ⅸ ⅹ",
)

private val CURRENCY_TOKENS = tokens(
    "\$ € £ ¥ ¢ ₹ ₽ ₩ ₺ ₫ ₪ ₦ ₴ ₱ ฿ ₡ ₣ ₤ ₥ ₧ ₨ ₭ ₮ ₲ ₳ ₵ ₸ ₼ ₾ ₿ ¤ ƒ Rp",
)

private val ARROWS_TOKENS = tokens(
    "← → ↑ ↓ ↔ ↕ ↖ ↗ ↘ ↙ ⇐ ⇒ ⇑ ⇓ ⇔ ⇕ ↩ ↪ ↰ ↱ ↲ ↳ ↵ ↶ ↷ ↺ ↻ ➜ ➔ ➝ ➞ ➡ ⬅ ⬆ ⬇ " +
        "⟵ ⟶ ⟷ ⇄ ⇅ ⇆ ⇦ ⇨ ⇧ ⇩ ➤ ➢ ▶ ◀ ▲ ▼ ► ◄",
)

private val SHAPES_TOKENS = tokens(
    "■ □ ▪ ▫ ▲ △ ▼ ▽ ◆ ◇ ○ ● ◎ ◉ ★ ☆ ✦ ✧ ♠ ♣ ♥ ♦ ♡ ♢ ✓ ✔ ✗ ✘ ☐ ☑ ☒ " +
        "─ │ ┌ ┐ └ ┘ ├ ┤ ┬ ┴ ┼ ═ ║ ╔ ╗ ╚ ╝ ╠ ╣ ╦ ╩ ╬ █ ▓ ▒ ░ ▀ ▄ ▌ ▐ ├── └──",
)

private val ACCENT_LOWER = tokens(
    "à á â ã ä å æ ç è é ê ë ì í î ï ñ ò ó ô õ ö ø œ ù ú û ü ý ÿ ß ð þ " +
        "ā ă ą ć č ď đ ē ę ě ğ ī ı ł ń ň ő ř ś š ş ť ū ů ű ź ż ž",
)

private val GREEK_LOWER = tokens("α β γ δ ε ζ η θ ι κ λ μ ν ξ ο π ρ σ τ υ φ χ ψ ω")

private val CODE_ITEMS = listOf(
    plain("=>"), plain("->"), plain("!="), plain("=="), plain("==="), plain("!=="),
    plain("<="), plain(">="), plain("&&"), plain("||"), plain("??"), plain("?."),
    plain("::"), plain("..."), plain("//"), plain("++"), plain("--"), plain("+="),
    plain("-="), plain("*="), plain("/="), plain("<<"), plain(">>"), plain("|>"),
    plain("<-"), plain("**"), plain("#!"), plain("0x"), plain("\\n"), plain("\\t"),
    pair("/* */", "/* ", " */"),
    pair("<!-- -->", "<!-- ", " -->"),
    pair("{{ }}", "{{ ", " }}"),
    pair("\${ }", "\${", "}"),
    pair("\$( )", "\$(", ")"),
    pair("\"\"\"", "\"\"\"", "\"\"\""),
    pair("'''", "'''", "'''"),
    pair("~~ ~~", "~~", "~~"),
    pair("```", "```\n", "\n```"),
)

object SymbolCatalog {
    private val paint = Paint()
    private val cache = mutableMapOf<SymbolCategory, List<SymbolItem>>()

    // Hasil disimpan di cache, jadi pemeriksaan font hanya terjadi sekali per kategori
    fun items(category: SymbolCategory): List<SymbolItem> =
        cache.getOrPut(category) { build(category) }

    private fun build(category: SymbolCategory): List<SymbolItem> = when (category) {
        SymbolCategory.COMMON -> renderable(COMMON_TOKENS)
        SymbolCategory.CODE -> CODE_ITEMS
        SymbolCategory.MATH -> renderable(MATH_TOKENS)
        SymbolCategory.NUMBERS -> renderable(NUMBERS_TOKENS)
        SymbolCategory.CURRENCY -> renderable(CURRENCY_TOKENS)
        SymbolCategory.ARROWS -> renderable(ARROWS_TOKENS)
        SymbolCategory.SHAPES -> renderable(SHAPES_TOKENS)
        SymbolCategory.ACCENTS -> renderable(withUppercase(ACCENT_LOWER))
        SymbolCategory.GREEK -> renderable(withUppercase(GREEK_LOWER))
    }

    // Hanya simbol yang bisa digambar font HP yang ditampilkan, jadi tidak ada kotak kosong
    private fun renderable(list: List<String>): List<SymbolItem> =
        list.filter { canRender(it) }.map { SymbolItem(it) }

    private fun canRender(token: String): Boolean {
        var index = 0
        while (index < token.length) {
            val codePoint = token.codePointAt(index)
            if (!paint.hasGlyph(String(Character.toChars(codePoint)))) return false
            index += Character.charCount(codePoint)
        }
        return true
    }

    // Huruf besar dibuat otomatis; dilewati kalau hasilnya bukan satu huruf non-ASCII (ß, ı)
    private fun withUppercase(lower: List<String>): List<String> {
        val upper = lower.mapNotNull { token ->
            token.uppercase().takeIf { it.length == 1 && it != token && it[0].code > 127 }
        }
        return lower + upper
    }
}
