package com.zenn.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import kotlin.math.hypot
import kotlin.math.min

private const val HOST_PREFS = "layout"
private const val HOST_KEY_HEIGHT = "key_height_dp"
private const val HOST_KEY_FLOAT_HEIGHT = "float_key_dp"
private const val HOST_KEY_FLOAT_WIDTH = "float_width_dp"
private const val HOST_KEY_FLOATING = "floating"
private const val HOST_KEY_X = "float_x"
private const val HOST_KEY_Y = "float_y"
private const val HOST_UNSET = Int.MIN_VALUE

// Tinggi tombol (dp): menempel dan melayang punya ukuran sendiri
private const val HOST_DEFAULT_KEY_DP = 48
private const val HOST_MIN_KEY_DP = 26
private const val HOST_MAX_KEY_DP = 72
private const val HOST_FLOAT_DEFAULT_KEY_DP = 34
private const val HOST_FLOAT_MIN_KEY_DP = 18
private const val HOST_FLOAT_MAX_KEY_DP = 60

// Lebar keyboard melayang (dp)
private const val HOST_FLOAT_MIN_WIDTH_DP = 220
private const val HOST_FLOAT_DEFAULT_MAX_WIDTH_DP = 380
private const val HOST_FLOAT_DEFAULT_WIDTH_FRACTION = 0.8f

// Susunan di dalam keyboard: [pegangan, baris atas, area isi, pegangan ukuran (melayang)]
private const val HOST_BAR_INDEX = 1
private const val HOST_BODY_INDEX = 2
private const val HOST_GRIP_HEIGHT_DP = 20
private const val HOST_FOOTER_DP = 24
private const val HOST_PILL_WIDTH_DP = 40
private const val HOST_PILL_HEIGHT_DP = 4
private const val HOST_DIVIDER_DP = 22

// Tinggi bagian selain deretan tombol: pegangan + baris atas + padding
private const val HOST_CHROME_DP = 74

// Batas tinggi keyboard terhadap layar; menempel dibatasi lebih ketat (penting di landscape)
private const val HOST_MAX_SCREEN_FRACTION = 0.6f
private const val HOST_FLOAT_MAX_SCREEN_FRACTION = 0.9f

// Ukuran teks tombol dan tinggi chip baris atas ikut skala tinggi tombol
private const val HOST_TEXT_RATIO = 0.38f
private const val HOST_TEXT_MIN_SP = 9f
private const val HOST_TEXT_MAX_SP = 20f
private const val HOST_LONG_LABEL_SCALE = 0.75f
private const val HOST_BAR_RATIO = 0.85f
private const val HOST_BAR_MIN_DP = 32
private const val HOST_BAR_MAX_DP = 40

private const val HOST_FLOAT_MARGIN_DP = 48
private const val HOST_FLOAT_ELEVATION_DP = 8
private const val HOST_PLACEHOLDER_DP = 1
private const val HOST_PLACE_RETRY_MS = 30L
private const val HOST_PLACE_MAX_RETRY = 10

