package com.zenn.keyboard

import android.os.SystemClock
import android.view.KeyEvent
import android.view.inputmethod.InputConnection

class ShortcutRunner(private val connection: () -> InputConnection?) {

    fun run(action: ShortcutAction) {
        val ic = connection() ?: return
        when (action) {
            is ShortcutAction.Menu -> ic.performContextMenuAction(action.id)
            is ShortcutAction.Combo -> sendCombo(ic, action)
            ShortcutAction.SelectLine -> {
                sendCombo(ic, ShortcutAction.Combo(KeyEvent.KEYCODE_MOVE_HOME))
                sendCombo(ic, ShortcutAction.Combo(KeyEvent.KEYCODE_MOVE_END, shift = true))
            }
        }
    }

    // Modifier ditekan dulu, lalu tombol utama, lalu modifier dilepas (urutan terbalik)
    private fun sendCombo(ic: InputConnection, combo: ShortcutAction.Combo) {
        val modifiers = buildList {
            if (combo.ctrl) {
                add(KeyEvent.KEYCODE_CTRL_LEFT to (KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON))
            }
            if (combo.shift) {
                add(KeyEvent.KEYCODE_SHIFT_LEFT to (KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON))
            }
        }
        val meta = modifiers.fold(0) { acc, pair -> acc or pair.second }

        modifiers.forEach { (code, _) -> send(ic, KeyEvent.ACTION_DOWN, code, meta) }
        send(ic, KeyEvent.ACTION_DOWN, combo.keyCode, meta)
        send(ic, KeyEvent.ACTION_UP, combo.keyCode, meta)
        modifiers.asReversed().forEach { (code, _) -> send(ic, KeyEvent.ACTION_UP, code, 0) }
    }

    private fun send(ic: InputConnection, action: Int, keyCode: Int, meta: Int) {
        val now = SystemClock.uptimeMillis()
        ic.sendKeyEvent(KeyEvent(now, now, action, keyCode, 0, meta))
    }
}
