package com.zenn.keyboard

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    private val imm by lazy { getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Palette.background
        window.navigationBarColor = Palette.background

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Palette.background)
            setPadding(dp(24), dp(48), dp(24), dp(24))
        }
        content.addView(label("Zenn Keyboard", 28f, Palette.text, bold = true))
        content.addView(label("Keyboard gelap untuk coding dari HP.", 15f, Palette.textDim, topDp = 6))
        content.addView(
            actionButton("1. Aktifkan keyboard") {
                startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            },
        )
        content.addView(actionButton("2. Pilih Zenn Keyboard") { imm.showInputMethodPicker() })
        content.addView(testField())
        setContentView(content)
    }

    private fun params(topDp: Int) =
        LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(topDp) }

    private fun label(
        text: String,
        sizeSp: Float,
        color: Int,
        bold: Boolean = false,
        topDp: Int = 0,
    ): TextView = TextView(this).apply {
        this.text = text
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        if (bold) typeface = Typeface.DEFAULT_BOLD
        layoutParams = params(topDp)
    }

    private fun actionButton(text: String, onClick: () -> Unit): Button =
        Button(this).apply {
            this.text = text
            isAllCaps = false
            setTextColor(Palette.background)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            background = roundedBackground(Palette.accent, Palette.accentPressed, 14)
            stateListAnimator = null
            minHeight = dp(52)
            layoutParams = params(16)
            setOnClickListener { onClick() }
        }

    private fun testField(): EditText = EditText(this).apply {
        hint = "Coba ketik di sini"
        setHintTextColor(Palette.textDim)
        setTextColor(Palette.text)
        background = roundedBackground(Palette.key, Palette.key, 14)
        setPadding(dp(16), dp(16), dp(16), dp(16))
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        minLines = 4
        gravity = Gravity.TOP
        layoutParams = params(24)
    }
}