// Mengatur tempat dan ukuran keyboard: menempel di bawah atau melayang, tinggi tombol,
// penyesuaian layar kecil/landscape, dan kerapian ukuran antar bagian.
// Pegangan atas: geser = ubah tinggi (menempel) / pindah (melayang), tahan = ganti mode.
// Pegangan bawah (hanya saat melayang): geser = ubah lebar dan tinggi.
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
    private val footer = LinearLayout(service)
    private val origin = IntArray(2)
    private var popup: PopupWindow? = null

    private var floating = prefs.getBoolean(HOST_KEY_FLOATING, false)
    private var dockKeyDp = prefs.getInt(HOST_KEY_HEIGHT, HOST_DEFAULT_KEY_DP)
        .coerceIn(HOST_MIN_KEY_DP, HOST_MAX_KEY_DP)
    private var floatKeyDp = prefs.getInt(HOST_KEY_FLOAT_HEIGHT, HOST_FLOAT_DEFAULT_KEY_DP)
        .coerceIn(HOST_FLOAT_MIN_KEY_DP, HOST_FLOAT_MAX_KEY_DP)
    private var floatWidthDp = prefs.getInt(HOST_KEY_FLOAT_WIDTH, HOST_UNSET)
    private var floatX = prefs.getInt(HOST_KEY_X, HOST_UNSET)
    private var floatY = prefs.getInt(HOST_KEY_Y, HOST_UNSET)
    private val activeKeyDp get() = if (floating) floatKeyDp else dockKeyDp

    // Keadaan gestur
    private var downX = 0f
    private var downY = 0f
    private var startKeyDp = 0
    private var startWidthPx = 0
    private var startX = 0
    private var startY = 0
    private var dragging = false
    private val toggleOnLongPress = Runnable { toggleFloating() }

    init {
        ui.root.layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        ui.root.setPadding(service.dp(3), 0, service.dp(3), service.dp(6))
        ui.root.addView(buildGrip(), 0)
        ui.root.addView(buildFooter())
        addBarDivider()
        applyMetrics()
        if (floating) showPlaceholder() else dock()
        refreshState()
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
            applyMetrics()
            dock()
            refreshState()
            return
        }
        popup = created
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
        applyMetrics()
        if (floating) showFloating() else dock()
        refreshState()
        save()
    }

    private fun dismissPopup() {
        popup?.dismiss()
        popup = null
    }

    private fun detachRoot() {
        (ui.root.parent as? ViewGroup)?.removeView(ui.root)
    }

    private fun floatWidthPx(): Int {
        val screenDp = metrics.widthPixels / metrics.density
        val defaultDp = min(screenDp * HOST_FLOAT_DEFAULT_WIDTH_FRACTION, HOST_FLOAT_DEFAULT_MAX_WIDTH_DP.toFloat())
        val widthDp = if (floatWidthDp == HOST_UNSET) defaultDp else floatWidthDp.toFloat()
        return service.dp(widthDp.coerceAtLeast(HOST_FLOAT_MIN_WIDTH_DP.toFloat()).coerceAtMost(screenDp).toInt())
    }

    // Tinggi tombol yang diinginkan, dibatasi supaya keyboard tidak melebihi sebagian
    // layar tetapi tidak lebih kecil dari batas minimum
    private fun fitKeyHeightPx(): Int {
        val fraction = if (floating) HOST_FLOAT_MAX_SCREEN_FRACTION else HOST_MAX_SCREEN_FRACTION
        val chromeDp = HOST_CHROME_DP + if (floating) HOST_FOOTER_DP else 0
        val maxTotal = (metrics.heightPixels * fraction).toInt()
        val maxKey = (maxTotal - service.dp(chromeDp)) / KEY_ROWS - 2 * service.dp(KEY_GAP_DP)
        val minDp = if (floating) HOST_FLOAT_MIN_KEY_DP else HOST_MIN_KEY_DP
        return service.dp(activeKeyDp).coerceAtMost(maxKey).coerceAtLeast(service.dp(minDp))
    }

    // Menyamakan ukuran semua bagian dengan tinggi tombol: tombol, teks tombol, chip
    // baris atas, dan tinggi panel. Bergantung pada susunan KeyboardUi (lihat konstanta
    // HOST_*_INDEX); kalau susunannya berubah, fungsi ini ikut disesuaikan.
    private fun applyMetrics() {
        val keyPx = fitKeyHeightPx()
        val textSp = (keyPx / metrics.density * HOST_TEXT_RATIO).coerceIn(HOST_TEXT_MIN_SP, HOST_TEXT_MAX_SP)
        val barPx = (keyPx * HOST_BAR_RATIO).toInt()
            .coerceIn(service.dp(HOST_BAR_MIN_DP), service.dp(HOST_BAR_MAX_DP))
        val panelPx = KEY_ROWS * (keyPx + 2 * service.dp(KEY_GAP_DP))

        (ui.root.getChildAt(HOST_BAR_INDEX) as? ViewGroup)?.let { bar ->
            forEachChip(bar) { setHeight(it, barPx) }
        }
        val body = ui.root.getChildAt(HOST_BODY_INDEX) as? ViewGroup ?: return
        (body.getChildAt(0) as? ViewGroup)?.let { typing ->
            for (r in 0 until typing.childCount) {
                val row = typing.getChildAt(r) as? ViewGroup ?: continue
                for (k in 0 until row.childCount) {
                    val key = row.getChildAt(k)
                    // Pengisi (spacer) bertinggi 0 dan dilewati
                    if ((key.layoutParams?.height ?: 0) <= 0) continue
                    setHeight(key, keyPx)
                    if (key is TextView) {
                        val scale = if (key.text.length > 2) HOST_LONG_LABEL_SCALE else 1f
                        key.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSp * scale)
                    }
                }
            }
        }
        for (i in 1 until body.childCount) setHeight(body.getChildAt(i), panelPx)
    }

    // Chip baris atas: tombol tetap + chip di dalam deretan yang bisa digeser
    private fun forEachChip(bar: ViewGroup, action: (View) -> Unit) {
        for (i in 0 until bar.childCount) {
            val child = bar.getChildAt(i)
            if (child is TextView) {
                action(child)
            } else if (child is HorizontalScrollView) {
                val strip = child.getChildAt(0) as? ViewGroup ?: continue
                for (j in 0 until strip.childCount) action(strip.getChildAt(j))
            }
        }
    }

    private fun setHeight(target: View, px: Int) {
        val params = target.layoutParams ?: return
        if (params.height == px) return
        params.height = px
        target.layoutParams = params
    }

    // Garis tipis pemisah antara tombol tetap dan deretan pintasan
    private fun addBarDivider() {
        val bar = ui.root.getChildAt(HOST_BAR_INDEX) as? ViewGroup ?: return
        val at = (0 until bar.childCount).firstOrNull { bar.getChildAt(it) is HorizontalScrollView } ?: return
        val divider = View(service).apply { setBackgroundColor(Palette.keyPressed) }
        val params = LinearLayout.LayoutParams(service.dp(1), service.dp(HOST_DIVIDER_DP)).apply {
            setMargins(service.dp(4), 0, service.dp(4), 0)
            gravity = Gravity.CENTER_VERTICAL
        }
        bar.addView(divider, at, params)
    }

    private fun resizeDocked(dy: Float) {
        // Tarik ke atas = lebih tinggi; tepi atas keyboard mengikuti jari
        val deltaDp = (-dy / KEY_ROWS) / metrics.density
        dockKeyDp = (startKeyDp + deltaDp).toInt().coerceIn(HOST_MIN_KEY_DP, HOST_MAX_KEY_DP)
        applyMetrics()
    }

    private fun resizeFloating(dx: Float, dy: Float) {
        val widthPx = (startWidthPx + dx).toInt()
            .coerceAtLeast(service.dp(HOST_FLOAT_MIN_WIDTH_DP))
            .coerceAtMost(metrics.widthPixels)
        floatWidthDp = (widthPx / metrics.density).toInt()
        val deltaDp = (dy / KEY_ROWS) / metrics.density
        floatKeyDp = (startKeyDp + deltaDp).toInt().coerceIn(HOST_FLOAT_MIN_KEY_DP, HOST_FLOAT_MAX_KEY_DP)
        applyMetrics()
        popup?.update(floatX - origin[0], floatY - origin[1], floatWidthPx(), -1)
        // Setelah ukuran berubah, pastikan keyboard tetap di dalam layar
        ui.root.post { moveTo(floatX, floatY) }
    }

    private fun save() {
        prefs.edit()
            .putInt(HOST_KEY_HEIGHT, dockKeyDp)
            .putInt(HOST_KEY_FLOAT_HEIGHT, floatKeyDp)
            .putInt(HOST_KEY_FLOAT_WIDTH, floatWidthDp)
            .putBoolean(HOST_KEY_FLOATING, floating)
            .putInt(HOST_KEY_X, floatX)
            .putInt(HOST_KEY_Y, floatY)
            .apply()
    }

    private fun refreshState() {
        val color = if (floating) Palette.accent else Palette.textDim
        pill.background = service.roundedBackground(color, color, 2)
        footer.visibility = if (floating) View.VISIBLE else View.GONE
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

    @SuppressLint("ClickableViewAccessibility")
    private fun buildFooter(): View = footer.apply {
        gravity = Gravity.CENTER
        contentDescription = "Pegangan ukuran keyboard melayang. Geser untuk ubah lebar dan tinggi."
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, service.dp(HOST_FOOTER_DP))
        val bar = View(service).apply {
            background = service.roundedBackground(Palette.accent, Palette.accent, 2)
        }
        addView(bar, LinearLayout.LayoutParams(service.dp(HOST_PILL_WIDTH_DP), service.dp(HOST_PILL_HEIGHT_DP)))
        setOnTouchListener { _, event -> onFooterTouch(event) }
    }

    private fun onGripTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                startKeyDp = dockKeyDp
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
                    if (floating) moveTo(startX + dx.toInt(), startY + dy.toInt()) else resizeDocked(dy)
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

    private fun onFooterTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                startKeyDp = floatKeyDp
                startWidthPx = floatWidthPx()
            }
            MotionEvent.ACTION_MOVE -> resizeFloating(event.rawX - downX, event.rawY - downY)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> save()
        }
        return true
    }
}
