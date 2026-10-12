package dev.retrobit.od.ambient

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import androidx.annotation.StringRes
import dev.retrobit.od.R
import dev.retrobit.od.data.Prefs
import dev.retrobit.od.system.PowerWatch
import dev.retrobit.od.system.ProximityWatch
import java.util.Calendar

/**
 * Otak keputusan AOD: kapan boleh tampil, dan bagaimana menghidup/mematikan layanan.
 */
object AmbientController {

    const val TAG = "RetroBitOD"

    fun prefs(context: Context): Prefs = Prefs.get(context)

    /** Nyalakan/matikan layanan latar depan sesuai preferensi. */
    fun sync(context: Context) {
        val prefs = prefs(context)
        val intent = Intent(context, AmbientService::class.java)
        if (prefs.ambientEnabled) {
            try {
                context.startForegroundService(intent)
            } catch (t: Throwable) {
                // misalnya FGS tidak diizinkan karena app di-background saat restart:
                // biarkan percobaan berikutnya (layar mati / tile) yang menangani.
                android.util.Log.w(TAG, "gagal start layanan", t)
            }
        } else {
            context.stopService(intent)
        }
    }

    /** Uji cepat: tampilkan AOD beberapa detik meskipun sedang tidak layar mati. */
    fun previewNow(context: Context, seconds: Int = 10) {
        val i = Intent(context, AmbientService::class.java).apply {
            action = AmbientService.ACTION_SHOW
            putExtra(AmbientService.EXTRA_SECONDS, seconds)
        }
        context.startForegroundService(i)
    }

    fun hideNow(context: Context) {
        val i = Intent(context, AmbientService::class.java).apply { action = AmbientService.ACTION_HIDE }
        context.startForegroundService(i)
    }

    /** Buka buram kartu selama [seconds] detik. */
    fun revealCard(context: Context, seconds: Int = 20) {
        prefs(context).revealCard(seconds)
        val i = Intent(context, AmbientService::class.java).apply { action = AmbientService.ACTION_REFRESH }
        context.startForegroundService(i)
    }

    fun isOverlayAllowed(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun isScreenOn(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        return pm.isInteractive
    }

    /** true = sekarang boleh menampilkan AOD */
    fun eligible(context: Context): Boolean = blockReason(context, prefs(context)) == null

    @StringRes
    fun blockReason(context: Context, prefs: Prefs): Int? {
        if (!prefs.ambientEnabled) return null
        if (prefs.renderMode == Prefs.MODE_DREAM) return R.string.status_dream_mode
        if (prefs.renderMode == Prefs.MODE_OVERLAY && !isOverlayAllowed(context)) {
            return R.string.status_blocked_permission
        }
        val battery = PowerWatch.battery(context, maxAgeMs = 0L)
        if (prefs.onlyWhenCharging && !battery.charging) return R.string.status_blocked_charging
        if (!battery.charging && battery.percent in 0 until prefs.batteryFloorPercent) {
            return R.string.status_blocked_battery
        }
        if (prefs.tempGuard && battery.temperatureC >= 42f) return R.string.status_blocked_temp
        if (prefs.proximityGuard && ProximityWatch.covered && ProximityWatch.supported) {
            return R.string.status_blocked_proximity
        }
        if (!inSchedule(prefs)) return R.string.status_blocked_schedule
        return null
    }

    fun inSchedule(prefs: Prefs): Boolean {
        if (!prefs.scheduleEnabled) return true
        val cal = Calendar.getInstance()
        val dayBit = 1 shl (cal.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY)
        if (prefs.scheduleDaysMask and dayBit == 0) return false
        val now = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val from = prefs.scheduleStartMinute
        val to = prefs.scheduleEndMinute
        return if (from <= to) now in from until to else now >= from || now < to
    }
}
