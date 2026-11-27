package com.zenn.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

private const val KEY_HEIGHT_DP = 48
private const val KEY_GAP_DP = 3
private const val REPEAT_DELAY_MS = 400L
private const val REPEAT_INTERVAL_MS = 45L
private const val DOUBLE_TAP_MS = 400L

enum class ShiftState { OFF, ONCE, LOCK }

enum class KeyStyle { NORMAL, ACTION, ACCENT }

class KeyboardUi(
    private val context: Context,
    private val listener: Listener,
) {
    interface Listener {
        fun onText(text: String)
        fun onBackspace()
        fun onEnter()
    }

    val root: LinearLayout = LinearLayout(context)
    private val handler = Handler(Looper.getMainLooper())
    private val letterKeys = mutableListOf<Pair<TextView, Char>>()
    private var shiftKey: TextView? = null
    private var shift = ShiftState.OFF
    private var lastShiftTap = 0L

    init {
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Palette.background)
        root.setPadding(context.dp(4), context.dp(6), context.dp(4), context.dp(8))
        build()
    }

    fun reset() = setShift(ShiftState.OFF)

    private fun build() {
        root.addView(row { "1234567890".forEach { addView(textKey(it.toString())) } })
        root.addView(row { "qwertyuiop".forEach { addView(letterKey(it)) } })
        root.addView(
            row {
                addView(spacer(0.5f))
                "asdfghjkl".forEach { addView(letterKey(it)) }
                addView(spacer(0.5f))
            },
        )
        root.addView(
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
        root.addView(
            row {
                addView(textKey(","))
                addView(key("Zenn", "Spasi", 5f, onPress = { listener.onText(" ") }))
                addView(textKey("."))
                addView(key("↵", "Enter", 1.5f, KeyStyle.ACCENT, onPress = { listener.onEnter() }))
            },
        )
    }

    private fun row(block: LinearLayout.() -> Unit): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
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

    @SuppressLint("ClickableViewAccessibility")
    private fun key(
        label: String,
        description: String,
        weight: Float = 1f,
        style: KeyStyle = KeyStyle.NORMAL,
        repeatable: Boolean = false,
        onPress: () -> Unit,
    ): TextView {
        val view = TextView(context).apply {
            text = label
            contentDescription = description
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTextColor(if (style == KeyStyle.ACCENT) Palette.background else Palette.text)
            val (normal, pressed) = when (style) {
                KeyStyle.NORMAL -> Palette.key to Palette.keyPressed
                KeyStyle.ACTION -> Palette.keyAction to Palette.keyPressed
                KeyStyle.ACCENT -> Palette.accent to Palette.accentPressed
            }
            background = context.roundedBackground(normal, pressed)
            layoutParams = LinearLayout.LayoutParams(0, context.dp(KEY_HEIGHT_DP), weight).apply {
                val gap = context.dp(KEY_GAP_DP)
                setMargins(gap, gap, gap, gap)
            }
        }

        val repeater = if (repeatable) Repeater(handler) else null
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onPress()
                    repeater?.start(onPress)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    repeater?.stop()
                }
            }
            true
        }
        return view
    }
}

// Tahan tombol = aksi berulang (dipakai backspace)
private class Repeater(private val handler: Handler) {
    private var task: Runnable? = null

    fun start(action: () -> Unit) {
        stop()
        val next = object : Runnable {
            override fun run() {
                action()
                handler.postDelayed(this, REPEAT_INTERVAL_MS)
            }
        }
        task = next
        handler.postDelayed(next, REPEAT_DELAY_MS)
    }

    fun stop() {
        task?.let { handler.removeCallbacks(it) }
        task = null
    }
}
