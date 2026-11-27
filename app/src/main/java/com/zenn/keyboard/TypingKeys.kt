package com.zenn.keyboard

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.TextView

private const val DOUBLE_TAP_MS = 400L
private const val EMOJI_KEY_WEIGHT = 1.3f
private const val SPACE_WEIGHT = 4.2f

enum class ShiftState { OFF, ONCE, LOCK }

// Area tombol ketik: angka, huruf, shift, backspace, spasi, enter, dan tombol emoji
class TypingKeys(
    private val context: Context,
    private val listener: KeyboardUi.Listener,
    private val onOpenEmoji: () -> Unit,
) {
    val view = LinearLayout(context)

    private val handler = Handler(Looper.getMainLooper())
    private val letterKeys = mutableListOf<Pair<TextView, Char>>()
    private var shiftKey: TextView? = null
    private var shift = ShiftState.OFF
    private var lastShiftTap = 0L

    init {
        view.orientation = LinearLayout.VERTICAL
        build()
    }

    fun resetShift() = setShift(ShiftState.OFF)

    private fun build() {
        view.addView(row { "1234567890".forEach { addView(textKey(it.toString())) } })
        view.addView(row { "qwertyuiop".forEach { addView(letterKey(it)) } })
        view.addView(
            row {
                addView(spacer(0.5f))
                "asdfghjkl".forEach { addView(letterKey(it)) }
                addView(spacer(0.5f))
            },
        )
        view.addView(
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
        view.addView(
            row {
                addView(emojiKey())
                addView(textKey(","))
                addView(key("Zenn", "Spasi", SPACE_WEIGHT, onPress = { listener.onText(" ") }))
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

    // Aktif saat jari dilepas, supaya panel emoji tidak menangkap sisa sentuhan
    private fun emojiKey(): TextView =
        context.buildChip("😀", "Emoji", KeyStyle.ACTION) { onOpenEmoji() }.apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setPadding(0, 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(0, context.dp(KEY_HEIGHT_DP), EMOJI_KEY_WEIGHT).apply {
                val gap = context.dp(KEY_GAP_DP)
                setMargins(gap, gap, gap, gap)
            }
        }

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
        val keyView = key(
            label = letter.toString(),
            description = letter.toString(),
            onPress = { commitLetter(letter) },
        )
        letterKeys.add(keyView to letter)
        return keyView
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
        letterKeys.forEach { (keyView, letter) ->
            keyView.text = if (upper) letter.uppercaseChar().toString() else letter.toString()
        }
        shiftKey?.apply {
            text = if (state == ShiftState.LOCK) "⇪" else "⇧"
            setTextColor(if (state == ShiftState.OFF) Palette.text else Palette.accent)
        }
    }
}
