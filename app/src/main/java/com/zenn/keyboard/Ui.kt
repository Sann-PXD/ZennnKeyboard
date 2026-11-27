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

object Palette {
    val background = Color.parseColor("#0E1014")
    val key = Color.parseColor("#1B1F27")
    val keyAction = Color.parseColor("#232834")
    val keyPressed = Color.parseColor("#2C3342")
    val accent = Color.parseColor("#7C9CFF")
    val accentPressed = Color.parseColor("#5F7FE6")
    val text = Color.parseColor("#E9ECF3")
    val textDim = Color.parseColor("#8B93A7")
}

enum class KeyStyle(val fill: Int, val pressed: Int, val text: Int) {
    NORMAL(Palette.key, Palette.keyPressed, Palette.text),
    ACTION(Palette.keyAction, Palette.keyPressed, Palette.text),
    ACCENT(Palette.accent, Palette.accentPressed, Palette.background),
}

fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

fun Context.roundedBackground(
    normal: Int,
    pressed: Int,
    radiusDp: Int = 10,
): StateListDrawable {
    fun shape(color: Int) = GradientDrawable().apply {
        cornerRadius = dp(radiusDp).toFloat()
        setColor(color)
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
    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
    setTextColor(style.text)
    setPadding(dp(14), 0, dp(14), 0)
    background = roundedBackground(style.fill, style.pressed)
    setOnClickListener {
        it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        onClick()
    }
}
