package com.zenn.keyboard

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

private const val DOUBLE_TAP_MS = 400L

enum class ShiftState { OFF, ONCE, LOCK }

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
    private val handler = Handler(Looper.getMainLooper())
    private val keysArea = LinearLayout(context)
    private val letterKeys = mutableListOf<Pair<TextView, Char>>()
    private var shiftKey: TextView? = null
    private var shift = ShiftState.OFF
    private var lastShiftTap = 0L
    private var mode = PanelMode.NONE

    private val shortcutPanel = ShortcutPanel(context) { listener.onShortcut(it) }
    private val clipboardPanel = ClipboardPanel(context, history) { listener.onText(it) }
    private val bar = ShortcutBar(
        context,
        onShortcut = { listener.onShortcut(it) },
        onToggleShortcuts = {
            setMode(if (mode == PanelMode.NONE) PanelMode.SHORTCUTS else PanelMode.NONE)
        },
        onToggleClipboard = {
            setMode(if (mode == PanelMode.CLIPBOARD) PanelMode.NONE else PanelMode.CLIPBOARD)
        },
    )

    init {
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Palette.background)
        root.setPadding(context.dp(4), context.dp(4), context.dp(4), context.dp(8))

        keysArea.orientation = LinearLayout.VERTICAL
        buildKeys()

        // Panel menimpa area tombol huruf, tingginya dibuat sama
        shortcutPanel.view.visibility = View.GONE
        clipboardPanel.view.visibility = View.GONE
        val panelHeight = context.dp(KEYS_AREA_HEIGHT_DP)
        val body = FrameLayout(context)
        body.addView(keysArea, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        body.addView(shortcutPanel.view, FrameLayout.LayoutParams(MATCH_PARENT, panelHeight))
        body.addView(clipboardPanel.view, FrameLayout.LayoutParams(MATCH_PARENT, panelHeight))

        root.addView(bar.view, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        root.addView(body, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        history.onChange = { clipboardPanel.refresh() }
    }

    fun reset() {
        setShift(ShiftState.OFF)
        setMode(PanelMode.NONE)
    }

    private fun setMode(next: PanelMode) {
        mode = next
        keysArea.visibility = if (next == PanelMode.NONE) View.VISIBLE else View.GONE
        shortcutPanel.view.visibility = if (next == PanelMode.SHORTCUTS) View.VISIBLE else View.GONE
        clipboardPanel.view.visibility = if (next == PanelMode.CLIPBOARD) View.VISIBLE else View.GONE
        bar.setMode(next)
    }

    private fun buildKeys() {
        keysArea.addView(row { "1234567890".forEach { addView(textKey(it.toString())) } })
        keysArea.addView(row { "qwertyuiop".forEach { addView(letterKey(it)) } })
        keysArea.addView(
            row {
                addView(spacer(0.5f))
                "asdfghjkl".forEach { addView(letterKey(it)) }
                addView(spacer(0.5f))
            },
        )
        keysArea.addView(
            row {
                val shiftView = key("⇧", "Shift", 1.5f, KeyStyle.ACTION, onPress = { onShiftTap() })
                shiftKey = shiftView
                addView(shiftView)
                "zxcvbnm".forEach { addView(letterKey(it)) }
                addView(
                    key(
                        "⌫", "Hapus", 1.5f, KeyStyle.ACTION,
                        repeatable = true,
                        onPress = { listener.onBackspace() },
                    ),
                )
            },
        )
        keysArea.addView(
            row {
                addView(textKey(","))
                addView(key("Zenn", "Spasi", 5f, onPress = { listener.onText(" ") }))
                addView(textKey("."))
                addView(key("↵", "Enter", 1.5f, KeyStyle.ACCENT, onPress = { listener.onEnter() }))
            },
        )
    }

    private fun key(
        label: String,
        description: String,
        weight: Float = 1f,
        style: KeyStyle = KeyStyle.NORMAL,
        repeatable: Boolean = false,
        onPress: () -> Unit,
    ): TextView = context.buildKey(handler, label, description, weight, style, repeatable, onPress)

    private fun row(block: LinearLayout.() -> Unit): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            block()
        }

    private fun spacer(weight: Float): View = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(0, 0, weight)
    }

    private fun textKey(text: String): TextView =
        key(label = text, description = text, onPress = { listener.onText(text) })

    private fun letterKey(letter: Char): TextView {
        val view = key(
            label = letter.toString(),
            description = letter.toString(),
            onPress = { commitLetter(letter) },
        )
        letterKeys.add(view to letter)
        return view
    }

    private fun commitLetter(letter: Char) {
        val out = if (shift == ShiftState.OFF) letter.toString() else letter.uppercaseChar().toString()
        listener.onText(out)
        if (shift == ShiftState.ONCE) setShift(ShiftState.OFF)
    }

    private fun onShiftTap() {
        val now = SystemClock.uptimeMillis()
        val next = when (shift) {
            ShiftState.OFF -> ShiftState.ONCE
            // Ketuk dua kali cepat = caps lock
            ShiftState.ONCE -> if (now - lastShiftTap < DOUBLE_TAP_MS) ShiftState.LOCK else ShiftState.OFF
            ShiftState.LOCK -> ShiftState.OFF
        }
        lastShiftTap = now
        setShift(next)
    }

    private fun setShift(state: ShiftState) {
        shift = state
        val upper = state != ShiftState.OFF
        letterKeys.forEach { (view, letter) ->
            view.text = if (upper) letter.uppercaseChar().toString() else letter.toString()
        }
        shiftKey?.apply {
            text = if (state == ShiftState.LOCK) "⇪" else "⇧"
            setTextColor(if (state == ShiftState.OFF) Palette.text else Palette.accent)
        }
    }
}
