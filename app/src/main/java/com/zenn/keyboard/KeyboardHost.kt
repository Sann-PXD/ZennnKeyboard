package com.zenn.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupWindow
import kotlin.math.hypot
import kotlin.math.min

private const val HOST_PREFS = "layout"
private const val HOST_KEY_HEIGHT = "key_height_dp"
private const val HOST_KEY_FLOATING = "floating"
private const val HOST_KEY_X = "float_x"
private const val HOST_KEY_Y = "float_y"
private const val HOST_UNSET = Int.MIN_VALUE

private const val HOST_DEFAULT_KEY_DP = 48
private const val HOST_MIN_KEY_DP = 26
private const val HOST_MAX_KEY_DP = 72
private const val HOST_GRIP_HEIGHT_DP = 20
private const val HOST_PILL_WIDTH_DP = 40
private const val HOST_PILL_HEIGHT_DP = 4

// Tinggi bagian selain deretan tombol: pegangan + baris atas + padding
private const val HOST_CHROME_DP = 82

// Batas tinggi keyboard terhadap layar; penting di landscape
private const val HOST_MAX_SCREEN_FRACTION = 0.6f
private const val HOST_FLOAT_WIDTH_FRACTION = 0.9f
private const val HOST_FLOAT_MAX_WIDTH_DP = 480
private const val HOST_FLOAT_MARGIN_DP = 48
private const val HOST_FLOAT_ELEVATION_DP = 8
private const val HOST_PLACEHOLDER_DP = 1
private const val HOST_PLACE_RETRY_MS = 30L
private const val HOST_PLACE_MAX_RETRY = 10

