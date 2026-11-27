package com.zenn.keyboard

import android.view.KeyEvent

sealed interface ShortcutAction {
    // Aksi menu konteks bawaan Android (salin, tempel, dll.)
    data class Menu(val id: Int) : ShortcutAction

    // Kombinasi tombol, contoh Ctrl+Shift+Z
    data class Combo(
        val keyCode: Int,
        val ctrl: Boolean = false,
        val shift: Boolean = false,
    ) : ShortcutAction

    object SelectLine : ShortcutAction
}

data class Shortcut(
    val label: String,
    val description: String,
    val action: ShortcutAction,
)

data class ShortcutGroup(val title: String, val items: List<Shortcut>)

private fun menu(label: String, description: String, id: Int) =
    Shortcut(label, description, ShortcutAction.Menu(id))

private fun combo(
    label: String,
    description: String,
    keyCode: Int,
    ctrl: Boolean = false,
    shift: Boolean = false,
) = Shortcut(label, description, ShortcutAction.Combo(keyCode, ctrl, shift))

object Shortcuts {
    private val selectAll = menu("Semua", "Pilih semua", android.R.id.selectAll)
    private val copy = menu("Salin", "Salin", android.R.id.copy)
    private val cut = menu("Potong", "Potong", android.R.id.cut)
    private val paste = menu("Tempel", "Tempel", android.R.id.paste)
    private val undo = combo("Undo", "Urungkan", KeyEvent.KEYCODE_Z, ctrl = true)
    private val redo = combo("Redo", "Ulangi", KeyEvent.KEYCODE_Z, ctrl = true, shift = true)
    private val tab = combo("Tab", "Tab", KeyEvent.KEYCODE_TAB)
    private val left = combo("←", "Kursor kiri", KeyEvent.KEYCODE_DPAD_LEFT)
    private val right = combo("→", "Kursor kanan", KeyEvent.KEYCODE_DPAD_RIGHT)

    // Baris cepat di atas keyboard
    val quick = listOf(selectAll, copy, cut, paste, undo, redo, tab, left, right)

    // Isi panel lengkap (tombol ☰)
    val groups = listOf(
        ShortcutGroup("Edit", listOf(selectAll, copy, cut, paste, undo, redo)),
        ShortcutGroup(
            "Gerak kursor",
            listOf(
                left,
                right,
                combo("↑", "Kursor atas", KeyEvent.KEYCODE_DPAD_UP),
                combo("↓", "Kursor bawah", KeyEvent.KEYCODE_DPAD_DOWN),
                combo("Home", "Awal baris", KeyEvent.KEYCODE_MOVE_HOME),
                combo("End", "Akhir baris", KeyEvent.KEYCODE_MOVE_END),
                combo("PgUp", "Halaman atas", KeyEvent.KEYCODE_PAGE_UP),
                combo("PgDn", "Halaman bawah", KeyEvent.KEYCODE_PAGE_DOWN),
                combo("⇤ File", "Awal file", KeyEvent.KEYCODE_MOVE_HOME, ctrl = true),
                combo("File ⇥", "Akhir file", KeyEvent.KEYCODE_MOVE_END, ctrl = true),
                combo("← Kata", "Kata sebelumnya", KeyEvent.KEYCODE_DPAD_LEFT, ctrl = true),
                combo("Kata →", "Kata berikutnya", KeyEvent.KEYCODE_DPAD_RIGHT, ctrl = true),
            ),
        ),
        ShortcutGroup(
            "Seleksi",
            listOf(
                Shortcut("Baris", "Pilih satu baris", ShortcutAction.SelectLine),
                combo("⇧ Awal", "Pilih sampai awal baris", KeyEvent.KEYCODE_MOVE_HOME, shift = true),
                combo("⇧ Akhir", "Pilih sampai akhir baris", KeyEvent.KEYCODE_MOVE_END, shift = true),
                combo("⇧ ←", "Perluas pilihan ke kiri", KeyEvent.KEYCODE_DPAD_LEFT, shift = true),
                combo("⇧ →", "Perluas pilihan ke kanan", KeyEvent.KEYCODE_DPAD_RIGHT, shift = true),
                combo("⇧ ← Kata", "Pilih kata sebelumnya", KeyEvent.KEYCODE_DPAD_LEFT, ctrl = true, shift = true),
                combo("⇧ Kata →", "Pilih kata berikutnya", KeyEvent.KEYCODE_DPAD_RIGHT, ctrl = true, shift = true),
            ),
        ),
        ShortcutGroup(
            "Editor",
            listOf(
                combo("Esc", "Escape", KeyEvent.KEYCODE_ESCAPE),
                tab,
                combo("⌫ Kata", "Hapus kata sebelumnya", KeyEvent.KEYCODE_DEL, ctrl = true),
                combo("Del", "Hapus ke depan", KeyEvent.KEYCODE_FORWARD_DEL),
                combo("Simpan", "Simpan (Ctrl+S)", KeyEvent.KEYCODE_S, ctrl = true),
                combo("Cari", "Cari (Ctrl+F)", KeyEvent.KEYCODE_F, ctrl = true),
                combo("Komentar", "Komentari baris (Ctrl+/)", KeyEvent.KEYCODE_SLASH, ctrl = true),
                combo("Duplikat", "Duplikat baris (Ctrl+D)", KeyEvent.KEYCODE_D, ctrl = true),
            ),
        ),
    )
}
