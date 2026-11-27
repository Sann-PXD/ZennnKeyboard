package com.zenn.keyboard

import android.content.Context
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

private const val CHIP_HEIGHT_DP = 44
private const val CHIP_GAP_DP = 3
private const val PANEL_COLUMNS = 4

// Baris pintasan cepat: tombol ☰ + chip yang bisa digeser ke samping
class ShortcutBar(
    private val context: Context,
    onShortcut: (Shortcut) -> Unit,
    onTogglePanel: () -> Unit,
) {
    val view = LinearLayout(context)

    private val toggle = context.buildChip("☰", "Buka semua pintasan", KeyStyle.ACCENT) {
        onTogglePanel()
    }

    init {
        view.orientation = LinearLayout.HORIZONTAL
        view.gravity = Gravity.CENTER_VERTICAL
        view.addView(toggle, chipParams())

        val strip = LinearLayout(context)
        Shortcuts.quick.forEach { shortcut ->
            val chip = context.buildChip(shortcut.label, shortcut.description) { onShortcut(shortcut) }
            strip.addView(chip, chipParams())
        }

        val scroller = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        scroller.addView(strip)
        view.addView(scroller, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
    }

    fun setPanelOpen(open: Boolean) {
        toggle.text = if (open) "ABC" else "☰"
        toggle.contentDescription = if (open) "Kembali ke keyboard" else "Buka semua pintasan"
    }

    private fun chipParams() =
        LinearLayout.LayoutParams(WRAP_CONTENT, context.dp(CHIP_HEIGHT_DP)).apply {
            val gap = context.dp(CHIP_GAP_DP)
            setMargins(gap, gap, gap, gap)
        }
}

// Panel semua pintasan, dikelompokkan, 4 kolom, bisa di-scroll
class ShortcutPanel(
    private val context: Context,
    private val onShortcut: (Shortcut) -> Unit,
) {
    val view = ScrollView(context)

    init {
        view.isVerticalScrollBarEnabled = false
        view.overScrollMode = View.OVER_SCROLL_NEVER

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(context.dp(2), 0, context.dp(2), context.dp(6))
        }
        Shortcuts.groups.forEach { group ->
            content.addView(title(group.title))
            group.items.chunked(PANEL_COLUMNS).forEach { content.addView(row(it)) }
        }
        view.addView(content)
    }

    private fun title(text: String): TextView = TextView(context).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        setTextColor(Palette.textDim)
        isAllCaps = true
        letterSpacing = 0.08f
        setPadding(context.dp(8), context.dp(10), 0, context.dp(2))
    }

    private fun row(items: List<Shortcut>): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        items.forEach { addView(chip(it), cellParams()) }
        // Sel kosong agar baris terakhir tetap rata kolomnya
        repeat(PANEL_COLUMNS - items.size) { addView(View(context), cellParams()) }
    }

    private fun chip(shortcut: Shortcut): TextView =
        context.buildChip(shortcut.label, shortcut.description) { onShortcut(shortcut) }.apply {
            setPadding(context.dp(4), 0, context.dp(4), 0)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }

    private fun cellParams() =
        LinearLayout.LayoutParams(0, context.dp(CHIP_HEIGHT_DP), 1f).apply {
            val gap = context.dp(CHIP_GAP_DP)
            setMargins(gap, gap, gap, gap)
        }
}
