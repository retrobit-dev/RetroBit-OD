package dev.retrobit.od.data

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.os.SystemClock

/**
 * Semua preferensi RetroBit OD ada di satu tempat supaya renderer, service dan UI
 * tidak saling bergantung. Pembacaan murah (di-cache [SharedPreferences]),
 * jadi aman dipanggil tiap frame.
 */
class Prefs private constructor(private val sp: SharedPreferences) {

    // ── master ───────────────────────────────────────────────────────────────
    var ambientEnabled: Boolean
        get() = sp.getBoolean(K_ENABLED, false)
        set(value) {
            putBoolean(K_ENABLED, value)
        }

    /** 0 = overlay window, 1 = activity (compat), 2 = dream/screensaver */
    var renderMode: Int
        get() = sp.getInt(K_RENDER_MODE, MODE_OVERLAY)
        set(value) {
            putInt(K_RENDER_MODE, value)
        }

    // ── gaya ─────────────────────────────────────────────────────────────────
    /** 0 = jam di atas, 1 = jam di tengah, 2 = kartu penuh */
    var layoutPreset: Int
        get() = sp.getInt(K_LAYOUT, LAYOUT_CLOCK_TOP)
        set(value) {
            putInt(K_LAYOUT, value)
        }

    /** 0 = pixel 5x7, 1 = LED 7 segmen */
    var fontFace: Int
        get() = sp.getInt(K_FONT, FONT_PIXEL)
        set(value) {
            putInt(K_FONT, value)
        }

    /** 0..3 → kecil, sedang, besar, ekstra */
    var clockScale: Int
        get() = sp.getInt(K_CLOCK_SCALE, 1)
        set(value) {
            putInt(K_CLOCK_SCALE, value)
        }

    var accentColor: Int
        get() = sp.getInt(K_ACCENT, Color.parseColor("#7CFFA0"))
        set(value) {
            putInt(K_ACCENT, value)
        }

    var glow: Boolean
        get() = sp.getBoolean(K_GLOW, true)
        set(value) {
            putBoolean(K_GLOW, value)
        }

    var scanlines: Boolean
        get() = sp.getBoolean(K_SCANLINES, false)
        set(value) {
            putBoolean(K_SCANLINES, value)
        }

    var showSeconds: Boolean
        get() = sp.getBoolean(K_SHOW_SECONDS, false)
        set(value) {
            putBoolean(K_SHOW_SECONDS, value)
        }

    var showDate: Boolean
        get() = sp.getBoolean(K_SHOW_DATE, true)
        set(value) {
            putBoolean(K_SHOW_DATE, value)
        }

    var showBattery: Boolean
        get() = sp.getBoolean(K_SHOW_BATTERY, true)
        set(value) {
            putBoolean(K_SHOW_BATTERY, value)
        }

    var showNotifications: Boolean
        get() = sp.getBoolean(K_SHOW_NOTIF, false)
        set(value) {
            putBoolean(K_SHOW_NOTIF, value)
        }

    var showCardLine: Boolean
        get() = sp.getBoolean(K_SHOW_CARD_LINE, true)
        set(value) {
            putBoolean(K_SHOW_CARD_LINE, value)
        }

    // ── kartu ASN ────────────────────────────────────────────────────────────
    var cardEnabled: Boolean
        get() = sp.getBoolean(K_CARD_ENABLED, false)
        set(value) {
            putBoolean(K_CARD_ENABLED, value)
        }

    /** true = tampilkan gambar kartu utuh, false = susun dari field */
    var cardUseImage: Boolean
        get() = sp.getBoolean(K_CARD_USE_IMAGE, false)
        set(value) {
            putBoolean(K_CARD_USE_IMAGE, value)
        }

    var cardJson: String
        get() = sp.getString(K_CARD_JSON, "") ?: ""
        set(value) {
            putString(K_CARD_JSON, value)
        }

    /** Setelah timestamp ini kartu diburamkan lagi (mode privasi). */
    var cardRevealUntil: Long
        get() = sp.getLong(K_CARD_REVEAL_UNTIL, 0L)
        set(value) {
            putLong(K_CARD_REVEAL_UNTIL, value)
        }

    /** true = kartu ditutupi sampai di-reveal (hemat privasi, bukan default). */
    var cardMasked: Boolean
        get() = sp.getBoolean(K_CARD_MASK, false)
        set(value) {
            putBoolean(K_CARD_MASK, value)
        }

    /** Kartu (yang diburamkan) otomatis dibuka selama HP sedang dicharge. */
    var cardRevealWhileCharging: Boolean
        get() = sp.getBoolean(K_CARD_CHARGE_ONLY, true)
        set(value) {
            putBoolean(K_CARD_CHARGE_ONLY, value)
        }

    fun revealCard(seconds: Int = 20) {
        cardRevealUntil = System.currentTimeMillis() + seconds * 1000L
    }

    /** Kartu boleh ditampilkan sekarang? */
    fun cardIsRevealed(charging: Boolean): Boolean {
        if (!cardMasked) return true
        if (cardRevealWhileCharging && charging) return true
        return System.currentTimeMillis() < cardRevealUntil
    }

    // ── otomatisasi ───────────────────────────────────────────────────────────
    var scheduleEnabled: Boolean
        get() = sp.getBoolean(K_SCHED_ON, false)
        set(value) {
            putBoolean(K_SCHED_ON, value)
        }

    /** menit sejak tengah malam */
    var scheduleStartMinute: Int
        get() = sp.getInt(K_SCHED_FROM, 22 * 60)
        set(value) {
            putInt(K_SCHED_FROM, value)
        }

