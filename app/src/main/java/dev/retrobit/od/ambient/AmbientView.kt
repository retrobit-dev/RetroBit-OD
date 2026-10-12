package dev.retrobit.od.ambient

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import androidx.core.view.ViewCompat
import dev.retrobit.od.data.Prefs
import dev.retrobit.od.render.AmbientRenderer
import dev.retrobit.od.render.RenderState

/**
 * Kanvas AOD. dipakai oleh 4 tempat: overlay window, AmbientActivity, DreamService,
 * dan pratinjau di halaman Gaya.
 *
 * Ticker hanya memanggil invalidate() kalau kontennya berubah (menit, geseran anti
 * burn-in, status kartu) — jam detik mati = 1 gambar per menit.
 */
class AmbientView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    private val preview: Boolean = false
) : View(context, attrs), SharedPreferences.OnSharedPreferenceChangeListener {

    private val prefs = Prefs.get(context)
    private val renderer = AmbientRenderer(context, prefs)
    private val state = RenderState()
    private val handler = Handler(Looper.getMainLooper())

    private var insetTop = 0
    private var insetBottom = 0
    private var running = false

    private val ticker = object : Runnable {
        override fun run() {
            if (state.update(context, prefs)) invalidate()
            handler.postDelayed(this, tickMillis())
        }
    }

    init {
        setBackgroundColor(Color.BLACK)
    }

    private fun tickMillis(): Long = if (prefs.showSeconds) 500L else 3000L

    fun resume() {
        if (running) return
        running = true
        readInsets()
        state.update(context, prefs)
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    fun pause() {
        running = false
        handler.removeCallbacks(ticker)
    }

    /** Paksa hitung ulang + redraw (dipakai saat user mengubah opsi / reveal kartu). */
    fun refreshNow() {
        state.update(context, prefs)
        invalidate()
    }

    fun currentInsets(): Pair<Int, Int> = insetTop to insetBottom

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        prefs.addListener(this)
        resume()
    }

    override fun onDetachedFromWindow() {
        prefs.removeListener(this)
        pause()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        readInsets()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        state.update(context, prefs)
        renderer.draw(canvas, width, height, state, insetTop, insetBottom)
    }

    override fun onSharedPreferenceChanged(sp: SharedPreferences?, key: String?) {
        post { if (running) refreshNow() }
    }

    private fun readInsets() {
        val insets = ViewCompat.getRootWindowInsets(this)
        val cutout = insets?.displayCutout
        val radius = (26f * resources.displayMetrics.density).toInt()
        val statusBar = dimension("status_bar_height")
        val navBar = dimension("navigation_bar_height")
        insetTop = maxOf(cutout?.safeInsetTop ?: 0, statusBar) + if (preview) 0 else radius / 2
        insetBottom = maxOf(cutout?.safeInsetBottom ?: 0, navBar) + if (preview) 0 else radius / 2
    }

    private fun dimension(name: String): Int {
        val id = resources.getIdentifier(name, "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else 0
    }
}
