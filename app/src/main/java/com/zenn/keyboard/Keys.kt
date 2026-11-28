package com.zenn.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.widget.LinearLayout
import android.widget.TextView

const val KEY_HEIGHT_DP = 48
const val KEY_GAP_DP = 2
const val KEY_ROWS = 5
const val KEYS_AREA_HEIGHT_DP = KEY_ROWS * (KEY_HEIGHT_DP + 2 * KEY_GAP_DP)

private const val REPEAT_DELAY_MS = 350L
private const val REPEAT_INTERVAL_MS = 45L

// Tombol ketik: karakter dikirim saat jari menyentuh. Tahan = mengulang (tombol
// biasa seperti huruf/angka/spasi, dan tombol yang diberi repeatable seperti hapus).
@SuppressLint("ClickableViewAccessibility")
fun Context.buildKey(
    handler: Handler,
    label: String,
    description: String,
    weight: Float = 1f,
    style: KeyStyle = KeyStyle.NORMAL,
    repeatable: Boolean = false,
    onPress: () -> Unit,
): TextView {
    val view = TextView(this).apply {
        text = label
        contentDescription = description
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        setTextColor(style.text)
        background = roundedBackground(style.fill, style.pressed)
        layoutParams = LinearLayout.LayoutParams(0, dp(KEY_HEIGHT_DP), weight).apply {
            val gap = dp(KEY_GAP_DP)
            setMargins(gap, gap, gap, gap)
        }
    }

    val repeater = if (repeatable || style == KeyStyle.NORMAL) Repeater(handler) else null
    view.setOnTouchListener { v, event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                v.isPressed = true
                // Karakter dikirim dulu; getaran memanggil sistem dan bisa memperlambat ketikan
                onPress()
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                repeater?.start({ v.isPressed && v.isAttachedToWindow }, onPress)
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

private class Repeater(private val handler: Handler) {
    private var task: Runnable? = null

    // isHeld false (jari lepas / tampilan hilang) menghentikan pengulangan
    fun start(isHeld: () -> Boolean, action: () -> Unit) {
        stop()
        val next = object : Runnable {
            override fun run() {
                if (!isHeld()) {
                    task = null
                    return
                }
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
