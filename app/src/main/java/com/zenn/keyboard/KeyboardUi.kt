package com.zenn.keyboard

import android.content.Context
import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout

// Merangkai semua bagian: baris pintasan di atas, lalu satu area yang bisa berisi
// tombol ketik, panel pintasan, riwayat clipboard, emoji, simbol, atau terjemahan
class KeyboardUi(
    private val context: Context,
    private val listener: Listener,
    history: ClipboardHistory,
) {
    interface Listener {
        fun onText(text: String)
        fun onBackspace()
        fun onEnter()
        fun onShortcut(shortcut: Shortcut)
    }

    val root: LinearLayout = LinearLayout(context)
    private var mode = PanelMode.NONE

    // Context ini adalah KeyboardService, jadi koneksi ke kolom teks bisa diambil langsung
    private val service get() = context as? InputMethodService

    private val typing = TypingKeys(
        context,
        listener,
        onOpenSymbols = { setMode(PanelMode.SYMBOLS) },
        onOpenEmoji = { setMode(PanelMode.EMOJI) },
    )
    private val shortcutPanel = ShortcutPanel(context) { listener.onShortcut(it) }
    private val clipboardPanel = ClipboardPanel(context, history) { listener.onText(it) }
    private val emojiPanel = EmojiPanel(
        context,
        EmojiRecents(context),
        onEmoji = { listener.onText(it) },
        onBackspace = { listener.onBackspace() },
        onClose = { setMode(PanelMode.NONE) },
    )
    private val symbolPanel = SymbolPanel(
        context,
        onText = { listener.onText(it) },
        onWrap = { open, close ->
            val action = ShortcutAction.Wrap(open, close)
            listener.onShortcut(Shortcut(open + close, "Pasangan $open $close", action))
        },
        onBackspace = { listener.onBackspace() },
        onClose = { setMode(PanelMode.NONE) },
    )
    private val translatePanel = TranslatePanel(
        context,
        connection = { service?.currentInputConnection },
        editorInfo = { service?.currentInputEditorInfo },
        onClose = { setMode(PanelMode.NONE) },
    )
    private val bar = ShortcutBar(
        context,
        onShortcut = { listener.onShortcut(it) },
        onToggleShortcuts = {
            setMode(if (mode == PanelMode.NONE) PanelMode.SHORTCUTS else PanelMode.NONE)
        },
        onToggleClipboard = { toggle(PanelMode.CLIPBOARD) },
        onToggleTranslate = { toggle(PanelMode.TRANSLATE) },
    )

    init {
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Palette.background)
        root.setPadding(context.dp(4), context.dp(4), context.dp(4), context.dp(8))

        // Semua panel menimpa area tombol ketik dan tingginya dibuat sama
        val panelHeight = context.dp(KEYS_AREA_HEIGHT_DP)
        val body = FrameLayout(context)
        body.addView(typing.view, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        listOf(
            shortcutPanel.view,
            clipboardPanel.view,
            emojiPanel.view,
            symbolPanel.view,
            translatePanel.view,
        ).forEach { panel ->
            panel.visibility = View.GONE
            body.addView(panel, FrameLayout.LayoutParams(MATCH_PARENT, panelHeight))
        }

        root.addView(bar.view, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        root.addView(body, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        history.onChange = { clipboardPanel.refresh() }
    }

    fun reset() {
        typing.resetShift()
        setMode(PanelMode.NONE)
    }

    private fun toggle(target: PanelMode) = setMode(if (mode == target) PanelMode.NONE else target)

    private fun setMode(next: PanelMode) {
        mode = next
        typing.view.visibility = visibleIf(next == PanelMode.NONE)
        shortcutPanel.view.visibility = visibleIf(next == PanelMode.SHORTCUTS)
        clipboardPanel.view.visibility = visibleIf(next == PanelMode.CLIPBOARD)
        emojiPanel.view.visibility = visibleIf(next == PanelMode.EMOJI)
        symbolPanel.view.visibility = visibleIf(next == PanelMode.SYMBOLS)
        translatePanel.view.visibility = visibleIf(next == PanelMode.TRANSLATE)
        if (next == PanelMode.EMOJI) emojiPanel.show()
        if (next == PanelMode.TRANSLATE) translatePanel.show()
        bar.setMode(next)
    }

    private fun visibleIf(show: Boolean): Int = if (show) View.VISIBLE else View.GONE
}
