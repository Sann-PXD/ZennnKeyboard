package com.zenn.keyboard

import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.EditorInfo

class KeyboardService : InputMethodService(), KeyboardUi.Listener {

    private var ui: KeyboardUi? = null

    override fun onCreateInputView(): View {
        val built = KeyboardUi(this, this)
        ui = built
        return built.root
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        ui?.reset()
    }

    override fun onText(text: String) {
        currentInputConnection?.commitText(text, 1)
    }

    override fun onBackspace() {
        val ic = currentInputConnection ?: return
        if (ic.getSelectedText(0).isNullOrEmpty()) {
            ic.deleteSurroundingTextInCodePoints(1, 0)
        } else {
            ic.commitText("", 1)
        }
    }

    override fun onEnter() {
        val ic = currentInputConnection ?: return
        val options = currentInputEditorInfo?.imeOptions ?: 0
        val action = options and EditorInfo.IME_MASK_ACTION
        val isMultiLine = (options and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
        val hasAction = action != EditorInfo.IME_ACTION_NONE &&
            action != EditorInfo.IME_ACTION_UNSPECIFIED
        if (hasAction && !isMultiLine) {
            ic.performEditorAction(action)
        } else {
            ic.commitText("\n", 1)
        }
    }
}
