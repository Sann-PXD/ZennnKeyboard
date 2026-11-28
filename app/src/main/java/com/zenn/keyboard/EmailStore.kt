package com.zenn.keyboard

import android.content.Context

// Email milik pengguna untuk saran di kolom email, disimpan lokal di HP.
// Yang terakhir dipakai muncul paling depan.
class EmailStore(context: Context) : PinStore(context, "emails", 20)
