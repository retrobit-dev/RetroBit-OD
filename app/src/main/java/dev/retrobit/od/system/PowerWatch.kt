package dev.retrobit.od.system

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.SystemClock

data class BatteryInfo(val percent: Int, val charging: Boolean, val temperatureC: Float)

/**
 * Baca status baterai lewat sticky broadcast (murah, tanpa menerima broadcast baru)
 * dengan cache singkat, plus monitoring proximity untuk mode "di saku".
 */
object PowerWatch {

    private var cached: BatteryInfo? = null
    private var cachedAt = 0L

    fun battery(context: Context, maxAgeMs: Long = 4000L): BatteryInfo {
        val now = SystemClock.elapsedRealtime()
        val old = cached
        if (old != null && now - cachedAt < maxAgeMs) return old
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return old ?: BatteryInfo(-1, false, 0f)
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        val percent = if (level >= 0) (level * 100 / scale).coerceIn(0, 100) else -1
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        val temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f
        return BatteryInfo(percent, charging, temp).also {
            cached = it
            cachedAt = now
        }
    }

    fun invalidateCache() {
        cachedAt = 0L
    }
}

object ProximityWatch {

    @Volatile
    var supported = false
        private set

    @Volatile
    var covered = false
        private set

    private var manager: SensorManager? = null
    private var sensor: Sensor? = null

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val max = event.sensor.maximumRange
            val value = event.values[0]
            covered = if (max >= 5f) value < 5f else value < max * 0.5f
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    fun start(context: Context) {
        if (manager != null) return
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        val s = sm.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        if (s == null) {
            supported = false
            return
        }
        supported = true
        manager = sm
        sensor = s
        sm.registerListener(listener, s, SensorManager.SENSOR_DELAY_NORMAL)
    }

    fun stop() {
        manager?.unregisterListener(listener)
        manager = null
        sensor = null
        covered = false
    }

    fun refreshSupported(context: Context): Boolean {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        supported = sm?.getDefaultSensor(Sensor.TYPE_PROXIMITY) != null
        return supported
    }
}
