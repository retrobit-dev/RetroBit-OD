package dev.retrobit.od.ambient

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import dev.retrobit.od.data.Prefs

/**
 * Mode kompatibilitas: beberapa build XOS menolak overlay di atas keyguard, jadi
 * kita pakai activity layar penuh yang shown-when-locked. Touch dipakai untuk
 * menutup cepat (geser ke atas), selain itu tombol power tetap mematikan layar.
 */
class AmbientActivity : Activity() {

    private var view: AmbientView? = null
    private var downY = 0f

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                AmbientService.ACTION_HIDE -> finishAndClose()
                AmbientService.ACTION_REFRESH -> view?.refreshNow()
                Intent.ACTION_USER_PRESENT -> finishAndClose()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        val prefs = Prefs.get(this)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_FULLSCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )
        val lp = window.attributes
        if (!prefs.forceBrightness) {
            lp.screenBrightness = (prefs.brightnessPercent / 100f).coerceIn(0f, 1f)
        }
        lp.layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        window.attributes = lp

        val ambient = AmbientView(this)
        view = ambient
        setContentView(ambient)

        val filter = IntentFilter().apply {
            addAction(AmbientService.ACTION_HIDE)
            addAction(AmbientService.ACTION_REFRESH)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_HOME ||
            keyCode == KeyEvent.KEYCODE_POWER
        ) {
            finishAndClose()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        when (event.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> downY = event.y
            android.view.MotionEvent.ACTION_UP -> if (downY - event.y > 120f * resources.displayMetrics.density) {
                finishAndClose()
                return true
            }
        }
        return true
    }

    private fun finishAndClose() {
        finish()
        overridePendingTransition(0, 0)
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(receiver) }
        view?.pause()
        view = null
        super.onDestroy()
    }
}
