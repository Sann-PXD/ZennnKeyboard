package com.zenn.keyboard

import android.content.Context

// Emoji terakhir dipakai + emoji yang disematkan, tersimpan permanen di HP
class EmojiRecents(context: Context) : PinStore(context, "emoji_recents", 40)