    var scheduleEndMinute: Int
        get() = sp.getInt(K_SCHED_TO, 6 * 60)
        set(value) {
            putInt(K_SCHED_TO, value)
        }

    /** bit 0 = Minggu … bit 6 = Sabtu */
    var scheduleDaysMask: Int
        get() = sp.getInt(K_SCHED_DAYS, 0b1111111)
        set(value) {
            putInt(K_SCHED_DAYS, value)
        }

    var onlyWhenCharging: Boolean
        get() = sp.getBoolean(K_ONLY_CHARGING, false)
        set(value) {
            putBoolean(K_ONLY_CHARGING, value)
        }

    var batteryFloorPercent: Int
        get() = sp.getInt(K_BATT_FLOOR, 15)
        set(value) {
            putInt(K_BATT_FLOOR, value)
        }

    var tempGuard: Boolean
        get() = sp.getBoolean(K_TEMP_GUARD, true)
        set(value) {
            putBoolean(K_TEMP_GUARD, value)
        }

    var proximityGuard: Boolean
        get() = sp.getBoolean(K_PROX_GUARD, false)
        set(value) {
            putBoolean(K_PROX_GUARD, value)
        }

    /** true = tampil sebentar lalu tidur, false = nyala terus selama AOD aktif */
    var peekMode: Boolean
        get() = sp.getBoolean(K_PEEK, true)
        set(value) {
            putBoolean(K_PEEK, value)
        }

    var peekSeconds: Int
        get() = sp.getInt(K_PEEK_SECONDS, 10)
        set(value) {
            putInt(K_PEEK_SECONDS, value)
        }

    /** 0..25 % brightness saat AOD (0 = seminimal mungkin) */
    var brightnessPercent: Int
        get() = sp.getInt(K_BRIGHTNESS, 0)
        set(value) {
            putInt(K_BRIGHTNESS, value)
        }

    /** pakai Settings.System untuk memaksa brightness (butuh WRITE_SETTINGS) */
    var forceBrightness: Boolean
        get() = sp.getBoolean(K_FORCE_BRIGHT, false)
        set(value) {
            putBoolean(K_FORCE_BRIGHT, value)
        }

    /** 0 = mati; >0 = menit antar geseran piksel anti burn-in */
    var burnInMinutes: Int
        get() = sp.getInt(K_BURN_IN, 2)
        set(value) {
            putInt(K_BURN_IN, value)
        }

    // ── util ─────────────────────────────────────────────────────────────────
    fun addListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        sp.registerOnSharedPreferenceChangeListener(listener)
    }

    fun removeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        sp.unregisterOnSharedPreferenceChangeListener(listener)
    }

    private fun putBoolean(key: String, value: Boolean) {
        sp.edit().putBoolean(key, value).apply()
    }

    private fun putInt(key: String, value: Int) {
        sp.edit().putInt(key, value).apply()
    }

    private fun putLong(key: String, value: Long) {
        sp.edit().putLong(key, value).apply()
    }

    private fun putString(key: String, value: String) {
        sp.edit().putString(key, value).apply()
    }

    companion object {
        const val MODE_OVERLAY = 0
        const val MODE_ACTIVITY = 1
        const val MODE_DREAM = 2

        const val LAYOUT_CLOCK_TOP = 0
        const val LAYOUT_CLOCK_CENTER = 1
        const val LAYOUT_CARD_FULL = 2

        const val FONT_PIXEL = 0
        const val FONT_LED = 1

        val CLOCK_SCALES = floatArrayOf(0.78f, 1.0f, 1.28f, 1.6f)

        private const val FILE = "retrobit_od"

        private const val K_ENABLED = "ambient_enabled"
        private const val K_RENDER_MODE = "render_mode"
        private const val K_LAYOUT = "layout"
        private const val K_FONT = "font"
        private const val K_CLOCK_SCALE = "clock_scale"
        private const val K_ACCENT = "accent"
        private const val K_GLOW = "glow"
        private const val K_SCANLINES = "scanlines"
        private const val K_SHOW_SECONDS = "show_seconds"
        private const val K_SHOW_DATE = "show_date"
        private const val K_SHOW_BATTERY = "show_battery"
        private const val K_SHOW_NOTIF = "show_notif"
        private const val K_SHOW_CARD_LINE = "show_card_line"
        private const val K_CARD_ENABLED = "card_enabled"
        private const val K_CARD_USE_IMAGE = "card_use_image"
        private const val K_CARD_JSON = "card_json"
        private const val K_CARD_REVEAL_UNTIL = "card_reveal_until"
        private const val K_CARD_MASK = "card_mask"
        private const val K_CARD_CHARGE_ONLY = "card_charge_only"
        private const val K_SCHED_ON = "schedule_on"
        private const val K_SCHED_FROM = "schedule_from"
        private const val K_SCHED_TO = "schedule_to"
        private const val K_SCHED_DAYS = "schedule_days"
        private const val K_ONLY_CHARGING = "only_charging"
        private const val K_BATT_FLOOR = "battery_floor"
        private const val K_TEMP_GUARD = "temp_guard"
        private const val K_PROX_GUARD = "proximity_guard"
        private const val K_PEEK = "peek_mode"
        private const val K_PEEK_SECONDS = "peek_seconds"
        private const val K_BRIGHTNESS = "brightness_percent"
        private const val K_FORCE_BRIGHT = "force_brightness"
        private const val K_BURN_IN = "burn_in_minutes"

        @Volatile
        private var instance: Prefs? = null

        fun get(context: Context): Prefs {
            return instance ?: synchronized(this) {
                instance ?: Prefs(
                    context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
                ).also { instance = it }
            }
        }
    }
}
