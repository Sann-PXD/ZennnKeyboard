package com.zenn.keyboard

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.widget.TextView

private const val CHIP_MIN_WIDTH_DP = 44
private const val GLASS_FADE = 0.7f

// Tema "dark liquid glass": hanya hitam, abu-abu, dan putih. Permukaan memakai putih
// transparan di atas latar gelap transparan, jadi ringan dan tanpa blur.
object Palette {
    val background = Color.parseColor("#0B0B0D")
    val glassTop = Color.parseColor("#EB1C1C20")
    val glassBottom = Color.parseColor("#F00A0A0C")
    val key = Color.parseColor("#26FFFFFF")
    val keyAction = Color.parseColor("#17FFFFFF")
    val keyPressed = Color.parseColor("#47FFFFFF")
    val stroke = Color.parseColor("#1FFFFFFF")
    val accent = Color.parseColor("#FFFFFF")
    val accentPressed = Color.parseColor("#C7C7CC")
    val text = Color.parseColor("#DCDCE1")
    val textDim = Color.parseColor("#8E8E93")
}

enum class KeyStyle(val fill: Int, val pressed: Int, val text: Int) {
    NORMAL(Palette.key, Palette.keyPressed, Palette.text),
    ACTION(Palette.keyAction, Palette.keyPressed, Palette.text),
    ACCENT(Palette.accent, Palette.accentPressed, Palette.background),
}

fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

// Bagian bawah permukaan dibuat sedikit lebih transparan: kesan kaca
private fun fade(color: Int): Int =
    if (Color.alpha(color) == 255) {
        color
    } else {
        Color.argb(
            (Color.alpha(color) * GLASS_FADE).toInt(),
            Color.red(color),
            Color.green(color),
            Color.blue(color),
        )
    }

fun Context.roundedBackground(
    normal: Int,
    pressed: Int,
    radiusDp: Int = 12,
): StateListDrawable {
    fun shape(color: Int) = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(color, fade(color)),
    ).apply {
        cornerRadius = dp(radiusDp).toFloat()
        setStroke(1, Palette.stroke)
    }
    return StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_pressed), shape(pressed))
        addState(intArrayOf(), shape(normal))
    }
}

// Chip: aksi jalan saat jari dilepas, jadi aman dipakai di area yang bisa digeser
fun Context.buildChip(
    label: String,
    description: String,
    style: KeyStyle = KeyStyle.ACTION,
    onClick: () -> Unit,
): TextView = TextView(this).apply {
    text = label
    contentDescription = description
    gravity = Gravity.CENTER
    minWidth = dp(CHIP_MIN_WIDTH_DP)
    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
    setTextColor(style.text)
    setPadding(dp(12), 0, dp(12), 0)
    background = roundedBackground(style.fill, style.pressed)
    setOnClickListener {
        it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        onClick()
    }
}
