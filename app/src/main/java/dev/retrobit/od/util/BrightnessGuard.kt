package dev.retrobit.od.util

import android.content.Context
import android.provider.Settings
import dev.retrobit.od.data.Prefs

/**
 * Beberapa ROM (termasuk XOS) mengabaikan `screenBrightness` pada window overlay.
 * Bila user mengizinkan WRITE_SETTINGS, kita turunkan brightness sistem saat AOD
 * tampil dan mengembalikannya saat AOD hilang. Semua dibungkus try/catch: kalau
 * OEM memblokir, tampilan tetap jalan (hanya brightness mengikuti sistem).
 */
object BrightnessGuard {

    private var savedBrightness = -1
    private var savedMode = -1

    fun canWrite(context: Context): Boolean = Settings.System.canWrite(context)

    fun apply(context: Context, prefs: Prefs) {
        if (!prefs.forceBrightness || savedBrightness >= 0) return
        if (!canWrite(context)) return
        try {
            val resolver = context.contentResolver
            savedBrightness = Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS, 128)
            savedMode = Settings.System.getInt(
                resolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
            )
            Settings.System.putInt(
                resolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
            val value = (prefs.brightnessPercent * 10).coerceIn(1, 255)
            Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, value)
        } catch (t: Throwable) {
            savedBrightness = -1
        }
    }

    fun restore(context: Context) {
        if (savedBrightness < 0) return
        try {
            val resolver = context.contentResolver
            Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, savedBrightness)
            if (savedMode >= 0) {
                Settings.System.putInt(
                    resolver,
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    savedMode
                )
            }
        } catch (_: Throwable) {
            // biarkan nilai terakhir; user bisa atur manual
        } finally {
            savedBrightness = -1
            savedMode = -1
        }
    }
}