// Mengatur tempat keyboard berada: menempel di bawah atau melayang, tinggi tombol,
// dan penyesuaian layar kecil/landscape. Dikendalikan lewat pegangan (garis kecil)
// di atas keyboard: geser = ubah tinggi (menempel) atau pindah (melayang),
// tahan = ganti mode.
class KeyboardHost(
    private val service: InputMethodService,
    private val ui: KeyboardUi,
) {
    // Dipakai sebagai input view milik sistem
    val view = FrameLayout(service)

    private val prefs = service.getSharedPreferences(HOST_PREFS, Context.MODE_PRIVATE)
    private val handler = Handler(Looper.getMainLooper())
    private val touchSlop = ViewConfiguration.get(service).scaledTouchSlop
    private val metrics get() = service.resources.displayMetrics
    private val pill = View(service)
    private val origin = IntArray(2)
    private var popup: PopupWindow? = null

    private var floating = prefs.getBoolean(HOST_KEY_FLOATING, false)
    private var keyHeightDp = prefs.getInt(HOST_KEY_HEIGHT, HOST_DEFAULT_KEY_DP)
        .coerceIn(HOST_MIN_KEY_DP, HOST_MAX_KEY_DP)
    private var floatX = prefs.getInt(HOST_KEY_X, HOST_UNSET)
    private var floatY = prefs.getInt(HOST_KEY_Y, HOST_UNSET)

    // Keadaan gestur pegangan
    private var downX = 0f
    private var downY = 0f
    private var startHeightDp = 0
    private var startX = 0
    private var startY = 0
    private var dragging = false
    private val toggleOnLongPress = Runnable { toggleFloating() }

    init {
        ui.root.layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        ui.root.addView(buildGrip(), 0)
        applyKeyHeight()
        if (floating) showPlaceholder() else dock()
        refreshPill()
    }

    // Dipanggil saat jendela keyboard tampil; mode melayang baru dibuat di sini
    fun onShown() {
        if (!floating || popup != null) return
        if (view.windowToken == null) {
            view.post { onShown() }
            return
        }
        showFloating()
    }

    fun onHidden() = dismissPopup()

    fun destroy() {
        handler.removeCallbacksAndMessages(null)
        dismissPopup()
    }

    private fun dock() {
        dismissPopup()
        detachRoot()
        view.removeAllViews()
        view.addView(ui.root)
    }

    // Saat melayang, jendela keyboard bawaan hanya menyisakan 1dp supaya aplikasi tidak terdorong
    private fun showPlaceholder() {
        detachRoot()
        view.removeAllViews()
        view.addView(View(service), FrameLayout.LayoutParams(MATCH_PARENT, service.dp(HOST_PLACEHOLDER_DP)))
    }

    private fun showFloating() {
        dismissPopup()
        showPlaceholder()
        val created = PopupWindow(ui.root, floatWidthPx(), WRAP_CONTENT).apply {
            inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
            elevation = service.dp(HOST_FLOAT_ELEVATION_DP).toFloat()
        }
        try {
            created.showAtLocation(view, Gravity.TOP or Gravity.START, 0, 0)
        } catch (e: RuntimeException) {
            // Jendela melayang gagal dibuat: kembali menempel (pilihan tersimpan tidak diubah)
            floating = false
            dock()
            refreshPill()
            return
        }
        popup = created
        refreshPill()
        placeAfterShown(0)
    }

    // Posisi layar saat offset popup (0,0) dibaca dulu (origin), supaya perhitungan
    // benar baik koordinat popup relatif ke jendela keyboard maupun ke layar
    private fun placeAfterShown(attempt: Int) {
        if (popup == null) return
        val root = ui.root
        if (!root.isAttachedToWindow || !root.isLaidOut || root.width == 0) {
            if (attempt < HOST_PLACE_MAX_RETRY) {
                handler.postDelayed({ placeAfterShown(attempt + 1) }, HOST_PLACE_RETRY_MS)
            }
            return
        }
        root.getLocationOnScreen(origin)
        val x = if (floatX == HOST_UNSET) (metrics.widthPixels - root.width) / 2 else floatX
        val y = if (floatY == HOST_UNSET) {
            metrics.heightPixels - root.height - service.dp(HOST_FLOAT_MARGIN_DP)
        } else {
            floatY
        }
        moveTo(x, y)
    }

    private fun moveTo(x: Int, y: Int) {
        val maxX = (metrics.widthPixels - ui.root.width).coerceAtLeast(0)
        val maxY = (metrics.heightPixels - ui.root.height).coerceAtLeast(0)
        floatX = x.coerceIn(0, maxX)
        floatY = y.coerceIn(0, maxY)
        popup?.update(floatX - origin[0], floatY - origin[1], -1, -1)
    }

    private fun toggleFloating() {
        floating = !floating
        if (floating) showFloating() else dock()
        refreshPill()
        save()
    }

    private fun dismissPopup() {
        popup?.dismiss()
        popup = null
    }

    private fun detachRoot() {
        (ui.root.parent as? ViewGroup)?.removeView(ui.root)
    }

    private fun floatWidthPx(): Int =
        min((metrics.widthPixels * HOST_FLOAT_WIDTH_FRACTION).toInt(), service.dp(HOST_FLOAT_MAX_WIDTH_DP))

    // Tinggi tombol yang diinginkan, dibatasi supaya keyboard tidak melebihi
    // sebagian layar (landscape) tetapi tidak lebih kecil dari batas minimum
    private fun fitKeyHeightPx(): Int {
        val gap = service.dp(KEY_GAP_DP)
        val maxTotal = (metrics.heightPixels * HOST_MAX_SCREEN_FRACTION).toInt()
        val maxKey = (maxTotal - service.dp(HOST_CHROME_DP)) / KEY_ROWS - 2 * gap
        return service.dp(keyHeightDp).coerceAtMost(maxKey).coerceAtLeast(service.dp(HOST_MIN_KEY_DP))
    }

    // Struktur dari KeyboardUi: [pegangan, baris atas, area isi]; area isi = [tombol ketik, panel...].
    // Kalau struktur itu berubah, fungsi ini ikut disesuaikan.
    private fun applyKeyHeight() {
        val keyPx = fitKeyHeightPx()
        val panelPx = KEY_ROWS * (keyPx + 2 * service.dp(KEY_GAP_DP))
        val body = ui.root.getChildAt(ui.root.childCount - 1) as? ViewGroup ?: return

        (body.getChildAt(0) as? ViewGroup)?.let { typing ->
            for (r in 0 until typing.childCount) {
                val row = typing.getChildAt(r) as? ViewGroup ?: continue
                for (k in 0 until row.childCount) {
                    val key = row.getChildAt(k)
                    // Pengisi (spacer) bertinggi 0 dan dilewati
                    if ((key.layoutParams?.height ?: 0) > 0) setHeight(key, keyPx)
                }
            }
        }
        for (i in 1 until body.childCount) setHeight(body.getChildAt(i), panelPx)
    }

    private fun setHeight(target: View, px: Int) {
        val params = target.layoutParams ?: return
        if (params.height == px) return
        params.height = px
        target.layoutParams = params
    }

    private fun resize(dy: Float) {
        // Tarik ke atas = lebih tinggi; tepi atas keyboard mengikuti jari
        val deltaDp = (-dy / KEY_ROWS) / metrics.density
        keyHeightDp = (startHeightDp + deltaDp).toInt().coerceIn(HOST_MIN_KEY_DP, HOST_MAX_KEY_DP)
        applyKeyHeight()
    }

    private fun save() {
        prefs.edit()
            .putInt(HOST_KEY_HEIGHT, keyHeightDp)
            .putBoolean(HOST_KEY_FLOATING, floating)
            .putInt(HOST_KEY_X, floatX)
            .putInt(HOST_KEY_Y, floatY)
            .apply()
    }

    private fun refreshPill() {
        val color = if (floating) Palette.accent else Palette.textDim
        pill.background = service.roundedBackground(color, color, 2)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun buildGrip(): View = LinearLayout(service).apply {
        gravity = Gravity.CENTER
        contentDescription = "Pegangan keyboard. Geser untuk ubah tinggi atau pindah, " +
            "tahan untuk melayang atau menempel."
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, service.dp(HOST_GRIP_HEIGHT_DP))
        addView(
            pill,
            LinearLayout.LayoutParams(service.dp(HOST_PILL_WIDTH_DP), service.dp(HOST_PILL_HEIGHT_DP)),
        )
        setOnTouchListener { _, event -> onGripTouch(event) }
    }

    private fun onGripTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                startHeightDp = keyHeightDp
                startX = if (floatX == HOST_UNSET) 0 else floatX
                startY = if (floatY == HOST_UNSET) 0 else floatY
                dragging = false
                handler.postDelayed(toggleOnLongPress, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downX
                val dy = event.rawY - downY
                if (!dragging && hypot(dx, dy) > touchSlop) {
                    dragging = true
                    handler.removeCallbacks(toggleOnLongPress)
                }
                if (dragging) {
                    if (floating) moveTo(startX + dx.toInt(), startY + dy.toInt()) else resize(dy)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(toggleOnLongPress)
                if (dragging) save()
                dragging = false
            }
        }
        return true
    }
}
