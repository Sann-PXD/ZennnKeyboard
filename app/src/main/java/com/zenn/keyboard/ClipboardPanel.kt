package com.zenn.keyboard

import android.content.Context
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

private const val ROW_HEIGHT_DP = 44
private const val ROW_GAP_DP = 2

// Panel riwayat clipboard: ketuk item = tempel, ☆ = sematkan, ✕ = hapus.
// Yang disematkan selalu di atas dan tidak ikut terhapus oleh "Bersihkan".
class ClipboardPanel(
    private val context: Context,
    private val history: ClipboardHistory,
    private val onPaste: (String) -> Unit,
) {
    val view = ScrollView(context)
    private val content = LinearLayout(context)

    init {
        view.isVerticalScrollBarEnabled = false
        view.overScrollMode = View.OVER_SCROLL_NEVER
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(context.dp(2), 0, context.dp(2), context.dp(6))
        view.addView(content)
        refresh()
    }

    fun refresh() {
        content.removeAllViews()
        val items = history.snapshot()
        val (pinned, recent) = items.partition { history.isPinned(it) }
        content.addView(header(hasRecent = recent.isNotEmpty()))
        if (items.isEmpty()) {
            content.addView(emptyState())
            return
        }
        if (pinned.isNotEmpty()) {
            content.addView(context.buildSectionTitle("Disematkan"))
            pinned.forEach { content.addView(itemRow(it, pinned = true)) }
        }
        if (recent.isNotEmpty()) {
            if (pinned.isNotEmpty()) content.addView(context.buildSectionTitle("Terbaru"))
            recent.forEach { content.addView(itemRow(it, pinned = false)) }
        }
    }

    private fun header(hasRecent: Boolean): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(
            context.buildSectionTitle("Riwayat clipboard"),
            LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f),
        )
        if (hasRecent) {
            val clear = context.buildChip("Bersihkan", "Hapus semua riwayat kecuali yang disematkan") {
                history.clearUnpinned()
            }
            addView(clear, LinearLayout.LayoutParams(WRAP_CONTENT, context.dp(ROW_HEIGHT_DP)))
        }
    }

    private fun emptyState(): TextView = TextView(context).apply {
        text = "Belum ada riwayat.\nSalin teks, nanti muncul di sini."
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        setTextColor(Palette.textDim)
        setPadding(context.dp(16), context.dp(32), context.dp(16), context.dp(32))
    }

    private fun itemRow(text: String, pinned: Boolean): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
            val gap = context.dp(ROW_GAP_DP)
            setMargins(gap, gap, gap, gap)
        }
        addView(preview(text), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))

        val pin = context.buildChip(
            if (pinned) "★" else "☆",
            if (pinned) "Lepas sematan" else "Sematkan",
            if (pinned) KeyStyle.ACCENT else KeyStyle.ACTION,
        ) { history.togglePin(text) }
        val delete = context.buildChip("✕", "Hapus dari riwayat") { history.remove(text) }
        addView(pin, squareParams())
        addView(delete, squareParams())
    }

    private fun squareParams(): LinearLayout.LayoutParams {
        val size = context.dp(ROW_HEIGHT_DP)
        return LinearLayout.LayoutParams(size, size).apply { setMargins(context.dp(ROW_GAP_DP), 0, 0, 0) }
    }

    private fun preview(text: String): TextView = TextView(context).apply {
        this.text = singleLine(text)
        contentDescription = "Tempel: ${singleLine(text)}"
        minHeight = context.dp(ROW_HEIGHT_DP)
        gravity = Gravity.CENTER_VERTICAL
        maxLines = 2
        ellipsize = TextUtils.TruncateAt.END
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        setTextColor(Palette.text)
        setPadding(context.dp(12), context.dp(8), context.dp(12), context.dp(8))
        background = context.roundedBackground(Palette.key, Palette.keyPressed)
        setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            onPaste(text)
        }
    }

    // Pratinjau satu baris; baris baru diganti ⏎
    private fun singleLine(text: String): String =
        text.lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ⏎ ")
}
