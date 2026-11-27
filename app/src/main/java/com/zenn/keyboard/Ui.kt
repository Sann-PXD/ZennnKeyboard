package com.zenn.keyboard

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable

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
