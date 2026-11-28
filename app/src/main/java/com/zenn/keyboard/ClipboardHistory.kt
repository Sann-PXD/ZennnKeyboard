package com.zenn.keyboard

import android.content.ClipboardManager
import android.content.Context

private const val CLIP_PREFS = "clipboard_history"
private const val CLIP_MAX_ITEMS = 50
private const val CLIP_MAX_CHARS = 5_000

// Penanda "sensitif" dari Android 13+ (mis. salinan dari pengelola password)
private const val CLIP_EXTRA_SENSITIVE = "android.content.extra.IS_SENSITIVE"

// Riwayat clipboard (permanen, bisa disematkan) yang mencatat setiap salinan baru
class ClipboardHistory(context: Context) :
    PinStore(context, CLIP_PREFS, CLIP_MAX_ITEMS, CLIP_MAX_CHARS) {

    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener { captureCurrent() }

    fun start() = manager.addPrimaryClipChangedListener(clipListener)

    fun stop() = manager.removePrimaryClipChangedListener(clipListener)

    fun captureCurrent() {
        try {
            val clip = manager.primaryClip ?: return
            if (clip.itemCount == 0) return
            val sensitive = clip.description?.extras?.getBoolean(CLIP_EXTRA_SENSITIVE) == true
            if (sensitive) return
            add(clip.getItemAt(0).coerceToText(appContext).toString())
        } catch (e: SecurityException) {
            // Android menolak akses clipboard saat ini; abaikan
        }
    }
}
