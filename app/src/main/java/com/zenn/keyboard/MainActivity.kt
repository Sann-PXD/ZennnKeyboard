package com.zenn.keyboard

import android.accounts.AccountManager
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.util.Patterns
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

private const val REQUEST_ACCOUNT = 1001

class MainActivity : Activity() {

    companion object {
        // Dikirim dari keyboard: langsung buka pemilih akun
        const val EXTRA_ADD_EMAIL = "add_email"
    }

    private val imm by lazy { getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager }
    private lateinit var emailList: LinearLayout
    private lateinit var manualEmail: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Palette.background
        window.navigationBarColor = Palette.background

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
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

        content.addView(label("Email saya", 18f, Palette.text, bold = true, topDp = 32))
        content.addView(
            label("Muncul sebagai saran di atas keyboard saat mengetik di kolom email.", 13f, Palette.textDim, topDp = 4),
        )
        emailList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = params(12)
        }
        content.addView(emailList)
        content.addView(actionButton("Tambah dari akun di HP") { pickAccount() })
        content.addView(manualEmailRow())

        content.addView(label("Coba keyboard", 18f, Palette.text, bold = true, topDp = 32))
        content.addView(testField("Coba ketik di sini", multiline = true, email = false))
        content.addView(testField("Coba kolom email", multiline = false, email = true))

        setContentView(
            ScrollView(this).apply {
                setBackgroundColor(Palette.background)
                addView(content)
            },
        )
        refreshEmails()
        if (intent.getBooleanExtra(EXTRA_ADD_EMAIL, false)) pickAccount()
    }

    // Pemilih akun bawaan HP: tidak butuh izin, dan hanya akun yang kamu pilih yang terbaca
    @Suppress("DEPRECATION")
    private fun pickAccount() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AccountManager.newChooseAccountIntent(null, null, null, null, null, null, null)
        } else {
            AccountManager.newChooseAccountIntent(null, null, null, false, null, null, null, null)
        }
        try {
            startActivityForResult(intent, REQUEST_ACCOUNT)
        } catch (e: ActivityNotFoundException) {
            toast("Pemilih akun tidak tersedia di HP ini. Tambahkan email secara manual.")
        }
    }

    @Deprecated("Dipakai karena Activity biasa (bukan AndroidX)")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_ACCOUNT || resultCode != RESULT_OK) return
        addEmail(data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME).orEmpty())
    }

    private fun addEmail(raw: String) {
        val email = raw.trim()
        // Akun non-email (mis. nomor telepon) ditolak
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            toast("Itu bukan alamat email yang valid.")
            return
        }
        EmailStore(this).add(email)
        refreshEmails()
    }

    private fun refreshEmails() {
        emailList.removeAllViews()
        val emails = EmailStore(this).snapshot()
        if (emails.isEmpty()) {
            emailList.addView(label("Belum ada email tersimpan.", 14f, Palette.textDim))
            return
        }
        emails.forEach { emailList.addView(emailRow(it)) }
    }

    private fun emailRow(email: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = params(6)
        val text = label(email, 15f, Palette.text).apply {
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        }
        addView(text)
        addView(
            buildChip("✕", "Hapus $email") {
                EmailStore(this@MainActivity).remove(email)
                refreshEmails()
            },
            LinearLayout.LayoutParams(dp(44), dp(44)),
        )
    }

    private fun manualEmailRow(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = params(12)
        manualEmail = testField("atau ketik email sendiri", multiline = false, email = true).apply {
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        }
        addView(manualEmail)
        addView(
            buildChip("Tambah", "Tambah email yang diketik") {
                addEmail(manualEmail.text.toString())
                manualEmail.text.clear()
            },
            LinearLayout.LayoutParams(WRAP_CONTENT, dp(48)).apply { leftMargin = dp(8) },
        )
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

    private fun testField(hintText: String, multiline: Boolean, email: Boolean): EditText =
        EditText(this).apply {
            hint = hintText
            setHintTextColor(Palette.textDim)
            setTextColor(Palette.text)
            background = roundedBackground(Palette.key, Palette.key, 14)
            setPadding(dp(16), dp(16), dp(16), dp(16))
            inputType = when {
                email -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                multiline -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                else -> InputType.TYPE_CLASS_TEXT
            }
            if (multiline) {
                minLines = 3
                gravity = Gravity.TOP
            }
            layoutParams = params(12)
        }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}
