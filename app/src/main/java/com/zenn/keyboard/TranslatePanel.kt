package com.zenn.keyboard

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

private const val TR_MAX_CHARS = 1_500
private const val TR_PREFS = "translate"
private const val TR_KEY_TARGET = "target"
private const val TR_DEFAULT_TARGET = "en"
private const val TR_ROW_HEIGHT_DP = 44
private const val TR_GAP_DP = 3
private const val TR_HINT = "Ketik teks di kolom (atau pilih sebagian teks), lalu buka 🌐 lagi."
private val TR_ERROR = Color.parseColor("#FF8A8A")

// Teks yang akan diterjemahkan beserta panjang teks sebelum/sesudah kursor,
// dipakai untuk menggantinya nanti
private class SourceText(val text: String, val before: Int, val after: Int, val selected: Boolean)

// Panel terjemahan: pilih bahasa tujuan di atas, hasil di tengah, aksi di bawah.
// Bahasa asal terdeteksi otomatis.
class TranslatePanel(
    private val context: Context,
    private val connection: () -> InputConnection?,
    private val editorInfo: () -> EditorInfo?,
    private val onClose: () -> Unit,
) {
    val view = LinearLayout(context)

    private val prefs = context.applicationContext
        .getSharedPreferences(TR_PREFS, Context.MODE_PRIVATE)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val languageChips = linkedMapOf<String, TextView>()
    private val status = TextView(context)
    private val result = TextView(context)
    private var target = prefs.getString(TR_KEY_TARGET, null) ?: TR_DEFAULT_TARGET
    private var source: SourceText? = null
    private var translated: String? = null
    private var requestId = 0

    init {
        view.orientation = LinearLayout.VERTICAL
        view.addView(languageStrip(), LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        status.setTextColor(Palette.textDim)
        status.setPadding(context.dp(8), context.dp(2), context.dp(8), context.dp(2))
        view.addView(status, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        result.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        result.setPadding(context.dp(12), context.dp(10), context.dp(12), context.dp(10))
        val scroller = ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            background = context.roundedBackground(Palette.key, Palette.key)
            addView(result)
        }
        val resultParams = LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f).apply {
            val gap = context.dp(TR_GAP_DP)
            setMargins(gap, gap, gap, gap)
        }
        view.addView(scroller, resultParams)
        view.addView(actionRow(), LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        highlightTarget()
        showMessage(TR_HINT)
    }

    // Dipanggil setiap panel dibuka: baca teks dari kolom lalu langsung terjemahkan
    fun show() {
        requestId++ // batalkan permintaan lama yang masih berjalan
        translated = null
        if (isPasswordField()) {
            source = null
            showMessage("Kolom password tidak diterjemahkan.")
        } else {
            source = readSource()
            translateCurrent()
        }
    }

    private fun readSource(): SourceText? {
        val ic = connection() ?: return null
        val selected = ic.getSelectedText(0)
        if (!selected.isNullOrEmpty()) return SourceText(selected.toString(), 0, 0, true)

        // Minta satu karakter lebih banyak dari batas agar teks yang kepanjangan ketahuan
        val before = ic.getTextBeforeCursor(TR_MAX_CHARS + 1, 0)?.toString().orEmpty()
        val after = ic.getTextAfterCursor(TR_MAX_CHARS + 1, 0)?.toString().orEmpty()
        val text = before + after
        if (text.isBlank()) return null
        return SourceText(text, before.length, after.length, false)
    }

    private fun isPasswordField(): Boolean {
        val type = editorInfo()?.inputType ?: return false
        val variation = type and InputType.TYPE_MASK_VARIATION
        return when (type and InputType.TYPE_MASK_CLASS) {
            InputType.TYPE_CLASS_TEXT ->
                variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                    variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
                    variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            InputType.TYPE_CLASS_NUMBER -> variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
            else -> false
        }
    }

    private fun translateCurrent() {
        val src = source
        translated = null
        if (src == null) {
            showMessage(TR_HINT)
            return
        }
        if (src.text.length > TR_MAX_CHARS) {
            showMessage(
                "Teks terlalu panjang (maks $TR_MAX_CHARS karakter). " +
                    "Pilih sebagian teks, lalu buka 🌐 lagi.",
                error = true,
            )
            return
        }

        val id = ++requestId
        val targetNow = target
        status.text = "Menerjemahkan…"
        result.text = ""
        Thread {
            val outcome = runCatching { Translator.translate(src.text, targetNow) }
            mainHandler.post { if (id == requestId) render(outcome, targetNow) }
        }.start()
    }

    private fun render(outcome: Result<Translation>, targetCode: String) {
        outcome.fold(
            onSuccess = { t ->
                translated = t.text
                result.setTextColor(Palette.text)
                result.text = t.text
                val from = if (t.detectedLang.isEmpty()) "Otomatis" else Languages.nameOf(t.detectedLang)
                status.text = "$from → ${Languages.nameOf(targetCode)}"
            },
            onFailure = { e ->
                showMessage((e as? TranslateException)?.message ?: "Gagal menerjemahkan.", error = true)
            },
        )
    }

    private fun showMessage(message: String, error: Boolean = false) {
        status.text = ""
        result.setTextColor(if (error) TR_ERROR else Palette.textDim)
        result.text = message
    }

    private fun chooseTarget(code: String) {
        target = code
        prefs.edit().putString(TR_KEY_TARGET, code).apply()
        highlightTarget()
        translateCurrent()
    }

    private fun insert() {
        val text = translated ?: return
        connection()?.commitText(text, 1)
        onClose()
    }

    private fun replace() {
        val text = translated ?: return
        val src = source ?: return
        val ic = connection() ?: return
        ic.beginBatchEdit()
        // Teks terpilih otomatis tergantikan oleh commitText; kalau tidak ada
        // pilihan, seluruh isi kolom dihapus dulu
        if (!src.selected) ic.deleteSurroundingText(src.before, src.after)
        ic.commitText(text, 1)
        ic.endBatchEdit()
        onClose()
    }

    private fun highlightTarget() {
        languageChips.forEach { (code, chip) ->
            val active = code == target
            chip.setTextColor(if (active) Palette.background else Palette.text)
            chip.background = if (active) {
                context.roundedBackground(Palette.accent, Palette.accentPressed)
            } else {
                context.roundedBackground(Palette.keyAction, Palette.keyPressed)
            }
        }
    }

    private fun languageStrip(): HorizontalScrollView {
        val strip = LinearLayout(context)
        Languages.all.forEach { (code, name) ->
            val chip = context.buildChip(name, "Terjemahkan ke $name") { chooseTarget(code) }
            languageChips[code] = chip
            strip.addView(chip, chipParams())
        }
        return HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(strip)
        }
    }

    private fun actionRow(): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(context.buildChip("ABC", "Kembali ke keyboard huruf") { onClose() }, chipParams())
        row.addView(context.buildChip("↻", "Terjemahkan ulang") { translateCurrent() }, chipParams())
        row.addView(
            context.buildChip("Sisipkan", "Sisipkan terjemahan di kursor") { insert() },
            weightParams(),
        )
        row.addView(
            context.buildChip("Ganti teks", "Ganti teks asli dengan terjemahan", KeyStyle.ACCENT) { replace() },
            weightParams(),
        )
        return row
    }

    private fun chipParams() =
        LinearLayout.LayoutParams(WRAP_CONTENT, context.dp(TR_ROW_HEIGHT_DP)).apply {
            val gap = context.dp(TR_GAP_DP)
            setMargins(gap, gap, gap, gap)
        }

    private fun weightParams() =
        LinearLayout.LayoutParams(0, context.dp(TR_ROW_HEIGHT_DP), 1f).apply {
            val gap = context.dp(TR_GAP_DP)
            setMargins(gap, gap, gap, gap)
        }
}
