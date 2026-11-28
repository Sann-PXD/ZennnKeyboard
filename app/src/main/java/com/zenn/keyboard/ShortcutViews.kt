package com.zenn.keyboard

import android.content.Context
import android.text.TextUtils
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

private const val CHIP_HEIGHT_DP = 44
private const val CHIP_GAP_DP = 3
private const val PANEL_COLUMNS = 4
private const val BAR_CHIP_HEIGHT_DP = 36
private const val BAR_CHIP_GAP_DP = 2
private const val BAR_TEXT_SP = 11f
private const val BAR_ICON_SP = 15f
private const val EMAIL_TEXT_SP = 12f

// Judul kecil untuk tiap kelompok di panel
fun Context.buildSectionTitle(text: String): TextView = TextView(this).apply {
    this.text = text
    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
    setTextColor(Palette.textDim)
    isAllCaps = true
    letterSpacing = 0.08f
    setPadding(dp(8), dp(10), 0, dp(2))
}

// Dua baris tetap tanpa geser, semua tombol berukuran sama:
// baris 1 = menu, riwayat, terjemahan + 5 pintasan teratas
// baris 2 = baris kode (Tab, pasangan kurung/kutip, Redo, kursor); di kolom email
//           baris ini berganti menjadi saran email milik pengguna
class ShortcutBar(
    private val context: Context,
    onShortcut: (Shortcut) -> Unit,
    onToggleShortcuts: () -> Unit,
    onToggleClipboard: () -> Unit,
    onToggleTranslate: () -> Unit,
    private val onEmailPick: (String) -> Unit,
    private val onEmailSetup: () -> Unit,
) {
    val view = LinearLayout(context)

    private val menuButton = context.buildChip("☰", "Buka semua pintasan", KeyStyle.ACCENT) {
        onToggleShortcuts()
    }
    private val codeChips = mutableListOf<View>()
    private val emailStrip = LinearLayout(context)
    private val emailScroller = HorizontalScrollView(context)

    init {
        view.orientation = LinearLayout.VERTICAL

        // Urutan Shortcuts.quick: Semua, Salin, Potong, Tempel, Undo, Redo,
        // ( ), { }, [ ], " ", Tab, ←, →
        val quick = Shortcuts.quick
        val top = listOf(
            menuButton,
            tool("Klip", "Riwayat clipboard", onToggleClipboard),
            tool("Trans", "Terjemahan", onToggleTranslate),
        ) + quick.subList(0, 5).map { chipFor(it, onShortcut) }
        val code = listOf(quick[10], quick[6], quick[7], quick[8], quick[9], quick[5], quick[11], quick[12])
            .map { chipFor(it, onShortcut) }
        codeChips.addAll(code)

        // Saran email berbagi baris dengan baris kode; disembunyikan sampai dibutuhkan
        val codeRow = row(code)
        emailScroller.isHorizontalScrollBarEnabled = false
        emailScroller.overScrollMode = View.OVER_SCROLL_NEVER
        emailScroller.addView(emailStrip)
        emailScroller.visibility = View.GONE
        codeRow.addView(emailScroller, LinearLayout.LayoutParams(0, context.dp(BAR_CHIP_HEIGHT_DP), 1f))

        view.addView(row(top))
        view.addView(codeRow)
    }

    fun setMode(mode: PanelMode) {
        val open = mode != PanelMode.NONE
        menuButton.text = if (open) "ABC" else "☰"
        menuButton.contentDescription = if (open) "Kembali ke keyboard" else "Buka semua pintasan"
    }

    // null = bukan kolom email (tampilkan baris kode); daftar = tampilkan saran email
    fun setEmailSuggestions(emails: List<String>?) {
        val emailMode = emails != null
        codeChips.forEach { it.visibility = if (emailMode) View.GONE else View.VISIBLE }
        emailScroller.visibility = if (emailMode) View.VISIBLE else View.GONE
        emailStrip.removeAllViews()
        if (emails == null) return

        emails.forEach { email ->
            emailStrip.addView(emailChip(email, "Pakai email $email") { onEmailPick(email) }, emailParams())
        }
        // Belum ada email tersimpan: chip ini mengajak menambah; sudah ada: jadi tombol tambah
        val addLabel = if (emails.isEmpty()) "+ Tambah email dari akun HP" else "+"
        emailStrip.addView(emailChip(addLabel, "Tambah email") { onEmailSetup() }, emailParams())
    }

    private fun tool(label: String, description: String, onClick: () -> Unit): TextView =
        context.buildChip(label, description) { onClick() }

    private fun chipFor(shortcut: Shortcut, onShortcut: (Shortcut) -> Unit): TextView =
        context.buildChip(shortcut.label, shortcut.description) { onShortcut(shortcut) }

    private fun emailChip(label: String, description: String, onClick: () -> Unit): TextView =
        context.buildChip(label, description) { onClick() }.apply {
            maxLines = 1
            setPadding(context.dp(10), 0, context.dp(10), 0)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, EMAIL_TEXT_SP)
        }

    private fun emailParams() =
        LinearLayout.LayoutParams(WRAP_CONTENT, context.dp(BAR_CHIP_HEIGHT_DP)).apply {
            val gap = context.dp(BAR_CHIP_GAP_DP)
            setMargins(gap, gap, gap, gap)
        }

    // Semua chip berbagi lebar baris sama rata, jadi tidak perlu digeser
    private fun row(chips: List<TextView>): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        chips.forEach { chip ->
            val isIcon = chip === menuButton
            chip.minWidth = 0
            chip.maxLines = 1
            chip.setPadding(context.dp(2), 0, context.dp(2), 0)
            chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (isIcon) BAR_ICON_SP else BAR_TEXT_SP)
            val params = LinearLayout.LayoutParams(0, context.dp(BAR_CHIP_HEIGHT_DP), 1f).apply {
                val gap = context.dp(BAR_CHIP_GAP_DP)
                setMargins(gap, gap, gap, gap)
            }
            addView(chip, params)
        }
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
            content.addView(context.buildSectionTitle(group.title))
            group.items.chunked(PANEL_COLUMNS).forEach { content.addView(row(it)) }
        }
        view.addView(content)
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
