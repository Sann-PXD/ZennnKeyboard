package com.zenn.keyboard

import android.content.Context
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView

private const val SYM_CELL_HEIGHT_DP = 44
private const val SYM_TAB_HEIGHT_DP = 44
private const val SYM_TAB_GAP_DP = 3
private const val SYM_TAB_ICON_SP = 16f
private const val SYM_BACKSPACE_WIDTH_DP = 56

// Panel simbol: grid di atas, baris kategori di bawah (ABC | kategori | ⌫)
class SymbolPanel(
    private val context: Context,
    private val onText: (String) -> Unit,
    private val onWrap: (String, String) -> Unit,
    private val onBackspace: () -> Unit,
    private val onClose: () -> Unit,
) {
    val view = LinearLayout(context)

    private val handler = Handler(Looper.getMainLooper())
    private val adapter = SymbolAdapter(context)
    private val grid = GridView(context)
    private val tabs = linkedMapOf<SymbolCategory, TextView>()

    init {
        view.orientation = LinearLayout.VERTICAL

        grid.stretchMode = GridView.STRETCH_COLUMN_WIDTH
        grid.isVerticalScrollBarEnabled = false
        grid.overScrollMode = View.OVER_SCROLL_NEVER
        grid.selector = context.roundedBackground(Palette.keyPressed, Palette.keyPressed)
        grid.adapter = adapter
        grid.setOnItemClickListener { _, cell, position, _ -> pick(adapter.getItem(position), cell) }

        view.addView(grid, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        view.addView(buildTabRow(), LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        select(SymbolCategory.COMMON)
    }

    private fun pick(item: SymbolItem, cell: View) {
        cell.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        val close = item.close
        if (close == null) onText(item.text) else onWrap(item.text, close)
    }

    private fun select(category: SymbolCategory) {
        grid.numColumns = category.columns
        adapter.submit(SymbolCatalog.items(category), category)
        grid.setSelection(0)
        tabs.forEach { (key, tab) -> styleTab(tab, key == category) }
    }

    private fun styleTab(tab: TextView, active: Boolean) {
        tab.setTextColor(if (active) Palette.background else Palette.text)
        tab.background = if (active) {
            context.roundedBackground(Palette.accent, Palette.accentPressed)
        } else {
            context.roundedBackground(Palette.keyAction, Palette.keyPressed)
        }
    }

    private fun buildTabRow(): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(
            context.buildChip("ABC", "Kembali ke keyboard huruf") { onClose() },
            tabParams(),
        )

        val strip = LinearLayout(context)
        SymbolCategory.values().forEach { category ->
            val tab = context.buildChip(category.icon, category.title) { select(category) }
            tab.setTextSize(TypedValue.COMPLEX_UNIT_SP, SYM_TAB_ICON_SP)
            tabs[category] = tab
            strip.addView(tab, tabParams())
        }
        val scroller = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        scroller.addView(strip)
        row.addView(scroller, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))

        // Backspace: tahan untuk menghapus cepat
        val backspace = context.buildKey(
            handler, "⌫", "Hapus", 1f, KeyStyle.ACTION, repeatable = true,
        ) { onBackspace() }
        val backspaceParams = LinearLayout.LayoutParams(
            context.dp(SYM_BACKSPACE_WIDTH_DP),
            context.dp(SYM_TAB_HEIGHT_DP),
        ).apply {
            val gap = context.dp(SYM_TAB_GAP_DP)
            setMargins(gap, gap, gap, gap)
        }
        row.addView(backspace, backspaceParams)
        return row
    }

    private fun tabParams() =
        LinearLayout.LayoutParams(WRAP_CONTENT, context.dp(SYM_TAB_HEIGHT_DP)).apply {
            val gap = context.dp(SYM_TAB_GAP_DP)
            setMargins(gap, gap, gap, gap)
        }
}

private class SymbolAdapter(private val context: Context) : BaseAdapter() {
    private var items: List<SymbolItem> = emptyList()
    private var style = SymbolCategory.COMMON

    fun submit(list: List<SymbolItem>, category: SymbolCategory) {
        items = list
        style = category
        notifyDataSetChanged()
    }

    override fun getCount(): Int = items.size

    override fun getItem(position: Int): SymbolItem = items[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val cell = (convertView as? TextView) ?: TextView(context).apply {
            gravity = Gravity.CENTER
            maxLines = 1
            setTextColor(Palette.text)
            layoutParams = AbsListView.LayoutParams(MATCH_PARENT, context.dp(SYM_CELL_HEIGHT_DP))
        }
        // Gaya diatur ulang tiap bind karena view dipakai ulang antar kategori
        cell.text = items[position].label
        cell.contentDescription = items[position].label
        cell.setTextSize(TypedValue.COMPLEX_UNIT_SP, style.textSp)
        cell.typeface = if (style.monospace) Typeface.MONOSPACE else Typeface.DEFAULT
        return cell
    }
}
