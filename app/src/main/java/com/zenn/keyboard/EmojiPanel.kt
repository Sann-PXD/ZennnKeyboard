package com.zenn.keyboard

import android.content.Context
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
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView

private const val COLUMNS = 8
private const val CELL_HEIGHT_DP = 44
private const val CELL_TEXT_SP = 26f
private const val TAB_HEIGHT_DP = 44
private const val TAB_GAP_DP = 3
private const val TAB_ICON_SP = 18f
private const val BACKSPACE_WIDTH_DP = 56

// Panel emoji: grid di atas, baris kategori di bawah (ABC | kategori | ⌫)
class EmojiPanel(
    private val context: Context,
    private val recents: EmojiRecents,
    private val onEmoji: (String) -> Unit,
    private val onBackspace: () -> Unit,
    private val onClose: () -> Unit,
) {
    val view = LinearLayout(context)

    private val handler = Handler(Looper.getMainLooper())
    private val adapter = EmojiAdapter(context)
    private val grid = GridView(context)
    private val tabs = linkedMapOf<EmojiCategory, TextView>()

    init {
        view.orientation = LinearLayout.VERTICAL

        grid.numColumns = COLUMNS
        grid.stretchMode = GridView.STRETCH_COLUMN_WIDTH
        grid.isVerticalScrollBarEnabled = false
        grid.overScrollMode = View.OVER_SCROLL_NEVER
        grid.selector = context.roundedBackground(Palette.keyPressed, Palette.keyPressed)
        grid.adapter = adapter
        grid.setOnItemClickListener { _, cell, position, _ -> pick(adapter.getItem(position), cell) }

        // Tampilan kosong, dipakai kategori "Terbaru" saat belum ada riwayat
        val empty = TextView(context).apply {
            text = "Belum ada emoji terbaru.\nPilih emoji, nanti muncul di sini."
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(Palette.textDim)
        }
        grid.emptyView = empty

        val body = FrameLayout(context)
        body.addView(grid, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        body.addView(empty, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        view.addView(body, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        view.addView(buildTabRow(), LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        select(EmojiCategory.FACES)
    }

    // Dipanggil saat panel dibuka: mulai dari "Terbaru" kalau sudah ada riwayat
    fun show() {
        select(if (recents.snapshot().isEmpty()) EmojiCategory.FACES else EmojiCategory.RECENT)
    }

    private fun pick(emoji: String, cell: View) {
        cell.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        recents.add(emoji)
        onEmoji(emoji)
    }

    private fun select(category: EmojiCategory) {
        val list = if (category == EmojiCategory.RECENT) {
            recents.snapshot()
        } else {
            EmojiCatalog.emojis(category)
        }
        adapter.submit(list)
        grid.setSelection(0)
        tabs.forEach { (key, tab) -> styleTab(tab, key == category) }
    }

    private fun styleTab(tab: TextView, active: Boolean) {
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
        EmojiCategory.values().forEach { category ->
            val tab = context.buildChip(category.icon, category.title) { select(category) }
            tab.setTextSize(TypedValue.COMPLEX_UNIT_SP, TAB_ICON_SP)
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
            context.dp(BACKSPACE_WIDTH_DP),
            context.dp(TAB_HEIGHT_DP),
        ).apply {
            val gap = context.dp(TAB_GAP_DP)
            setMargins(gap, gap, gap, gap)
        }
        row.addView(backspace, backspaceParams)
        return row
    }

    private fun tabParams() =
        LinearLayout.LayoutParams(WRAP_CONTENT, context.dp(TAB_HEIGHT_DP)).apply {
            val gap = context.dp(TAB_GAP_DP)
            setMargins(gap, gap, gap, gap)
        }
}

private class EmojiAdapter(private val context: Context) : BaseAdapter() {
    private var items: List<String> = emptyList()

    fun submit(list: List<String>) {
        items = list
        notifyDataSetChanged()
    }

    override fun getCount(): Int = items.size

    override fun getItem(position: Int): String = items[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val cell = (convertView as? TextView) ?: TextView(context).apply {
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, CELL_TEXT_SP)
            layoutParams = AbsListView.LayoutParams(MATCH_PARENT, context.dp(CELL_HEIGHT_DP))
        }
        cell.text = items[position]
        return cell
    }
}
