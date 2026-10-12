package dev.retrobit.od.service

import android.service.dreams.DreamService
import dev.retrobit.od.ambient.AmbientView

/**
 * Screensaver (Daydream) — jalur paling hemat untuk "jam nyala terus" saat HP di
 * charger/dock: sistem yang mengatur kapan tampil, dan layar boleh dibiarkan redup
 * (setScreenBright(false)). Aktifkan di Setelan → Layar → Screensaver → RetroBit OD.
 */
class AmbientDreamService : DreamService() {

    private var view: AmbientView? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        setFullscreen(true)
        setInteractive(false)
        setScreenBright(false)
        val ambient = AmbientView(this)
        view = ambient
        setContentView(ambient)
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        view?.resume()
    }

    override fun onDreamingStopped() {
        view?.pause()
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        view?.pause()
        view = null
        super.onDetachedFromWindow()
    }
}
