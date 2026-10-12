package dev.retrobit.od.ui

import android.app.TimePickerDialog
import android.os.Bundle
import android.text.format.DateFormat
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.slider.Slider
import dev.retrobit.od.R
import dev.retrobit.od.ambient.AmbientController
import dev.retrobit.od.data.Prefs
import dev.retrobit.od.system.ProximityWatch
import kotlin.math.roundToInt

class AutomationActivity : AppCompatActivity() {

    private val prefs by lazy { Prefs.get(this) }

    private val dayIds = intArrayOf(
        R.id.day0, R.id.day1, R.id.day2, R.id.day3, R.id.day4, R.id.day5, R.id.day6
    )
    private val burnIds = intArrayOf(R.id.burn0, R.id.burn1, R.id.burn2, R.id.burn5)
    private val burnMinutes = intArrayOf(0, 1, 2, 5)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_automation)

        bindSwitch(R.id.switchSchedule, { prefs.scheduleEnabled }, { prefs.scheduleEnabled = it })
        bindSwitch(R.id.switchCharging, { prefs.onlyWhenCharging }, { prefs.onlyWhenCharging = it })
        bindSwitch(R.id.switchTemp, { prefs.tempGuard }, { prefs.tempGuard = it })
        bindSwitch(R.id.switchPeek, { prefs.peekMode }, { prefs.peekMode = it })
        bindSwitch(R.id.switchForceBright, { prefs.forceBrightness }, { prefs.forceBrightness = it })
        bindSwitch(R.id.switchMask, { prefs.cardMasked }, { prefs.cardMasked = it })
        bindSwitch(
            R.id.switchCardCharge,
            { prefs.cardRevealWhileCharging },
            { prefs.cardRevealWhileCharging = it }
        )

        val prox = findViewById<SwitchCompat>(R.id.switchProx)
        val proxHint = findViewById<TextView>(R.id.txtProxHint)
        prox.isChecked = prefs.proximityGuard
        if (ProximityWatch.refreshSupported(this)) {
            proxHint.visibility = TextView.GONE
            prox.setOnCheckedChangeListener { _, checked ->
                prefs.proximityGuard = checked
                if (checked) ProximityWatch.start(this) else ProximityWatch.stop()
            }
        } else {
            prox.isEnabled = false
            prox.isChecked = false
            proxHint.setText(R.string.proximity_unsupported)
            proxHint.visibility = TextView.VISIBLE
        }

        bindTime(R.id.btnFrom, true)
        bindTime(R.id.btnTo, false)
        bindDays()
        bindSliders()
        bindBurnIn()
        refreshLabels()
    }

    private fun bindSwitch(id: Int, getter: () -> Boolean, setter: (Boolean) -> Unit) {
        val switch = findViewById<SwitchCompat>(id)
        switch.isChecked = getter()
        switch.setOnCheckedChangeListener { _, checked ->
            setter(checked)
            AmbientController.sync(this)
        }
    }

    private fun bindTime(id: Int, start: Boolean) {
        findViewById<MaterialButton>(id).setOnClickListener {
            val minuteOfDay = if (start) prefs.scheduleStartMinute else prefs.scheduleEndMinute
            TimePickerDialog(
                this,
                { _, hour, minute ->
                    val value = hour * 60 + minute
                    if (start) prefs.scheduleStartMinute = value else prefs.scheduleEndMinute = value
                    refreshLabels()
                },
                minuteOfDay / 60,
                minuteOfDay % 60,
                DateFormat.is24HourFormat(this)
            ).show()
        }
    }

    private fun bindDays() {
        dayIds.forEachIndexed { index, id ->
            val button = findViewById<MaterialButton>(id)
            button.isChecked = prefs.scheduleDaysMask and (1 shl index) != 0
            button.setOnClickListener {
                val bit = 1 shl index
                prefs.scheduleDaysMask = prefs.scheduleDaysMask xor bit
                button.isChecked = prefs.scheduleDaysMask and bit != 0
            }
        }
    }

    private fun bindSliders() {
        findViewById<Slider>(R.id.sliderFloor).apply {
            value = snap(prefs.batteryFloorPercent.toFloat(), valueFrom, valueTo, 5f)
            addOnChangeListener { _, value, _ ->
                prefs.batteryFloorPercent = value.roundToInt()
                refreshLabels()
            }
        }
        findViewById<Slider>(R.id.sliderPeek).apply {
            value = snap(prefs.peekSeconds.toFloat(), valueFrom, valueTo, 5f)
            addOnChangeListener { _, value, _ ->
                prefs.peekSeconds = value.roundToInt()
                refreshLabels()
            }
        }
        findViewById<Slider>(R.id.sliderBright).apply {
            value = snap(prefs.brightnessPercent.toFloat(), valueFrom, valueTo, 1f)
            addOnChangeListener { _, value, _ ->
                prefs.brightnessPercent = value.roundToInt()
                refreshLabels()
            }
        }
    }

    private fun bindBurnIn() {
        val group = findViewById<MaterialButtonToggleGroup>(R.id.groupBurn)
        val index = burnMinutes.indexOfFirst { it == prefs.burnInMinutes }.coerceAtLeast(0)
        group.check(burnIds[index])
        group.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val at = burnIds.indexOf(checkedId)
                if (at >= 0) prefs.burnInMinutes = burnMinutes[at]
            }
        }
    }

    private fun refreshLabels() {
        findViewById<TextView>(R.id.txtFloor).text =
            "${getString(R.string.battery_floor)}: ${prefs.batteryFloorPercent}%"
        findViewById<TextView>(R.id.txtPeek).text =
            "${getString(R.string.peek_duration)}: ${prefs.peekSeconds}s"
        findViewById<TextView>(R.id.txtBright).text =
            "${getString(R.string.brightness)}: ${prefs.brightnessPercent}%"
        findViewById<MaterialButton>(R.id.btnFrom).text =
            "${getString(R.string.schedule_from)} ${formatMinute(prefs.scheduleStartMinute)}"
        findViewById<MaterialButton>(R.id.btnTo).text =
            "${getString(R.string.schedule_to)} ${formatMinute(prefs.scheduleEndMinute)}"
    }

    private fun formatMinute(minuteOfDay: Int): String {
        val hour = minuteOfDay / 60
        val minute = minuteOfDay % 60
        return String.format("%02d:%02d", hour, minute)
    }

    private fun snap(value: Float, from: Float, to: Float, step: Float): Float {
        val clamped = value.coerceIn(from, to)
        val steps = ((clamped - from) / step).roundToInt()
        return (from + steps * step).coerceIn(from, to)
    }

    override fun onResume() {
        super.onResume()
        dayIds.forEachIndexed { index, id ->
            findViewById<MaterialButton>(id).isChecked = prefs.scheduleDaysMask and (1 shl index) != 0
        }
        refreshLabels()
    }
}
