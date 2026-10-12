package dev.retrobit.od.render

import android.content.Context
import android.os.SystemClock
import android.text.format.DateFormat
import android.util.DisplayMetrics
import dev.retrobit.od.R
import dev.retrobit.od.data.Prefs
import dev.retrobit.od.service.NotificationMonitor
import dev.retrobit.od.system.PowerWatch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Semua angka/teks yang dibutuhkan renderer, dihitung sekali per tick.
 * [update] mengembalikan true hanya kalau kontennya benar-benar berubah,
 * supaya View tidak di-invalidate tanpa perlu (hemat baterai).
 */
class RenderState {

    var timeText = "--:--"
    var dateText = ""
    var batteryText = ""
    var notifText = ""
    var batteryPercent = -1
    var charging = false
    var temperatureC = 0f
    var shiftX = 0
    var shiftY = 0
    var colonVisible = true
    var cardMasked = false

    private var signature = ""

    fun update(context: Context, prefs: Prefs): Boolean {
        val locale = Locale.getDefault()
        val cal = Calendar.getInstance()
        val is24 = DateFormat.is24HourFormat(context)
        val pattern = when {
            prefs.showSeconds && is24 -> "HH:mm:ss"
            prefs.showSeconds -> "hh:mm:ss a"
            is24 -> "HH:mm"
            else -> "hh:mm a"
        }
        timeText = SimpleDateFormat(pattern, locale).format(cal.time).uppercase(locale)
        colonVisible = !prefs.showSeconds || cal.get(Calendar.SECOND) % 2 == 0

        dateText = if (prefs.showDate) {
            SimpleDateFormat("EEE, d MMM yyyy", locale).format(cal.time).uppercase(locale)
        } else {
            ""
        }

        val battery = PowerWatch.battery(context)
        batteryPercent = battery.percent
        charging = battery.charging
        temperatureC = battery.temperatureC
        batteryText = when {
            !prefs.showBattery || battery.percent < 0 -> ""
            battery.charging -> context.getString(R.string.charging_label, battery.percent)
            else -> context.getString(R.string.battery_label, battery.percent)
        }.uppercase(locale)

        notifText = if (prefs.showNotifications) {
            val count = NotificationMonitor.activeCount
            if (count > 0) context.getString(R.string.notif_count, count).uppercase(locale) else ""
        } else {
            ""
        }

        cardMasked = prefs.cardEnabled && !prefs.cardIsRevealed(battery.charging)

        val metrics: DisplayMetrics = context.resources.displayMetrics
        val interval = prefs.burnInMinutes
        if (interval > 0) {
            val step = ((SystemClock.elapsedRealtime() / (60_000L * interval)).toInt() % 4)
            val d = (2f * metrics.density).toInt().coerceAtLeast(1)
            shiftX = if (step == 1 || step == 2) d else 0
            shiftY = if (step == 2 || step == 3) d else 0
        } else {
            shiftX = 0
            shiftY = 0
        }

        val next = buildString {
            append(timeText).append('|')
            append(dateText).append('|')
            append(batteryText).append('|')
            append(notifText).append('|')
            append(shiftX).append(',').append(shiftY).append('|')
            append(colonVisible).append('|')
            append(cardMasked).append('|')
            append(prefs.layoutPreset).append(prefs.fontFace).append(prefs.clockScale)
            append(prefs.accentColor).append(prefs.glow).append(prefs.scanlines)
            append(prefs.cardEnabled).append(prefs.cardUseImage).append(prefs.cardJson)
        }
        val changed = next != signature
        signature = next
        return changed
    }
}
